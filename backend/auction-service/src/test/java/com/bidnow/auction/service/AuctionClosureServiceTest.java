package com.bidnow.auction.service;

import com.bidnow.auction.config.ClosureProperties;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.job.AuctionClosureJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.AuctionEndedEvent;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosureServiceTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    @Mock
    private AuctionKafkaProducer kafkaProducer;

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final OffsetDateTime PAST_END = OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC);
    private static final ClosureProperties CLOSURE = new ClosureProperties(20);

    private AuctionClosureService closureService;

    @BeforeEach
    void initTransactionSync() {
        TransactionSynchronizationManager.initSynchronization();
        closureService = new AuctionClosureService(auctionItemRepository, auctionStatusHistoryRepository,
                kafkaProducer, CLOSURE, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    /** Runs the after-commit hooks with JobRunr's static API mocked and verifies the single schedule call. */
    private void assertClosureScheduled(UUID auctionId, Instant endTime) {
        try (MockedStatic<BackgroundJob> backgroundJob = Mockito.mockStatic(BackgroundJob.class)) {
            triggerAfterCommit();
            backgroundJob.verify(() -> BackgroundJob.<AuctionClosureJob>schedule(
                    eq(AuctionClosureService.closureJobId(auctionId, endTime)),
                    eq(endTime.plus(CLOSURE.grace())),
                    any(IocJobLambda.class)));
            backgroundJob.verifyNoMoreInteractions();
        }
    }

    @Test
    void close_whenActiveWithBids_completesAuction() {
        UUID auctionId = UUID.randomUUID();
        UUID currentWinnerId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(2)
                .currentWinnerId(currentWinnerId)
                .currentPrice(new BigDecimal("300.00"))
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .endTime(PAST_END)
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);
        triggerAfterCommit();

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        assertThat(auction.getWinnerId()).isEqualTo(currentWinnerId);
        assertThat(auction.getCompletedAt()).isNotNull();
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionStatusHistory> historyCaptor = ArgumentCaptor.forClass(AuctionStatusHistory.class);
        verify(auctionStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo("ACTIVE");
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<AuctionEndedEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEndedEvent.class);
        verify(kafkaProducer).publishAuctionEnded(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getWinnerId()).isEqualTo(currentWinnerId);
        assertThat(eventCaptor.getValue().getWinningBidAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        // TODO: update this assertion when the bidding-service provides loser data (see loserIds TODO in AuctionClosureService)
        assertThat(eventCaptor.getValue().getLoserIds()).isEmpty();
    }

    @Test
    void close_whenActiveWithNoBids_failsAuction() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(0)
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .endTime(PAST_END)
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);
        triggerAfterCommit();

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        assertThat(auction.getWinnerId()).isNull();
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionEndedEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEndedEvent.class);
        verify(kafkaProducer).publishAuctionEnded(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getWinnerId()).isNull();
        assertThat(eventCaptor.getValue().getWinningBidAmount()).isNull();
        // TODO: update this assertion when the bidding-service provides loser data (see loserIds TODO in AuctionClosureService)
        assertThat(eventCaptor.getValue().getLoserIds()).isEmpty();
    }

    @Test
    void close_whenAuctionNotFound_skipsGracefully() {
        UUID auctionId = UUID.randomUUID();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.empty());

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).save(any());
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }

    @Test
    void close_whenAlreadyCompleted_isNoop() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.COMPLETED)
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).save(any());
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }

    @Test
    void close_readsAuctionWithRowLock() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(0)
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .endTime(PAST_END)
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void close_beforeExtendedEndTime_reschedulesInsteadOfClosing() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(3)
                .currentWinnerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("300.00"))
                .title("Extended Auction")
                .sellerId(UUID.randomUUID())
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC))
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        verify(auctionItemRepository, never()).save(any());
        verify(auctionStatusHistoryRepository, never()).save(any());
        // the deferred job is keyed on the NEW end time and fires grace after it
        assertClosureScheduled(auctionId, NOW.plusSeconds(300));
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }

    @Test
    void scheduleClosureJob_afterCommit_schedulesAtEndTimePlusGraceWithEndTimeKeyedId() {
        UUID auctionId = UUID.randomUUID();
        Instant endTime = NOW.plusSeconds(3600);

        closureService.scheduleClosureJob(auctionId, endTime);

        assertClosureScheduled(auctionId, endTime);
    }

    @Test
    void scheduleClosureJob_firesStrictlyAfterEndTime() {
        // JobRunr enqueues scheduled jobs up to one poll interval (15 s) early; the grace must cover it,
        // otherwise a job could run before end_time and try to reschedule itself under its own ID.
        assertThat(CLOSURE.grace()).isGreaterThan(Duration.ofSeconds(15));
        UUID auctionId = UUID.randomUUID();
        Instant endTime = NOW;

        closureService.scheduleClosureJob(auctionId, endTime);

        try (MockedStatic<BackgroundJob> backgroundJob = Mockito.mockStatic(BackgroundJob.class)) {
            triggerAfterCommit();
            ArgumentCaptor<Instant> fireAt = ArgumentCaptor.forClass(Instant.class);
            backgroundJob.verify(() -> BackgroundJob.<AuctionClosureJob>schedule(
                    any(UUID.class), fireAt.capture(), any(IocJobLambda.class)));
            assertThat(fireAt.getValue()).isAfter(endTime.plusSeconds(15));
        }
    }

    @Test
    void close_exactlyAtEndTime_closes() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(0)
                .title("Ending Now")
                .sellerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .endTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
    }

    @Test
    void closureJobId_isDeterministicPerAuctionAndCloseTime() {
        UUID auctionId = UUID.randomUUID();
        Instant end = NOW;

        assertThat(AuctionClosureService.closureJobId(auctionId, end))
                .isEqualTo(AuctionClosureService.closureJobId(auctionId, end));
        assertThat(AuctionClosureService.closureJobId(auctionId, end))
                .isNotEqualTo(AuctionClosureService.closureJobId(auctionId, end.plusSeconds(300)));
        assertThat(AuctionClosureService.closureJobId(auctionId, end))
                .isNotEqualTo(AuctionClosureService.closureJobId(UUID.randomUUID(), end));
    }
}
