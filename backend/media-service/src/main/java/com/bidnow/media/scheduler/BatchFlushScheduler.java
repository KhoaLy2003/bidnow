package com.bidnow.media.scheduler;

import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.batch.BidAlerts;
import com.bidnow.media.notification.batch.BidBatcher;
import com.bidnow.media.notification.batch.DueBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Flushes closed outbid / new-bid windows. Safe on every instance: a window is claimed atomically in Redis,
 * so exactly one instance dispatches it. Windows with no extra alerts produce nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchFlushScheduler {

    static final int CLAIM_LIMIT = 100;

    private final BidBatcher batcher;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${notification.outbid.flush-delay-ms:15000}")
    public void flush() {
        List<DueBatch> due;
        try {
            due = batcher.claimDue(clock.instant(), CLAIM_LIMIT);
        } catch (RuntimeException ex) {
            log.warn("Bid batch flush skipped - Redis unavailable: {}", ex.getMessage());
            return;
        }
        for (DueBatch batch : due) {
            if (batch.count() == 0) {
                continue;
            }
            try {
                dispatcher.dispatch(BidAlerts.batched(batch));
            } catch (RuntimeException ex) {
                log.error("Lost batched {} for user {} on auction {} ({} alerts): {}", batch.kind(), batch.userId(),
                        batch.auctionId(), batch.count(), ex.getMessage(), ex);
            }
        }
    }
}
