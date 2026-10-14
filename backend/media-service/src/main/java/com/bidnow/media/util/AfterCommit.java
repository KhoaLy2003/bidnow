package com.bidnow.media.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers side effects (email, Kafka push) until the surrounding transaction commits, so they never fire for
 * a rolled-back notification. Any data access inside the action needs its own transaction (REQUIRES_NEW).
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
                    // the notification is already committed, so the caller must not see an error
                    log.error("After-commit action failed - the notification is stored but was not delivered", ex);
                }
            }
        });
    }
}
