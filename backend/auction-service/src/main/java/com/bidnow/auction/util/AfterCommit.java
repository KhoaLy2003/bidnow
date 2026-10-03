package com.bidnow.auction.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers side effects (Kafka publishes) until the surrounding transaction commits, so they never run
 * while the auction row lock is held and never fire for a rolled-back change.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * @throws IllegalStateException if no transaction synchronization is active
     */
    public static void run(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    // the change is already committed, so the caller must not see an error
                    log.error("CRITICAL: after-commit action failed - the change is committed but its event was not published", ex);
                }
            }
        });
    }
}
