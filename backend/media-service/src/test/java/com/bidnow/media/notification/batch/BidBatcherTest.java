package com.bidnow.media.notification.batch;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BidBatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    /** Every Redis call fails, as when Redis is down. */
    private final StringRedisTemplate down = mock(StringRedisTemplate.class, invocation -> {
        throw new RedisConnectionFailureException("Redis is down");
    });
    private final BidBatcher batcher = new BidBatcher(down, Clock.fixed(NOW, ZoneOffset.UTC), 300);

    @Test
    void batchKey_hasFixedFormat() {
        assertThat(BidBatcher.batchKey(BidAlertKind.OUTBID, ALICE, AUCTION))
                .isEqualTo("notif:batch:OUTBID:" + ALICE + ":" + AUCTION);
    }

    @Test
    void record_redisDown_sendsImmediately() {
        BidBatchOutcome outcome = batcher.record(new BidAlert(BidAlertKind.OUTBID, ALICE, AUCTION, UUID.randomUUID(),
                "Vase", new BigDecimal("10")));

        assertThat(outcome).isEqualTo(BidBatchOutcome.IMMEDIATE);
    }

    @Test
    void claimDue_redisDown_propagatesForTheSchedulerToHandle() {
        assertThatThrownBy(() -> batcher.claimDue(NOW, 100)).isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimDue_redisFailsMidLoop_returnsWhatWasAlreadyClaimed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("k1", "k2")));
        List<String> fields = List.of("kind", "OUTBID", "userId", ALICE.toString(), "auctionId", AUCTION.toString(),
                "windowStart", String.valueOf(NOW.toEpochMilli()), "count", "2", "latestAmount", "130",
                "auctionTitle", "Vase");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(fields)
                .thenThrow(new RedisConnectionFailureException("Redis went away"));

        List<DueBatch> claimed = new BidBatcher(redis, Clock.fixed(NOW, ZoneOffset.UTC), 300).claimDue(NOW, 100);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).count()).isEqualTo(2);
    }
}
