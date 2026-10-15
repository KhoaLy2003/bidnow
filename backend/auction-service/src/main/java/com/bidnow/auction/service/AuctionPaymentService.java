package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Projects wallet-service's {@link PaymentEvent} onto the auction: the payment deadline, the paid
 * timestamp, and {@code COMPLETED → FAILED} when the winner forfeits. Wallet-service stays the system
 * of record; every effect here is guarded by a null or status check, so a replayed event is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionPaymentService {

    static final String TYPE_REQUIRED = "REQUIRED";
    static final String TYPE_COMPLETED = "COMPLETED";
    static final String TYPE_FAILED = "FAILED";
    static final String FORFEIT_REASON = "Winner did not pay by the payment deadline";

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final Clock clock;

    /**
     * @throws IllegalStateException if the auction is still {@code ACTIVE}. This is a defensive guard: it
     *                               should not happen, because every {@code AuctionEndedEvent} is published
     *                               after its transaction commits. If it ever does, throwing lets the Kafka
     *                               error handler retry (1s/2s/4s) before dead-lettering. The check runs
     *                               before the winner check because an {@code ACTIVE} row has no winner yet.
     */
    @Transactional
    public void applyPayment(PaymentEvent event) {
        if (event == null || event.getAuctionId() == null || !isHandled(event.getPaymentType())) {
            log.debug("Ignoring payment event {}", event);
            return;
        }

        UUID auctionId = event.getAuctionId();
        AuctionItem auction = auctionItemRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null) {
            log.warn("Payment event {} skipped: auction {} not found", event.getPaymentType(), auctionId);
            return;
        }
        if (auction.getStatus() == AuctionStatus.ACTIVE) {
            throw new IllegalStateException("Auction " + auctionId + " is still ACTIVE; payment event "
                    + event.getPaymentType() + " will be retried");
        }
        if (auction.getStatus() != AuctionStatus.COMPLETED) {
            log.debug("Payment event {} skipped: auction {} is {}", event.getPaymentType(), auctionId,
                    auction.getStatus());
            return;
        }
        if (event.getUserId() == null || !event.getUserId().equals(auction.getWinnerId())) {
            log.warn("Payment event {} skipped: user {} is not the winner of auction {}",
                    event.getPaymentType(), event.getUserId(), auctionId);
            return;
        }

        switch (event.getPaymentType()) {
            case TYPE_REQUIRED -> applyRequired(auction, event);
            case TYPE_COMPLETED -> applyCompleted(auction);
            case TYPE_FAILED -> applyFailed(auction);
            default -> { /* unreachable: isHandled() filtered the type */ }
        }
    }

    private static boolean isHandled(String paymentType) {
        return TYPE_REQUIRED.equals(paymentType) || TYPE_COMPLETED.equals(paymentType)
                || TYPE_FAILED.equals(paymentType);
    }

    private void applyRequired(AuctionItem auction, PaymentEvent event) {
        if (auction.getPaymentDeadline() != null || event.getDeadline() == null) {
            return;
        }
        auction.setPaymentDeadline(OffsetDateTime.ofInstant(event.getDeadline(), ZoneOffset.UTC));
        auctionItemRepository.save(auction);
        log.info("Auction {} payment deadline set to {}", auction.getId(), auction.getPaymentDeadline());
    }

    private void applyCompleted(AuctionItem auction) {
        if (auction.getWinnerPaidAt() != null) {
            return;
        }
        auction.setWinnerPaidAt(OffsetDateTime.now(clock));
        auctionItemRepository.save(auction);
        log.info("Auction {} paid by the winner at {}", auction.getId(), auction.getWinnerPaidAt());
    }

    private void applyFailed(AuctionItem auction) {
        if (auction.getWinnerPaidAt() != null) {
            return;
        }
        auction.setStatus(AuctionStatus.FAILED);
        auctionItemRepository.save(auction);
        auctionStatusHistoryRepository.save(AuctionStatusHistory.builder()
                .auction(auction)
                .fromStatus(AuctionStatus.COMPLETED.name())
                .toStatus(AuctionStatus.FAILED.name())
                .triggeredBy(null)
                .reason(FORFEIT_REASON)
                .build());
        log.info("Auction {} failed: winner did not pay (COMPLETED → FAILED)", auction.getId());
    }
}
