package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.exception.DepositReleaseException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.DepositLockService;
import com.bidnow.wallet.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Releases deposit locks when an auction ends (all but the winner) or is cancelled (everyone).
 * Deliberately not transactional: each lock is released in its own transaction by
 * {@link DepositLockService#releaseDeposit}, so one failure never rolls back the others.
 * Losers are derived from deposit_locks; AuctionEndedEvent.loserIds is not used.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleEventConsumer {

    private final DepositLockRepository depositLockRepository;
    private final WalletRepository walletRepository;
    private final DepositLockService depositLockService;
    private final PaymentService paymentService;

    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionEnded(AuctionEndedEvent event) {
        log.info("Received AuctionEndedEvent for auctionId={}, winnerId={}", event.getAuctionId(), event.getWinnerId());
        List<RuntimeException> failures = new ArrayList<>();
        runStep(failures, () -> releaseAll(event.getAuctionId(), event.getWinnerId(), RefundReason.AUCTION_LOST));
        if (event.getWinnerId() != null) {
            runStep(failures, () -> paymentService.createPaymentHold(event.getAuctionId(), event.getWinnerId(),
                    event.getSellerId(), event.getWinningBidAmount()));
        }
        rethrowFirst(failures);
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionCancelled(AuctionCancelledEvent event) {
        log.info("Received AuctionCancelledEvent for auctionId={}", event.getAuctionId());
        List<RuntimeException> failures = new ArrayList<>();
        runStep(failures, () -> paymentService.cancelPaymentHold(event.getAuctionId()));
        runStep(failures, () -> releaseAll(event.getAuctionId(), null, RefundReason.AUCTION_CANCELLED));
        rethrowFirst(failures);
    }

    /** Runs one independent settlement step; a failure is recorded so the other steps still run. */
    private void runStep(List<RuntimeException> failures, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ex) {
            log.error("Auction lifecycle step failed", ex);
            failures.add(ex);
        }
    }

    /** Rethrow so the Kafka error handler redelivers; every step is idempotent. */
    private static void rethrowFirst(List<RuntimeException> failures) {
        if (!failures.isEmpty()) {
            throw failures.get(0);
        }
    }

    private void releaseAll(UUID auctionId, UUID excludeUserId, RefundReason reason) {
        List<DepositLock> locks = depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED);
        if (locks.isEmpty()) {
            log.debug("No LOCKED deposits for auctionId={}", auctionId);
            return;
        }

        UUID excludedWalletId = excludeUserId == null ? null
                : walletRepository.findByUserId(excludeUserId).map(Wallet::getId).orElse(null);

        List<UUID> failedLockIds = new ArrayList<>();
        for (DepositLock lock : locks) {
            if (Objects.equals(lock.getWalletId(), excludedWalletId)) {
                continue;
            }
            try {
                depositLockService.releaseDeposit(lock.getId(), lock.getWalletId(), reason);
            } catch (RuntimeException ex) {
                log.error("Failed to release deposit lockId={}, walletId={}, auctionId={}",
                        lock.getId(), lock.getWalletId(), auctionId, ex);
                failedLockIds.add(lock.getId());
            }
        }

        if (!failedLockIds.isEmpty()) {
            throw new DepositReleaseException(auctionId, failedLockIds);
        }
    }
}
