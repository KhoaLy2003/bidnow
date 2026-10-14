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
 * Sends payment reminder #2 to winners who have not paid when their deadline is within the reminder window
 * (default 24h). Not transactional: each reminder runs in its own transaction; a failure is logged and the hold is
 * retried on the next run. Safe on several instances — {@code sendPaymentReminder} locks the hold with SKIP LOCKED
 * and stamps {@code reminder_sent_at}, so a hold is reminded once.
 */
@Slf4j
@Component
public class PaymentReminderScheduler {

    private final PaymentHoldRepository paymentHoldRepository;
    private final PaymentService paymentService;
    private final int batchSize;
    private final long reminderBeforeDeadlineHours;

    public PaymentReminderScheduler(PaymentHoldRepository paymentHoldRepository,
                                    PaymentService paymentService,
                                    @Value("${wallet.payment.reminder-batch-size:100}") int batchSize,
                                    @Value("${wallet.payment.reminder-before-deadline-hours:24}")
                                    long reminderBeforeDeadlineHours) {
        this.paymentHoldRepository = paymentHoldRepository;
        this.paymentService = paymentService;
        this.batchSize = batchSize;
        this.reminderBeforeDeadlineHours = reminderBeforeDeadlineHours;
    }

    @Scheduled(initialDelayString = "${wallet.payment.reminder-initial-delay-ms:60000}",
               fixedDelayString = "${wallet.payment.reminder-interval-ms:300000}")
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> auctionIds = paymentHoldRepository.findDueForReminderAuctionIds(PaymentHoldStatus.PENDING_PAYMENT,
                now, now.plusHours(reminderBeforeDeadlineHours), PageRequest.of(0, batchSize));
        int succeeded = 0;
        for (UUID auctionId : auctionIds) {
            try {
                paymentService.sendPaymentReminder(auctionId);
                succeeded++;
            } catch (RuntimeException ex) {
                log.error("Payment reminder failed for auctionId={}", auctionId, ex);
            }
        }
        if (!auctionIds.isEmpty()) {
            log.info("Payment reminder run: {} due holds found, {} processed without error", auctionIds.size(), succeeded);
        }
    }
}
