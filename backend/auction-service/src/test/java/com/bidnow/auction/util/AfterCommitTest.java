package com.bidnow.auction.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AfterCommitTest {

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void failingAction_doesNotPropagate() {
        AfterCommit.run(() -> {
            throw new IllegalStateException("broker down");
        });

        assertThatCode(AfterCommitTest::triggerAfterCommit).doesNotThrowAnyException();
    }

    @Test
    void action_runsOnlyWhenAfterCommitTriggered() {
        AtomicInteger calls = new AtomicInteger();
        AfterCommit.run(calls::incrementAndGet);

        assertThat(calls.get()).isZero();
        triggerAfterCommit();
        assertThat(calls.get()).isEqualTo(1);
    }
}
