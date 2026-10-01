package com.bidnow.media.notification.batch;

import com.bidnow.bdd.container.RedisContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Lua scripts against a real Redis. Needs Docker; run explicitly:
 * mvn -q -pl media-service -am test -Dtest=BidBatcherRedisIT -Dsurefire.failIfNoSpecifiedTests=false
 */
class BidBatcherRedisIT {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        GenericContainer<?> container = RedisContainerSupport.REDIS;
        LettuceConnectionFactory factory = new LettuceConnectionFactory(container.getHost(),
                container.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @BeforeEach
    void flushRedis() {
        try (RedisConnection connection = redis.getRequiredConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    private static BidBatcher at(Instant now) {
        return new BidBatcher(redis, Clock.fixed(now, ZoneOffset.UTC), 300);
    }

    private static BidAlert outbid(UUID user, UUID auction, String amount) {
        return new BidAlert(BidAlertKind.OUTBID, user, auction, UUID.randomUUID(), "Vintage Watch",
                new BigDecimal(amount));
    }

    @Test
    void firstAlert_isImmediate_andDueAtWindowEnd() {
        assertThat(at(T0).record(outbid(ALICE, AUCTION, "100"))).isEqualTo(BidBatchOutcome.IMMEDIATE);

        String key = BidBatcher.batchKey(BidAlertKind.OUTBID, ALICE, AUCTION);
        assertThat(redis.opsForZSet().score(BidBatcher.DUE_KEY, key))
                .isEqualTo((double) T0.plusSeconds(300).toEpochMilli());
        assertThat(redis.getExpire(key)).isBetween(300L, 360L);
    }

    @Test
    void laterAlertsInWindow_areBatched_andFlushedOnce() {
        assertThat(at(T0).record(outbid(ALICE, AUCTION, "110"))).isEqualTo(BidBatchOutcome.IMMEDIATE);
        assertThat(at(T0.plusSeconds(60)).record(outbid(ALICE, AUCTION, "120"))).isEqualTo(BidBatchOutcome.BATCHED);
        assertThat(at(T0.plusSeconds(120)).record(outbid(ALICE, AUCTION, "130"))).isEqualTo(BidBatchOutcome.BATCHED);

        assertThat(at(T0.plusSeconds(299)).claimDue(T0.plusSeconds(299), 100)).isEmpty();
        List<DueBatch> due = at(T0.plusSeconds(300)).claimDue(T0.plusSeconds(300), 100);

        assertThat(due).containsExactly(new DueBatch(BidAlertKind.OUTBID, ALICE, AUCTION, T0, 2, "Vintage Watch",
                new BigDecimal("130")));
        assertThat(redis.hasKey(BidBatcher.batchKey(BidAlertKind.OUTBID, ALICE, AUCTION))).isFalse();
    }

    @Test
    void quietWindow_flushesWithZeroCount() {
        at(T0).record(outbid(ALICE, AUCTION, "100"));

        List<DueBatch> due = at(T0.plusSeconds(300)).claimDue(T0.plusSeconds(300), 100);

        assertThat(due).singleElement().extracting(DueBatch::count).isEqualTo(0);
    }

    @Test
    void afterFlush_nextAlertIsImmediateAgain() {
        at(T0).record(outbid(ALICE, AUCTION, "100"));
        at(T0.plusSeconds(300)).claimDue(T0.plusSeconds(300), 100);

        assertThat(at(T0.plusSeconds(301)).record(outbid(ALICE, AUCTION, "140"))).isEqualTo(BidBatchOutcome.IMMEDIATE);
    }

    @Test
    void redeliveredAlerts_replayImmediate_butNeverCountTwice() {
        BidAlert first = outbid(ALICE, AUCTION, "110");
        BidAlert second = outbid(ALICE, AUCTION, "120");

        assertThat(at(T0).record(first)).isEqualTo(BidBatchOutcome.IMMEDIATE);
        assertThat(at(T0.plusSeconds(1)).record(first)).isEqualTo(BidBatchOutcome.IMMEDIATE);
        assertThat(at(T0.plusSeconds(2)).record(second)).isEqualTo(BidBatchOutcome.BATCHED);
        assertThat(at(T0.plusSeconds(3)).record(second)).isEqualTo(BidBatchOutcome.DUPLICATE);

        assertThat(at(T0.plusSeconds(300)).claimDue(T0.plusSeconds(300), 100))
                .singleElement().extracting(DueBatch::count).isEqualTo(1);
    }

    @Test
    void usersAuctionsAndKinds_areIndependent() {
        assertThat(at(T0).record(outbid(ALICE, AUCTION, "100"))).isEqualTo(BidBatchOutcome.IMMEDIATE);
        assertThat(at(T0).record(outbid(BOB, AUCTION, "100"))).isEqualTo(BidBatchOutcome.IMMEDIATE);
        assertThat(at(T0).record(outbid(ALICE, OTHER_AUCTION, "100"))).isEqualTo(BidBatchOutcome.IMMEDIATE);
        assertThat(at(T0).record(new BidAlert(BidAlertKind.NEW_BID, ALICE, AUCTION, UUID.randomUUID(), "Vintage Watch",
                new BigDecimal("100")))).isEqualTo(BidBatchOutcome.IMMEDIATE);
    }

    @Test
    void racingClaims_onlyOneInstanceGetsTheWindow() {
        at(T0).record(outbid(ALICE, AUCTION, "100"));
        at(T0.plusSeconds(10)).record(outbid(ALICE, AUCTION, "110"));
        Instant due = T0.plusSeconds(300);

        List<DueBatch> first = at(due).claimDue(due, 100);
        List<DueBatch> second = at(due).claimDue(due, 100);

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    @Test
    void expiredHashStillInDueSet_isSkipped() {
        at(T0).record(outbid(ALICE, AUCTION, "100"));
        redis.delete(BidBatcher.batchKey(BidAlertKind.OUTBID, ALICE, AUCTION));

        assertThat(at(T0.plusSeconds(300)).claimDue(T0.plusSeconds(300), 100)).isEmpty();
        assertThat(redis.opsForZSet().size(BidBatcher.DUE_KEY)).isZero();
    }

    @Test
    void windowLength_comesFromConfig() {
        new BidBatcher(redis, Clock.fixed(T0, ZoneOffset.UTC), 30).record(outbid(ALICE, AUCTION, "100"));

        assertThat(redis.opsForZSet().score(BidBatcher.DUE_KEY, BidBatcher.batchKey(BidAlertKind.OUTBID, ALICE, AUCTION)))
                .isEqualTo((double) T0.plus(Duration.ofSeconds(30)).toEpochMilli());
    }

    @Test
    void staleClaim_doesNotStealAReopenedWindow() {
        at(T0).record(outbid(ALICE, AUCTION, "100"));
        assertThat(at(T0.plusSeconds(300)).claimDue(T0.plusSeconds(300), 100)).hasSize(1);
        assertThat(at(T0.plusSeconds(301)).record(outbid(ALICE, AUCTION, "140"))).isEqualTo(BidBatchOutcome.IMMEDIATE);

        String key = BidBatcher.batchKey(BidAlertKind.OUTBID, ALICE, AUCTION);
        assertThat(at(T0.plusSeconds(300)).claim(key, T0.plusSeconds(300))).isEmpty();

        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.opsForZSet().score(BidBatcher.DUE_KEY, key))
                .isEqualTo((double) T0.plusSeconds(601).toEpochMilli());
    }
}
