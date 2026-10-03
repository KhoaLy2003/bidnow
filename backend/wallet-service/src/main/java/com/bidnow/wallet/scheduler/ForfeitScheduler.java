package com.bidnow.wallet.scheduler;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Forfeits payment holds whose 48h deadline passed without payment. Not transactional: each hold is
 * forfeited in its own transaction; a failure is logged and the hold is retried on the next run.
 * Safe on several instances — {@code forfeitExpiredHold} locks the hold row with SKIP LOCKED.
 */
@Slf4j
@Component
public class ForfeitScheduler {

    private final PaymentHoldRepository paymentHoldRepository;
    private final PaymentService paymentService;
    private final int batchSize;

    public ForfeitScheduler(PaymentHoldRepository paymentHoldRepository,
                            PaymentService paymentService,
                            @Value("${wallet.payment.forfeit-batch-size:100}") int batchSize) {
        this.paymentHoldRepository = paymentHoldRepository;
        this.paymentService = paymentService;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${wallet.payment.forfeit-initial-delay-ms:60000}",
               fixedDelayString = "${wallet.payment.forfeit-interval-ms:300000}")
    public void forfeitExpiredHolds() {
        List<UUID> auctionIds = paymentHoldRepository.findExpiredAuctionIds(
                PaymentHoldStatus.PENDING_PAYMENT, LocalDateTime.now(), PageRequest.of(0, batchSize));
        int succeeded = 0;
        for (UUID auctionId : auctionIds) {
            try {
                paymentService.forfeitExpiredHold(auctionId);
                succeeded++;
            } catch (RuntimeException ex) {
                log.error("Forfeit failed for auctionId={}", auctionId, ex);
            }
        }
        if (!auctionIds.isEmpty()) {
            log.info("Forfeit run: {} expired holds found, {} processed without error", auctionIds.size(), succeeded);
        }
    }
}
