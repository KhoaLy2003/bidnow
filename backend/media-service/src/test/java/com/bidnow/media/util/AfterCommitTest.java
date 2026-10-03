package com.bidnow.media.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfterCommitTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void run_outsideTransaction_throwsIllegalState() {
        assertThatThrownBy(() -> AfterCommit.run(() -> { }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void run_executesOnlyOnAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger calls = new AtomicInteger();

        AfterCommit.run(calls::incrementAndGet);
        assertThat(calls).hasValue(0);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(calls).hasValue(1);
    }

    @Test
    void run_swallowsActionFailure() {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.run(() -> {
            throw new IllegalStateException("boom");
        });

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
