package com.bidnow.media.scheduler;

import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.batch.BidAlertKind;
import com.bidnow.media.notification.batch.BidAlerts;
import com.bidnow.media.notification.batch.BidBatcher;
import com.bidnow.media.notification.batch.DueBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchFlushSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:05:00Z");
    private static final Instant WINDOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock
    private BidBatcher batcher;
    @Mock
    private NotificationDispatcher dispatcher;

    private BatchFlushScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BatchFlushScheduler(batcher, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DueBatch batch(BidAlertKind kind, UUID user, int count) {
        return new DueBatch(kind, user, AUCTION, WINDOW, count, "Vintage Watch", new BigDecimal("130"));
    }

    @Test
    void dueBatchWithCount_dispatchesOneBatchedIntent() {
        DueBatch due = batch(BidAlertKind.OUTBID, ALICE, 2);
        when(batcher.claimDue(NOW, BatchFlushScheduler.CLAIM_LIMIT)).thenReturn(List.of(due));

        scheduler.flush();

        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        assertThat(intent.getValue()).isEqualTo(BidAlerts.batched(due));
    }

    @Test
    void quietWindow_dispatchesNothing() {
        when(batcher.claimDue(NOW, BatchFlushScheduler.CLAIM_LIMIT))
                .thenReturn(List.of(batch(BidAlertKind.OUTBID, ALICE, 0)));

        scheduler.flush();

        verifyNoInteractions(dispatcher);
    }

    @Test
    void redisDown_isSwallowed() {
        when(batcher.claimDue(NOW, BatchFlushScheduler.CLAIM_LIMIT))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(scheduler::flush).doesNotThrowAnyException();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void oneFailedDispatch_doesNotStopTheRest() {
        when(batcher.claimDue(NOW, BatchFlushScheduler.CLAIM_LIMIT))
                .thenReturn(List.of(batch(BidAlertKind.OUTBID, ALICE, 2), batch(BidAlertKind.NEW_BID, SELLER, 3)));
        doThrow(new IllegalStateException("db down")).doNothing().when(dispatcher).dispatch(any());

        assertThatCode(scheduler::flush).doesNotThrowAnyException();
        verify(dispatcher, times(2)).dispatch(any());
    }
}
