package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-10-17T12:00:00Z");
    private static final OffsetDateTime EARLIER_PAID_AT =
            OffsetDateTime.ofInstant(Instant.parse("2026-10-14T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionStatusHistoryRepository auctionStatusHistoryRepository;

    private AuctionPaymentService service;

    private final UUID auctionId = UUID.randomUUID();
    private final UUID winnerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuctionPaymentService(auctionItemRepository, auctionStatusHistoryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuctionItem auction(AuctionStatus status, UUID winner) {
        return AuctionItem.builder()
                .id(auctionId)
                .status(status)
                .winnerId(winner)
                .build();
    }

    private AuctionItem stubAuction(AuctionStatus status, UUID winner) {
        AuctionItem auction = auction(status, winner);
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));
        return auction;
    }

    private PaymentEvent event(String type) {
        return PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(winnerId)
                .paymentType(type)
                .deadline(DEADLINE)
                .build();
    }

    // ---- REQUIRED ----

    @Test
    void required_setsPaymentDeadline() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);

        service.applyPayment(event("REQUIRED"));

        assertThat(auction.getPaymentDeadline()).isEqualTo(OffsetDateTime.ofInstant(DEADLINE, ZoneOffset.UTC));
        verify(auctionItemRepository).save(auction);
    }

    @Test
    void required_whenDeadlineAlreadySet_keepsTheFirstValue() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        OffsetDateTime first = OffsetDateTime.ofInstant(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);
        auction.setPaymentDeadline(first);

        service.applyPayment(event("REQUIRED"));

        assertThat(auction.getPaymentDeadline()).isEqualTo(first);
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void required_withoutDeadline_changesNothing() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        PaymentEvent noDeadline = event("REQUIRED");
        noDeadline.setDeadline(null);

        service.applyPayment(noDeadline);

        assertThat(auction.getPaymentDeadline()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    // ---- COMPLETED ----

    @Test
    void completed_setsWinnerPaidAtToConsumeTime() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);

        service.applyPayment(event("COMPLETED"));

        assertThat(auction.getWinnerPaidAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        verify(auctionItemRepository).save(auction);
    }

    @Test
    void completed_replay_keepsTheFirstValue() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        auction.setWinnerPaidAt(EARLIER_PAID_AT);

        service.applyPayment(event("COMPLETED"));

        assertThat(auction.getWinnerPaidAt()).isEqualTo(EARLIER_PAID_AT);
        verify(auctionItemRepository, never()).save(any());
    }

    // ---- FAILED (forfeit) ----

    @Test
    void failed_onCompletedAuction_movesToFailedAndRecordsHistory() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionStatusHistory> history = ArgumentCaptor.forClass(AuctionStatusHistory.class);
        verify(auctionStatusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getAuction()).isSameAs(auction);
        assertThat(history.getValue().getFromStatus()).isEqualTo("COMPLETED");
        assertThat(history.getValue().getToStatus()).isEqualTo("FAILED");
        assertThat(history.getValue().getTriggeredBy()).isNull();
        assertThat(history.getValue().getReason()).isEqualTo("Winner did not pay by the payment deadline");
    }

    @Test
    void failed_afterPayment_isIgnored() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        auction.setWinnerPaidAt(EARLIER_PAID_AT);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        verify(auctionItemRepository, never()).save(any());
        verifyNoInteractions(auctionStatusHistoryRepository);
    }

    @Test
    void failed_replay_whenAlreadyFailed_isNoOp() {
        AuctionItem auction = stubAuction(AuctionStatus.FAILED, winnerId);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        verify(auctionItemRepository, never()).save(any());
        verifyNoInteractions(auctionStatusHistoryRepository);
    }

    @Test
    void completed_afterFailed_isIgnored() {
        AuctionItem auction = stubAuction(AuctionStatus.FAILED, winnerId);

        service.applyPayment(event("COMPLETED"));

        assertThat(auction.getWinnerPaidAt()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    // ---- guards ----

    @Test
    void unknownAuction_isSkipped() {
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.empty());

        service.applyPayment(event("COMPLETED"));

        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void wrongUser_isSkipped() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        PaymentEvent other = event("COMPLETED");
        other.setUserId(UUID.randomUUID());

        service.applyPayment(other);

        assertThat(auction.getWinnerPaidAt()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void nullUser_isSkipped() {
        AuctionItem auction = stubAuction(AuctionStatus.COMPLETED, winnerId);
        PaymentEvent anonymous = event("COMPLETED");
        anonymous.setUserId(null);

        service.applyPayment(anonymous);

        assertThat(auction.getWinnerPaidAt()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void cancelledAuction_isSkipped() {
        AuctionItem auction = stubAuction(AuctionStatus.CANCELLED, winnerId);

        service.applyPayment(event("FAILED"));

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void activeAuction_withoutWinner_throwsSoItIsRetried() {
        // An ACTIVE row has no winnerId yet, so this also pins "retry guard runs before the winner check".
        stubAuction(AuctionStatus.ACTIVE, null);

        assertThatThrownBy(() -> service.applyPayment(event("REQUIRED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(auctionId.toString());

        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void malformedEvents_areIgnoredWithoutTouchingTheDatabase() {
        PaymentEvent noAuction = event("COMPLETED");
        noAuction.setAuctionId(null);

        service.applyPayment(null);
        service.applyPayment(noAuction);
        service.applyPayment(event(null));
        service.applyPayment(event("REMINDER_24H"));
        service.applyPayment(event("REFUNDED"));

        verifyNoInteractions(auctionItemRepository, auctionStatusHistoryRepository);
    }
}
