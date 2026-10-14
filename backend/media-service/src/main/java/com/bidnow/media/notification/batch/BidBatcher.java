package com.bidnow.media.notification.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Outbid / new-bid batching windows in Redis, one hash per (kind, user, auction). The first alert in a quiet
 * window is sent immediately; later ones are counted and flushed as one alert when the window closes.
 * If Redis fails, alerts are sent immediately (noisy, never silent).
 */
@Slf4j
@Component
public class BidBatcher {

    static final String DUE_KEY = "notif:batch:due";
    private static final Duration TTL_SLACK = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration window;
    private final RedisScript<String> recordScript =
            RedisScript.of(new ClassPathResource("redis/batch-record.lua"), String.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final RedisScript<List<String>> claimScript =
            (RedisScript) RedisScript.of(new ClassPathResource("redis/batch-claim.lua"), List.class);

    public BidBatcher(StringRedisTemplate redis, Clock clock,
                      @Value("${notification.outbid.batch-window-seconds:300}") long windowSeconds) {
        this.redis = redis;
        this.clock = clock;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    static String batchKey(BidAlertKind kind, UUID userId, UUID auctionId) {
        return "notif:batch:" + kind + ":" + userId + ":" + auctionId;
    }

    public BidBatchOutcome record(BidAlert alert) {
        long now = clock.millis();
        try {
            String outcome = redis.execute(recordScript,
                    List.of(batchKey(alert.kind(), alert.userId(), alert.auctionId()), DUE_KEY),
                    String.valueOf(now),
                    String.valueOf(now + window.toMillis()),
                    String.valueOf(window.plus(TTL_SLACK).toMillis()),
                    alert.bidId().toString(),
                    alert.amount() == null ? "" : alert.amount().toPlainString(),
                    alert.auctionTitle() == null ? "" : alert.auctionTitle(),
                    alert.kind().name(),
                    alert.userId().toString(),
                    alert.auctionId().toString());
            return BidBatchOutcome.valueOf(outcome);
        } catch (RuntimeException ex) {
            log.warn("Bid batching unavailable for {} to user {} on auction {} - sending immediately: {}",
                    alert.kind(), alert.userId(), alert.auctionId(), ex.getMessage());
            return BidBatchOutcome.IMMEDIATE;
        }
    }

    /** Claims up to {@code limit} windows that closed by {@code now}. Redis failures propagate. */
    public List<DueBatch> claimDue(Instant now, int limit) {
        Set<String> keys = redis.opsForZSet().rangeByScore(DUE_KEY, 0, now.toEpochMilli(), 0, limit);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<DueBatch> claimed = new ArrayList<>();
        for (String key : keys) {
            try {
                claim(key, now).ifPresent(claimed::add);
            } catch (RuntimeException ex) {
                // Windows already claimed are deleted from Redis: return them rather than lose them with the exception.
                log.warn("Redis failed while claiming batch window {} - returning the {} already claimed: {}",
                        key, claimed.size(), ex.getMessage());
                break;
            }
        }
        return claimed;
    }

    /** Claims one window if it is still due at {@code now}; empty if another instance got it or it is gone. */
    Optional<DueBatch> claim(String key, Instant now) {
        List<String> fields = redis.execute(claimScript, List.of(key, DUE_KEY), String.valueOf(now.toEpochMilli()));
        return toDueBatch(key, fields);
    }

    private static Optional<DueBatch> toDueBatch(String key, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> hash = new HashMap<>();
        for (int i = 0; i + 1 < fields.size(); i += 2) {
            hash.put(fields.get(i), fields.get(i + 1));
        }
        try {
            String amount = hash.get("latestAmount");
            String title = hash.get("auctionTitle");
            return Optional.of(new DueBatch(
                    BidAlertKind.valueOf(hash.get("kind")),
                    UUID.fromString(hash.get("userId")),
                    UUID.fromString(hash.get("auctionId")),
                    Instant.ofEpochMilli(Long.parseLong(hash.get("windowStart"))),
                    Integer.parseInt(hash.get("count")),
                    title == null || title.isEmpty() ? null : title,
                    amount == null || amount.isEmpty() ? null : new BigDecimal(amount)));
        } catch (RuntimeException ex) {
            log.warn("Dropping malformed batch window {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }
}
