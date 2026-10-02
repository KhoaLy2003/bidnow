package com.bidnow.auction.service;

import com.bidnow.auction.config.EndingSoonProperties;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.job.AuctionEndingSoonJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.common.dto.event.AuctionEndingSoonEvent;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.scheduling.BackgroundJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionEndingSoonServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionKafkaProducer kafkaProducer;

    private AuctionEndingSoonService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        service = new AuctionEndingSoonService(auctionItemRepository, kafkaProducer,
                new EndingSoonProperties(List.of(60, 15)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static AuctionItem auction(AuctionStatus status, Instant endTime) {
        return AuctionItem.builder().id(AUCTION).title("Vintage Watch").sellerId(SELLER).status(status)
                .endTime(OffsetDateTime.ofInstant(endTime, ZoneOffset.UTC)).build();
    }

    /** Runs the after-commit hooks with JobRunr's static API mocked; returns it for verification. */
    private static void assertScheduledExactly(Instant endTime, int... thresholds) {
        try (MockedStatic<BackgroundJob> backgroundJob = Mockito.mockStatic(BackgroundJob.class)) {
            triggerAfterCommit();
            for (int minutes : thresholds) {
                backgroundJob.verify(() -> BackgroundJob.<AuctionEndingSoonJob>schedule(
                        eq(AuctionEndingSoonService.jobId(AUCTION, minutes, endTime)),
                        eq(endTime.minus(Duration.ofMinutes(minutes))),
                        any(IocJobLambda.class)));
            }
            backgroundJob.verifyNoMoreInteractions();
        }
    }

    // ── scheduleAll ───────────────────────────────────────────────────────────

    @Test
    void scheduleAll_schedulesEveryFutureThresholdAfterCommit() {
        Instant end = NOW.plus(Duration.ofHours(2));

        service.scheduleAll(AUCTION, end);

        assertScheduledExactly(end, 60, 15);
    }

    @Test
    void scheduleAll_skipsThresholdsAlreadyPast() {
        Instant end = NOW.plus(Duration.ofMinutes(50)); // the 60-minute mark was 10 minutes ago

        service.scheduleAll(AUCTION, end);

        assertScheduledExactly(end, 15);
    }

    @Test
    void scheduleAll_withNoThresholds_schedulesNothing() {
        service = new AuctionEndingSoonService(auctionItemRepository, kafkaProducer,
                new EndingSoonProperties(List.of()), Clock.fixed(NOW, ZoneOffset.UTC));

        service.scheduleAll(AUCTION, NOW.plus(Duration.ofHours(2)));

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void scheduleAll_outsideATransaction_fails() {
        TransactionSynchronizationManager.clearSynchronization();
        try {
            assertThatThrownBy(() -> service.scheduleAll(AUCTION, NOW.plus(Duration.ofHours(2))))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @Test
    void jobId_isDeterministicPerAuctionThresholdAndEndTime() {
        Instant end = NOW.plus(Duration.ofHours(2));

        assertThat(AuctionEndingSoonService.jobId(AUCTION, 15, end)).isEqualTo(AuctionEndingSoonService.jobId(AUCTION, 15, end));
        assertThat(AuctionEndingSoonService.jobId(AUCTION, 15, end)).isNotEqualTo(AuctionEndingSoonService.jobId(AUCTION, 60, end));
        assertThat(AuctionEndingSoonService.jobId(AUCTION, 15, end))
                .isNotEqualTo(AuctionEndingSoonService.jobId(AUCTION, 15, end.plusSeconds(300)));
    }

    // ── fire ──────────────────────────────────────────────────────────────────

    @Test
    void fire_activeWithUnchangedEnd_publishesEndingSoonAfterCommit() {
        Instant end = NOW.plus(Duration.ofMinutes(15));
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, end)));

        service.fire(AUCTION, 15, end.toEpochMilli());
        verifyNoInteractions(kafkaProducer); // not before commit
        triggerAfterCommit();

        ArgumentCaptor<AuctionEndingSoonEvent> event = ArgumentCaptor.forClass(AuctionEndingSoonEvent.class);
        verify(kafkaProducer).publishEndingSoon(event.capture());
        assertThat(event.getValue()).isEqualTo(AuctionEndingSoonEvent.builder()
                .auctionId(AUCTION).auctionTitle("Vintage Watch").sellerId(SELLER).endTime(end)
                .thresholdMinutes(15).build());
    }

    @Test
    void fire_endTimeWithSubMillisecondPrecision_stillMatches() {
        Instant end = NOW.plus(Duration.ofMinutes(15)).plusNanos(123_456); // Postgres keeps microseconds
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, end)));

        service.fire(AUCTION, 15, end.toEpochMilli());
        triggerAfterCommit();

        verify(kafkaProducer).publishEndingSoon(any());
    }

    @Test
    void fire_extendedSinceScheduling_reschedulesForTheNewEndInsteadOfPublishing() {
        Instant oldEnd = NOW.plus(Duration.ofMinutes(15));
        Instant newEnd = NOW.plus(Duration.ofMinutes(20));
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, newEnd)));

        service.fire(AUCTION, 15, oldEnd.toEpochMilli());

        assertScheduledExactly(newEnd, 15);
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_extendedButNewThresholdAlreadyPast_doesNothing() {
        Instant oldEnd = NOW.plus(Duration.ofMinutes(15));
        Instant newEnd = NOW.plus(Duration.ofMinutes(10)); // e.g. edited earlier; its 15-min mark has passed
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, newEnd)));

        service.fire(AUCTION, 15, oldEnd.toEpochMilli());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_notActive_isNoOp() {
        Instant end = NOW.plus(Duration.ofMinutes(15));
        for (AuctionStatus status : List.of(AuctionStatus.COMPLETED, AuctionStatus.CANCELLED, AuctionStatus.FAILED)) {
            when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION)).thenReturn(Optional.of(auction(status, end)));

            service.fire(AUCTION, 15, end.toEpochMilli());
        }

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_auctionGone_isNoOp() {
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION)).thenReturn(Optional.empty());

        service.fire(AUCTION, 15, NOW.toEpochMilli());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_afterTheEndTime_isNoOp() {
        Instant end = NOW.minusSeconds(1); // job ran late (e.g. after downtime)
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, end)));

        service.fire(AUCTION, 15, end.toEpochMilli());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }
}
