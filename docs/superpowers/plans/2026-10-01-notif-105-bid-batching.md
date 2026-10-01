# NOTIF-105: Outbid & New-Bid Batching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A user who is outbid several times within 5 minutes gets one notification immediately and one "outbid N more times, current price $Y" when the window closes. The seller's per-bid alerts are batched the same way. The ephemeral per-bid `OUTBID` push is removed, so there are no duplicate toasts.

**Architecture:**
- **Batching state.** A Redis hash per `(kind, userId, auctionId)` holds the open window. A Lua script decides atomically whether each bid is sent `IMMEDIATE` (it opens a window) or counted as `BATCHED`. Window closes are tracked in a due sorted set.
- **Flushing.** A `@Scheduled` flusher claims due windows with a second Lua script (`ZREM` + `HGETALL` + `DEL`; only the instance whose `ZREM` succeeds proceeds) and dispatches one batched intent through the existing `NotificationDispatcher`.
- **Fallback.** If Redis fails, every bid is sent immediately: noisy, never silent.
- **Removal.** The broadcaster stops pushing `OUTBID`. The auction page's socket hook shows the outbid toast from the stored `BID_OUTBID` `NOTIFICATION` push instead.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data Redis (Lettuce), Redis 7 Lua scripts, Spring `@Scheduled`, JUnit 5 + Mockito + AssertJ, Testcontainers Redis (`bdd-support` `RedisContainerSupport`). Frontend: Next.js 14 + TypeScript, `@stomp/stompjs`, sonner, vitest.

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`, specifically the **Decisions** section (1, 2, 10) and **Story 5**. Sources: `docs/epics/notification/notification-service-mvp.md` §2 "Outbid Alert – Smart Batching" and `docs/epics/notification/issue-13.md` §1. The window is 5 minutes per Decision 1, overriding #19's 2 minutes.

## Global Constraints

- media-service keeps its own DB (`media_*` tables). There are no cross-DB queries. Cross-service data comes from events or internal Feign endpoints only.
- Kafka publishes happen in `afterCommit()` hooks. Domain consumers use the shared `${spring.kafka.consumer.group-id}`.
- Every tunable is config, never hard-coded: batch window `notification.outbid.batch-window-seconds: 300`, flush delay `notification.outbid.flush-delay-ms: 15000`.
- Multi-instance safety: schedulers claim work atomically (the `ZREM` result in Redis). There is no ShedLock.
- Send-side failures never break the consumer. Redis failure → immediate dispatch + WARN.
- Batched alerts are **in-app only** (MVP doc table: "Outbid alert (batched) — Real-time", "New bid on auction (seller) — Real-time"). No email and no new templates.
- Dedup-key formats are stored data: never change an existing `DedupKeys` format. Only add new ones.
- **Agents do not commit.** Skip every commit step. The user commits.
- Maven runs from `backend/`: `mvn -q -pl media-service -am test`. Frontend runs from `frontend/`: `npm test`, `npm run lint`, `npm run build`.
- Conventional commit for the story (the user runs it): `feat(notification): batch outbid and new-bid alerts (NOTIF-105)`.

## Rulings (decisions this plan makes beyond the roadmap)

1. **Immediate alerts are keyed per bid.** The immediate alert uses `DedupKeys.bidAlert(type, bidId)` = `{TYPE}:BID:{bidId}`. The batched alert uses the existing `DedupKeys.batch(type, auctionId, windowStart)`. Both are unique per user through the `(user_id, dedup_key)` constraint. Keying the immediate alert on the bid makes a redelivered first bid map to the same row, regardless of the Redis state.
2. **Redelivery inside a window.** The Lua script remembers each bid's outcome in the hash (`bid:{bidId}` → `IMMEDIATE`|`BATCHED`):
   - A redelivered `IMMEDIATE` bid returns `IMMEDIATE` again, so the handler re-dispatches and the DB dedup absorbs it. This also covers "Redis recorded it, but the DB dispatch failed".
   - A redelivered `BATCHED` bid returns `DUPLICATE` and is not counted twice.
3. **`bidId` is required for batching.** bidding-service always sets it. If it's missing, the handler logs WARN and skips the outbid and new-bid alerts. `FIRST_BID` is unaffected.
4. **Seller alerts.** `totalBids == 1` → `FIRST_BID` (Story 4, unchanged). `totalBids > 1` → `NEW_BID` batch. `totalBids == null` → no seller alert.
5. **Frontend keeps the outbid toast.** Removing the backend `OUTBID` push would silently drop the toast until Story 8. `useAuctionSocket` therefore switches to the `NOTIFICATION` envelope and toasts only `BID_OUTBID`, using the notification's own message. Story 8 must take this toast over (noted in the roadmap) to avoid double toasts.
6. **Accepted edge cases:**
   - A batched alert may arrive after the auction has ended (at most one window late).
   - If dispatch fails after a successful claim, that one batch is lost (ERROR log).
   - The due set and the batch hashes use different keys, so the scripts assume a single Redis node, not Redis Cluster, as in docker-compose.

## File Structure

| File | Responsibility |
|---|---|
| `backend/media-service/src/main/java/com/bidnow/media/notification/DedupKeys.java` (modify) | Add `bidAlert(type, bidId)` |
| `.../notification/batch/BidAlertKind.java` (create) | `OUTBID` / `NEW_BID`, mapped to `NotificationType` |
| `.../notification/batch/BidAlert.java` (create) | One bid-driven alert for one recipient (the batcher's input) |
| `.../notification/batch/DueBatch.java` (create) | One closed window claimed for flushing |
| `.../notification/batch/BidBatchOutcome.java` (create) | `IMMEDIATE` / `BATCHED` / `DUPLICATE` |
| `.../notification/batch/BidAlerts.java` (create) | Builds the immediate and batched `NotificationIntent`s (copy, keys, metadata) |
| `.../notification/batch/BidBatcher.java` (create) | Redis window bookkeeping via the two Lua scripts, plus the Redis-down fallback |
| `backend/media-service/src/main/resources/redis/batch-record.lua` (create) | Atomic open-or-increment |
| `backend/media-service/src/main/resources/redis/batch-claim.lua` (create) | Atomic claim of one due window |
| `.../config/SchedulingConfig.java` (create) | `@EnableScheduling` (first scheduler in media-service) |
| `.../scheduler/BatchFlushScheduler.java` (create) | Every 15 s: claim due windows → dispatch batched intents |
| `.../notification/handler/BidNotificationHandler.java` (modify) | Outbid + new-bid alerts through the batcher |
| `.../realtime/AuctionRealtimeBroadcaster.java`, `AuctionRealtimeMessage.java`, `RealtimePayloads.java` (modify) | Remove the `OUTBID` user push |
| `backend/media-service/pom.xml`, `src/main/resources/application.yml` (modify) | Redis starter + config |
| `docker-compose.yml` (repo root, modify) | `REDIS_HOST` + `depends_on: redis` for media-service |
| `frontend/types/api/notification.api.ts` (create) | `NotificationDto`, `UserNotificationMessage` |
| `frontend/types/api/realtime.api.ts`, `frontend/lib/realtime/dispatch.ts`, `frontend/hooks/useAuctionSocket.ts` (modify) | Drop `OUTBID` and toast from `NOTIFICATION`/`BID_OUTBID` |
| `docs/architecture.md`, `docs/diagrams/03-bidding-antisniping-flow.md`, roadmap (modify) | Contract change |

Paths below use `M = backend/media-service/src/main/java/com/bidnow/media` and `T = backend/media-service/src/test/java/com/bidnow/media`.

---

### Task 1: Bid-alert model and copy

**Files:**
- Modify: `M/notification/DedupKeys.java`
- Create: `M/notification/batch/BidAlertKind.java`, `BidAlert.java`, `DueBatch.java`, `BidBatchOutcome.java`, `BidAlerts.java`
- Test: `T/notification/DedupKeysTest.java` (modify), `T/notification/batch/BidAlertsTest.java` (create)

**Interfaces:**
- Consumes: `NotificationIntent(UUID userId, NotificationType type, String dedupKey, UUID auctionId, String title, String message, String actionUrl, Map<String,Object> metadata, EmailSpec email)`, `NotificationFormats.money(BigDecimal)`, `NotificationLinks.auctionPath(UUID)`, `DedupKeys.batch(NotificationType, UUID, Instant)` (all existing).
- Produces:
  - `DedupKeys.bidAlert(NotificationType type, UUID bidId): String`
  - `enum BidAlertKind { OUTBID, NEW_BID; NotificationType type() }`
  - `record BidAlert(BidAlertKind kind, UUID userId, UUID auctionId, UUID bidId, String auctionTitle, BigDecimal amount)`
  - `record DueBatch(BidAlertKind kind, UUID userId, UUID auctionId, Instant windowStart, int count, String auctionTitle, BigDecimal latestAmount)`
  - `enum BidBatchOutcome { IMMEDIATE, BATCHED, DUPLICATE }`
  - `BidAlerts.immediate(BidAlert): NotificationIntent`, `BidAlerts.batched(DueBatch): NotificationIntent`

- [ ] **Step 1: Write the failing tests**

Add to `DedupKeysTest.formatsAreFixed()`, after the `batch` assertion:

```java
        assertThat(DedupKeys.bidAlert(NotificationType.BID_OUTBID, A)).isEqualTo("BID_OUTBID:BID:" + A);
```

Add to `DedupKeysTest.keysFitTheColumn()`:

```java
        assertThat(DedupKeys.bidAlert(NotificationType.BID_OUTBID, A).length()).isLessThanOrEqualTo(200);
```

Create `T/notification/batch/BidAlertsTest.java`:

```java
package com.bidnow.media.notification.batch;

import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationIntent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BidAlertsTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final Instant WINDOW = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void immediateOutbid_isKeyedPerBid() {
        NotificationIntent intent = BidAlerts.immediate(new BidAlert(BidAlertKind.OUTBID, ALICE, AUCTION, BID,
                "Vintage Watch", new BigDecimal("120")));

        assertThat(intent.userId()).isEqualTo(ALICE);
        assertThat(intent.type()).isEqualTo(NotificationType.BID_OUTBID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.bidAlert(NotificationType.BID_OUTBID, BID));
        assertThat(intent.auctionId()).isEqualTo(AUCTION);
        assertThat(intent.title()).isEqualTo("You've been outbid");
        assertThat(intent.message()).isEqualTo("Someone outbid you on \"Vintage Watch\". Current price: $120.00.");
        assertThat(intent.actionUrl()).isEqualTo("/auctions/" + AUCTION);
        assertThat(intent.metadata()).containsEntry("currentPrice", "120");
        assertThat(intent.email()).isNull();
    }

    @Test
    void immediateNewBid_addressesTheSeller() {
        NotificationIntent intent = BidAlerts.immediate(new BidAlert(BidAlertKind.NEW_BID, ALICE, AUCTION, BID,
                "Vintage Watch", new BigDecimal("120")));

        assertThat(intent.type()).isEqualTo(NotificationType.NEW_BID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.bidAlert(NotificationType.NEW_BID, BID));
        assertThat(intent.title()).isEqualTo("New bid on your auction");
        assertThat(intent.message()).isEqualTo("\"Vintage Watch\" received a new bid. Current price: $120.00.");
    }

    @Test
    void batchedOutbid_summarisesTheWindow() {
        NotificationIntent intent = BidAlerts.batched(new DueBatch(BidAlertKind.OUTBID, ALICE, AUCTION, WINDOW, 2,
                "Vintage Watch", new BigDecimal("130")));

        assertThat(intent.type()).isEqualTo(NotificationType.BID_OUTBID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.batch(NotificationType.BID_OUTBID, AUCTION, WINDOW));
        assertThat(intent.message())
                .isEqualTo("You've been outbid 2 more times on \"Vintage Watch\". Current price: $130.00.");
        assertThat(intent.metadata()).containsEntry("currentPrice", "130").containsEntry("count", 2);
        assertThat(intent.email()).isNull();
    }

    @Test
    void batchedCopy_usesSingularForOne() {
        assertThat(BidAlerts.batched(new DueBatch(BidAlertKind.OUTBID, ALICE, AUCTION, WINDOW, 1, "Vase",
                new BigDecimal("5"))).message()).contains("outbid 1 more time on");
        assertThat(BidAlerts.batched(new DueBatch(BidAlertKind.NEW_BID, ALICE, AUCTION, WINDOW, 1, "Vase",
                new BigDecimal("5"))).message()).isEqualTo("\"Vase\" received 1 more bid. Current price: $5.00.");
    }

    @Test
    void batchedNewBid_countsBids() {
        NotificationIntent intent = BidAlerts.batched(new DueBatch(BidAlertKind.NEW_BID, ALICE, AUCTION, WINDOW, 3,
                "Vase", new BigDecimal("150")));

        assertThat(intent.type()).isEqualTo(NotificationType.NEW_BID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.batch(NotificationType.NEW_BID, AUCTION, WINDOW));
        assertThat(intent.message()).isEqualTo("\"Vase\" received 3 more bids. Current price: $150.00.");
    }

    @Test
    void missingTitleAndAmount_degradeGracefully() {
        NotificationIntent intent = BidAlerts.immediate(new BidAlert(BidAlertKind.OUTBID, ALICE, AUCTION, BID,
                null, null));

        assertThat(intent.message()).isEqualTo("Someone outbid you on your auction.");
        assertThat(intent.metadata()).doesNotContainKey("currentPrice");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl media-service -am test -Dtest='DedupKeysTest,BidAlertsTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure. `bidAlert`, `BidAlert`, `BidAlertKind`, `DueBatch` and `BidAlerts` are not defined.

- [ ] **Step 3: Implement**

Add to `DedupKeys` (after `batch`):

```java
    /** The immediate (window-opening) outbid / new-bid alert: one per bid, so a redelivered bid is a no-op. */
    public static String bidAlert(NotificationType type, UUID bidId) {
        return type.name() + ":BID:" + bidId;
    }
```

Create `M/notification/batch/BidAlertKind.java`:

```java
package com.bidnow.media.notification.batch;

import com.bidnow.media.domain.enums.NotificationType;

/** The two batched bid alerts: the previous leader was outbid, or the seller received a bid. */
public enum BidAlertKind {
    OUTBID(NotificationType.BID_OUTBID),
    NEW_BID(NotificationType.NEW_BID);

    private final NotificationType type;

    BidAlertKind(NotificationType type) {
        this.type = type;
    }

    public NotificationType type() {
        return type;
    }
}
```

Create `M/notification/batch/BidAlert.java`:

```java
package com.bidnow.media.notification.batch;

import java.math.BigDecimal;
import java.util.UUID;

/** One bid that should alert one user; {@code amount} is the new current price. */
public record BidAlert(BidAlertKind kind, UUID userId, UUID auctionId, UUID bidId, String auctionTitle,
                       BigDecimal amount) {
}
```

Create `M/notification/batch/DueBatch.java`:

```java
package com.bidnow.media.notification.batch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A closed batching window: {@code count} alerts arrived after the immediate one. */
public record DueBatch(BidAlertKind kind, UUID userId, UUID auctionId, Instant windowStart, int count,
                       String auctionTitle, BigDecimal latestAmount) {
}
```

Create `M/notification/batch/BidBatchOutcome.java`:

```java
package com.bidnow.media.notification.batch;

/** What to do with a bid alert: send it now, leave it to the window flush, or ignore a redelivery. */
public enum BidBatchOutcome {
    IMMEDIATE,
    BATCHED,
    DUPLICATE
}
```

Create `M/notification/batch/BidAlerts.java`:

```java
package com.bidnow.media.notification.batch;

import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.bidnow.media.notification.NotificationFormats.money;

/** In-app copy for outbid / new-bid alerts. In-app only: no email (MVP: real-time channel). */
public final class BidAlerts {

    private static final String OUTBID_TITLE = "You've been outbid";
    private static final String NEW_BID_TITLE = "New bid on your auction";

    private BidAlerts() {
    }

    public static NotificationIntent immediate(BidAlert alert) {
        String title = quoted(alert.auctionTitle());
        String message = alert.kind() == BidAlertKind.OUTBID
                ? "Someone outbid you on " + title + "." + price(alert.amount())
                : title + " received a new bid." + price(alert.amount());
        return new NotificationIntent(alert.userId(), alert.kind().type(),
                DedupKeys.bidAlert(alert.kind().type(), alert.bidId()), alert.auctionId(), title(alert.kind()),
                message, NotificationLinks.auctionPath(alert.auctionId()), metadata(alert.amount(), null), null);
    }

    public static NotificationIntent batched(DueBatch batch) {
        String title = quoted(batch.auctionTitle());
        int n = batch.count();
        String message = batch.kind() == BidAlertKind.OUTBID
                ? "You've been outbid " + n + (n == 1 ? " more time" : " more times") + " on " + title + "."
                        + price(batch.latestAmount())
                : title + " received " + n + (n == 1 ? " more bid" : " more bids") + "." + price(batch.latestAmount());
        return new NotificationIntent(batch.userId(), batch.kind().type(),
                DedupKeys.batch(batch.kind().type(), batch.auctionId(), batch.windowStart()), batch.auctionId(),
                title(batch.kind()), message, NotificationLinks.auctionPath(batch.auctionId()),
                metadata(batch.latestAmount(), n), null);
    }

    private static String title(BidAlertKind kind) {
        return kind == BidAlertKind.OUTBID ? OUTBID_TITLE : NEW_BID_TITLE;
    }

    private static String quoted(String auctionTitle) {
        return auctionTitle == null || auctionTitle.isBlank() ? "your auction" : "\"" + auctionTitle + "\"";
    }

    private static String price(BigDecimal amount) {
        return amount == null ? "" : " Current price: " + money(amount) + ".";
    }

    private static Map<String, Object> metadata(BigDecimal amount, Integer count) {
        Map<String, Object> metadata = new HashMap<>();
        if (amount != null) {
            metadata.put("currentPrice", amount.toPlainString());
        }
        if (count != null) {
            metadata.put("count", count);
        }
        return metadata;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='DedupKeysTest,BidAlertsTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (all tests in both classes).

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 2: Redis batching (`BidBatcher` + Lua)

**Files:**
- Modify: `backend/media-service/pom.xml`, `backend/media-service/src/main/resources/application.yml`, `docker-compose.yml` (repo root)
- Create: `backend/media-service/src/main/resources/redis/batch-record.lua`, `backend/media-service/src/main/resources/redis/batch-claim.lua`, `M/notification/batch/BidBatcher.java`
- Test: `T/notification/batch/BidBatcherRedisIT.java` (create; needs Docker), `T/notification/batch/BidBatcherTest.java` (create)

**Interfaces:**
- Consumes: `BidAlert`, `BidAlertKind`, `DueBatch`, `BidBatchOutcome` (Task 1); `Clock` bean (`config/ClockConfig`); `RedisContainerSupport.REDIS` (`com.bidnow.bdd.container`, already on the media-service test classpath via `bdd-support`).
- Produces:
  - `@Component BidBatcher(StringRedisTemplate redis, Clock clock, @Value("${notification.outbid.batch-window-seconds:300}") long windowSeconds)`
  - `BidBatchOutcome record(BidAlert alert)`: never throws. On a Redis failure it returns `IMMEDIATE`.
  - `List<DueBatch> claimDue(Instant now, int limit)`: may throw on a Redis failure (the caller handles it).
  - `static String batchKey(BidAlertKind kind, UUID userId, UUID auctionId)` = `notif:batch:{kind}:{userId}:{auctionId}`
  - `static final String DUE_KEY = "notif:batch:due"`

- [ ] **Step 1: Add the Redis dependency and configuration**

In `backend/media-service/pom.xml`, add after the `spring-boot-starter-mail` dependency:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
```

In `backend/media-service/src/main/resources/application.yml`, add under `spring:`, after the `mail:` block and before `servlet:`, at the same indentation as `mail:`:

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 200ms
      connect-timeout: 500ms
```

Under `management:`, add before `tracing:` (same indentation):

```yaml
  health:
    redis:
      enabled: false   # Redis only batches alerts; its outage must not mark media-service DOWN
```

At the end of the file, add:

```yaml

notification:
  outbid:
    batch-window-seconds: 300   # outbid / new-bid batching window (Decision 1)
    flush-delay-ms: 15000       # how often due windows are flushed
```

In the repo-root `docker-compose.yml`, `media-service` service:
- Add `      - REDIS_HOST=redis` to `environment:` after `MAIL_PORT=1025`.
- Add to `depends_on:`:

```yaml
      redis:
        condition: service_healthy
```

- [ ] **Step 2: Write the failing tests**

Create `T/notification/batch/BidBatcherRedisIT.java`:

```java
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
}
```

Create `T/notification/batch/BidBatcherTest.java`:

```java
package com.bidnow.media.notification.batch;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `mvn -q -pl media-service -am test -Dtest='BidBatcherTest,BidBatcherRedisIT' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure (`BidBatcher` is not defined). Docker must be running for the IT.

- [ ] **Step 4: Write the Lua scripts**

Create `backend/media-service/src/main/resources/redis/batch-record.lua`:

```lua
-- Open a batching window or count a bid into the open one. Atomic.
-- KEYS[1] batch hash, KEYS[2] due sorted set
-- ARGV[1] now (epoch ms)  ARGV[2] due at (epoch ms)  ARGV[3] ttl (ms)  ARGV[4] bidId
-- ARGV[5] amount ('' if none)  ARGV[6] auction title ('' if none)  ARGV[7] kind  ARGV[8] userId  ARGV[9] auctionId
-- Returns IMMEDIATE (send now), BATCHED (counted for the flush) or DUPLICATE (a batched bid seen before).
local bidField = 'bid:' .. ARGV[4]
local seen = redis.call('HGET', KEYS[1], bidField)
if seen then
  if seen == 'IMMEDIATE' then
    return 'IMMEDIATE'
  end
  return 'DUPLICATE'
end
if redis.call('HSETNX', KEYS[1], 'windowStart', ARGV[1]) == 1 then
  redis.call('HSET', KEYS[1], 'count', '0', 'latestAmount', ARGV[5], 'auctionTitle', ARGV[6],
    'kind', ARGV[7], 'userId', ARGV[8], 'auctionId', ARGV[9], bidField, 'IMMEDIATE')
  redis.call('PEXPIRE', KEYS[1], ARGV[3])
  redis.call('ZADD', KEYS[2], ARGV[2], KEYS[1])
  return 'IMMEDIATE'
end
redis.call('HINCRBY', KEYS[1], 'count', 1)
redis.call('HSET', KEYS[1], 'latestAmount', ARGV[5], 'auctionTitle', ARGV[6], bidField, 'BATCHED')
return 'BATCHED'
```

Create `backend/media-service/src/main/resources/redis/batch-claim.lua`:

```lua
-- Claim one due window. Only the caller whose ZREM succeeds gets the fields; the window is deleted. Atomic.
-- KEYS[1] batch hash, KEYS[2] due sorted set
-- Returns the hash as a flat field/value list, or an empty list if another instance claimed it (or it expired).
if redis.call('ZREM', KEYS[2], KEYS[1]) == 0 then
  return {}
end
local fields = redis.call('HGETALL', KEYS[1])
redis.call('DEL', KEYS[1])
return fields
```

- [ ] **Step 5: Implement `BidBatcher`**

Create `M/notification/batch/BidBatcher.java`:

```java
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
            List<String> fields = redis.execute(claimScript, List.of(key, DUE_KEY));
            toDueBatch(key, fields).ifPresent(claimed::add);
        }
        return claimed;
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
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='BidBatcherTest,BidBatcherRedisIT' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `BidBatcherTest` has 3 tests and `BidBatcherRedisIT` has 9.

Then run the default suite, which does not include `*IT`, to make sure nothing else broke:
Run: `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**: skipped (the user commits).

---

### Task 3: Flush scheduler

**Files:**
- Create: `M/config/SchedulingConfig.java`, `M/scheduler/BatchFlushScheduler.java`
- Test: `T/scheduler/BatchFlushSchedulerTest.java`

**Interfaces:**
- Consumes: `BidBatcher.claimDue(Instant, int): List<DueBatch>` (Task 2); `BidAlerts.batched(DueBatch): NotificationIntent` (Task 1); `NotificationDispatcher.dispatch(NotificationIntent)` (existing); the `Clock` bean.
- Produces: `@Component BatchFlushScheduler(BidBatcher batcher, NotificationDispatcher dispatcher, Clock clock)` with `void flush()`, scheduled with `fixedDelayString = "${notification.outbid.flush-delay-ms:15000}"`, and `static final int CLAIM_LIMIT = 100`.

- [ ] **Step 1: Write the failing test**

Create `T/scheduler/BatchFlushSchedulerTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=BatchFlushSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure (`BatchFlushScheduler` is not defined).

- [ ] **Step 3: Implement**

Create `M/config/SchedulingConfig.java`:

```java
package com.bidnow.media.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

Create `M/scheduler/BatchFlushScheduler.java`:

```java
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl media-service -am test -Dtest=BatchFlushSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 4: Handler sends outbid and new-bid alerts through the batcher

**Files:**
- Modify: `M/notification/handler/BidNotificationHandler.java`
- Test: `T/notification/handler/BidNotificationHandlerTest.java` (replace)

**Interfaces:**
- Consumes: `BidBatcher.record(BidAlert): BidBatchOutcome` (Task 2); `BidAlerts.immediate(BidAlert)` (Task 1); `AuctionLookup.title(UUID, String)`, `AuctionLookup.sellerId(UUID): Optional<UUID>`, `NotificationDispatcher.dispatch(NotificationIntent)` (existing); `BidPlacedEvent` fields `auctionId, auctionTitle, bidderId, bidAmount, previousHighestBidderId, bidId, totalBids` (common).
- Produces: `BidNotificationHandler(NotificationDispatcher, AuctionLookup, BidBatcher)`, `void bidPlaced(BidPlacedEvent)`. The consumer wiring (`NotificationKafkaConsumer.consumeBidPlaced`) is unchanged.

- [ ] **Step 1: Write the failing test**

Replace `T/notification/handler/BidNotificationHandlerTest.java` with:

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.batch.BidAlert;
import com.bidnow.media.notification.batch.BidAlertKind;
import com.bidnow.media.notification.batch.BidAlerts;
import com.bidnow.media.notification.batch.BidBatchOutcome;
import com.bidnow.media.notification.batch.BidBatcher;
import com.bidnow.media.projection.AuctionLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID BID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;
    @Mock
    private BidBatcher batcher;

    @InjectMocks
    private BidNotificationHandler handler;

    private static BidPlacedEvent bid(Integer totalBids, UUID previousLeader) {
        return BidPlacedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch").bidId(BID)
                .bidderId(BOB).bidAmount(new BigDecimal("120")).previousHighestBidderId(previousLeader)
                .totalBids(totalBids).build();
    }

    private void stubAuction() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));
    }

    private List<BidAlert> recordedAlerts(int expected) {
        ArgumentCaptor<BidAlert> alerts = ArgumentCaptor.forClass(BidAlert.class);
        verify(batcher, times(expected)).record(alerts.capture());
        return alerts.getAllValues();
    }

    @Test
    void firstBid_notifiesSellerInApp_withoutBatching() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.bidPlaced(bid(1, null));

        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        assertThat(intent.getValue().userId()).isEqualTo(SELLER);
        assertThat(intent.getValue().type()).isEqualTo(NotificationType.FIRST_BID);
        assertThat(intent.getValue().dedupKey()).isEqualTo(DedupKeys.firstBid(AUCTION));
        assertThat(intent.getValue().message()).contains("$120.00");
        assertThat(intent.getValue().email()).isNull();
        verifyNoInteractions(batcher);
    }

    @Test
    void firstBid_unknownSeller_isSkipped() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.empty());

        handler.bidPlaced(bid(1, null));

        verifyNoInteractions(dispatcher, batcher);
    }

    @Test
    void outbid_openingAWindow_dispatchesBothAlertsImmediately() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.IMMEDIATE);

        handler.bidPlaced(bid(3, ALICE));

        BidAlert outbid = new BidAlert(BidAlertKind.OUTBID, ALICE, AUCTION, BID, "Vintage Watch", new BigDecimal("120"));
        BidAlert newBid = new BidAlert(BidAlertKind.NEW_BID, SELLER, AUCTION, BID, "Vintage Watch", new BigDecimal("120"));
        assertThat(recordedAlerts(2)).containsExactly(outbid, newBid);
        ArgumentCaptor<NotificationIntent> intents = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher, times(2)).dispatch(intents.capture());
        assertThat(intents.getAllValues()).containsExactly(BidAlerts.immediate(outbid), BidAlerts.immediate(newBid));
    }

    @Test
    void batchedOrDuplicateAlerts_areNotDispatched() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED, BidBatchOutcome.DUPLICATE);

        handler.bidPlaced(bid(3, ALICE));

        verify(batcher, times(2)).record(any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void leaderRaisingOwnBid_onlyAlertsTheSeller() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(3, BOB));

        assertThat(recordedAlerts(1)).extracting(BidAlert::kind).containsExactly(BidAlertKind.NEW_BID);
    }

    @Test
    void noPreviousLeader_onlyAlertsTheSeller() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(2, null));

        assertThat(recordedAlerts(1)).extracting(BidAlert::userId).containsExactly(SELLER);
    }

    @Test
    void unknownSeller_stillAlertsTheOutbidUser() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.empty());
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(3, ALICE));

        assertThat(recordedAlerts(1)).extracting(BidAlert::userId).containsExactly(ALICE);
    }

    @Test
    void unknownTotalBids_skipsTheSellerAlert() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(null, ALICE));

        assertThat(recordedAlerts(1)).extracting(BidAlert::kind).containsExactly(BidAlertKind.OUTBID);
    }

    @Test
    void missingBidId_skipsBatchedAlerts() {
        BidPlacedEvent event = bid(3, ALICE);
        event.setBidId(null);

        handler.bidPlaced(event);

        verifyNoInteractions(batcher, dispatcher);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl media-service -am test -Dtest=BidNotificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The outbid/new-bid tests fail because no `record` call is made, `firstBid_*` still passes, and `missingBidId` may pass. If `@InjectMocks` cannot place `batcher`, the result is also FAIL: the handler has no `BidBatcher` field yet, so Mockito injects nothing and the outbid tests fail.

- [ ] **Step 3: Implement**

Replace `M/notification/handler/BidNotificationHandler.java` with:

```java
package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.notification.batch.BidAlert;
import com.bidnow.media.notification.batch.BidAlertKind;
import com.bidnow.media.notification.batch.BidAlerts;
import com.bidnow.media.notification.batch.BidBatchOutcome;
import com.bidnow.media.notification.batch.BidBatcher;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.money;

/**
 * Bid events → in-app alerts. The first bid tells the seller immediately (FIRST_BID). Later bids alert the
 * previous leader (BID_OUTBID) and the seller (NEW_BID) through 5-minute batching windows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;
    private final BidBatcher batcher;

    public void bidPlaced(BidPlacedEvent event) {
        Integer totalBids = event.getTotalBids();
        if (totalBids != null && totalBids == 1) {
            firstBid(event);
            return;
        }
        UUID auctionId = event.getAuctionId();
        if (event.getBidId() == null) {
            log.warn("Bid on auction {} has no bidId - outbid / new-bid alerts skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        UUID previous = event.getPreviousHighestBidderId();
        if (previous != null && !previous.equals(event.getBidderId())) {
            alert(new BidAlert(BidAlertKind.OUTBID, previous, auctionId, event.getBidId(), title, event.getBidAmount()));
        }
        if (totalBids != null && totalBids > 1) {
            Optional<UUID> seller = auctions.sellerId(auctionId);
            if (seller.isPresent()) {
                alert(new BidAlert(BidAlertKind.NEW_BID, seller.get(), auctionId, event.getBidId(), title,
                        event.getBidAmount()));
            } else {
                log.debug("Bid on auction {} but its seller is not projected yet - new-bid alert skipped", auctionId);
            }
        }
    }

    private void alert(BidAlert alert) {
        if (batcher.record(alert) == BidBatchOutcome.IMMEDIATE) {
            dispatcher.dispatch(BidAlerts.immediate(alert));
        }
    }

    private void firstBid(BidPlacedEvent event) {
        UUID auctionId = event.getAuctionId();
        Optional<UUID> seller = auctions.sellerId(auctionId);
        if (seller.isEmpty()) {
            log.warn("First bid on auction {} but its seller is not projected yet - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        dispatcher.dispatch(new NotificationIntent(seller.get(), NotificationType.FIRST_BID,
                DedupKeys.firstBid(auctionId), auctionId, "First bid received",
                "\"" + title + "\" received its first bid: " + money(event.getBidAmount()) + ".",
                NotificationLinks.auctionPath(auctionId), null, null));
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='BidNotificationHandlerTest,NotificationKafkaConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (9 handler tests; consumer tests unchanged).

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 5: Remove the ephemeral OUTBID push and move the frontend toast to `NOTIFICATION`

**Files:**
- Modify: `M/realtime/AuctionRealtimeBroadcaster.java`, `M/realtime/AuctionRealtimeMessage.java`, `M/realtime/RealtimePayloads.java`
- Modify tests: `T/realtime/AuctionRealtimeBroadcasterTest.java`, `T/realtime/RealtimeMessageJsonTest.java`
- Create: `frontend/types/api/notification.api.ts`, `frontend/lib/realtime/dispatch.test.ts`
- Modify: `frontend/types/api/realtime.api.ts`, `frontend/lib/realtime/dispatch.ts`, `frontend/hooks/useAuctionSocket.ts`
- Modify docs: `docs/architecture.md`, `docs/diagrams/03-bidding-antisniping-flow.md`, `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`

**Interfaces:**
- Consumes: the `/user/queue/notifications` envelope `{type: "NOTIFICATION", notification: NotificationResponse, unreadCount}` (`UserNotificationMessage`, Story 1). The `NotificationResponse` JSON fields are `id, type, title, message, actionUrl, auctionId, metadata, read, createdAt`.
- Produces (frontend):
  - `parseUserNotification(body: string): NotificationDto | null` in `lib/realtime/dispatch.ts`
  - `NotificationDto` and `UserNotificationMessage` in `types/api/notification.api.ts` (Story 8 extends these)
  - `RealtimeHandlers` loses `outbid`

- [ ] **Step 1: Update the backend tests to assert the new contract (failing)**

In `T/realtime/AuctionRealtimeBroadcasterTest.java`:
- Delete the tests `bidPlaced_notifiesOutbidPreviousLeader`, `bidPlaced_noPreviousLeader_sendsNoOutbid` and `bidPlaced_leaderRaisingOwnBid_sendsNoOutbid`.
- Add:

```java
    @Test
    void bidPlaced_sendsNoUserQueueMessage() {
        broadcaster.bidPlaced(bidPlaced(PREVIOUS));

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
```

Keep the `PREVIOUS` constant: the new test uses it.

In `T/realtime/RealtimeMessageJsonTest.java`, delete the `outbid()` test method.

Run: `mvn -q -pl media-service -am test -Dtest='AuctionRealtimeBroadcasterTest,RealtimeMessageJsonTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. `bidPlaced_sendsNoUserQueueMessage` fails because the broadcaster still calls `convertAndSendToUser`.

- [ ] **Step 2: Remove the OUTBID push**

In `M/realtime/AuctionRealtimeBroadcaster.java`:
- Delete the `USER_QUEUE` constant, the whole `UUID previous = …` block at the end of `bidPlaced`, and the `sendToUser` method.
- Remove the now-unused import `java.util.UUID` only if nothing else uses it. `auctionTopic(UUID)` and `sendToAuction(UUID, …)` still do, so keep it.

The resulting `bidPlaced` is:

```java
    public void bidPlaced(BidPlacedEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.BID_PLACED, new RealtimePayloads.BidPlaced(
                event.getBidId(),
                event.getBidderId(),
                event.getBidderName(),
                event.getBidAmount(),
                event.getBidTime() == null ? null : event.getBidTime().atOffset(ZoneOffset.UTC),
                event.getTotalBids(),
                event.getEndTime(),
                event.isAntiSnipingTriggered()));
    }
```

In `M/realtime/AuctionRealtimeMessage.java`, delete `public static final String OUTBID = "OUTBID";`.

In `M/realtime/RealtimePayloads.java`, delete the `Outbid` record.

Run: `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS. If compilation fails, a stale `OUTBID`/`Outbid` reference remains. Find it with `grep -rn "OUTBID\|Outbid(" backend/media-service/src` and remove it; `BID_OUTBID` matches are fine.

- [ ] **Step 3: Write the failing frontend test**

Create `frontend/lib/realtime/dispatch.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { dispatchRealtimeMessage, parseRealtimeMessage, parseUserNotification, type RealtimeHandlers } from './dispatch'

const notification = {
  id: 'n-1',
  type: 'BID_OUTBID',
  title: "You've been outbid",
  message: 'Someone outbid you on "Vase". Current price: $120.00.',
  actionUrl: '/auctions/a-1',
  auctionId: 'a-1',
  metadata: { currentPrice: '120' },
  read: false,
  createdAt: '2026-10-01T10:00:00',
}

describe('parseUserNotification', () => {
  it('returns the notification from a NOTIFICATION envelope', () => {
    const body = JSON.stringify({ type: 'NOTIFICATION', notification, unreadCount: 3 })

    expect(parseUserNotification(body)).toEqual(notification)
  })

  it('rejects other envelopes, malformed JSON and incomplete notifications', () => {
    expect(parseUserNotification(JSON.stringify({ type: 'OUTBID', auctionId: 'a-1', payload: {} }))).toBeNull()
    expect(parseUserNotification('not json')).toBeNull()
    expect(parseUserNotification(JSON.stringify({ type: 'NOTIFICATION' }))).toBeNull()
    expect(parseUserNotification(JSON.stringify({ type: 'NOTIFICATION', notification: { id: 'n-1' } }))).toBeNull()
  })
})

describe('parseRealtimeMessage', () => {
  it('no longer accepts the removed OUTBID message', () => {
    const body = JSON.stringify({ type: 'OUTBID', auctionId: 'a-1', payload: { currentPrice: 1 } })

    expect(parseRealtimeMessage(body)).toBeNull()
  })
})

describe('dispatchRealtimeMessage', () => {
  const handlers = (): RealtimeHandlers => ({
    bidPlaced: vi.fn(), extended: vi.fn(), ended: vi.fn(), cancelled: vi.fn(),
  })

  it('ignores messages for another auction', () => {
    const h = handlers()
    const msg = parseRealtimeMessage(JSON.stringify({ type: 'AUCTION_CANCELLED', auctionId: 'a-2', payload: { reason: null } }))

    expect(dispatchRealtimeMessage(msg!, 'a-1', h)).toBe(false)
    expect(h.cancelled).not.toHaveBeenCalled()
  })

  it('routes messages for the open auction', () => {
    const h = handlers()
    const msg = parseRealtimeMessage(JSON.stringify({ type: 'AUCTION_CANCELLED', auctionId: 'a-1', payload: { reason: 'fraud' } }))

    expect(dispatchRealtimeMessage(msg!, 'a-1', h)).toBe(true)
    expect(h.cancelled).toHaveBeenCalledWith({ reason: 'fraud' })
  })
})
```

Run (from `frontend/`): `npx vitest run lib/realtime/dispatch.test.ts`
Expected: FAIL. `parseUserNotification` is not exported, and `OUTBID` still parses.

- [ ] **Step 4: Implement the frontend change**

Create `frontend/types/api/notification.api.ts`:

```ts
/** A stored notification, as listed by /api/v1/notifications and pushed on /user/queue/notifications. */
export interface NotificationDto {
  id: string
  /** media-service NotificationType, e.g. BID_OUTBID, NEW_BID, AUCTION_WON */
  type: string
  title: string
  message: string
  actionUrl: string | null
  auctionId: string | null
  metadata: Record<string, unknown> | null
  read: boolean
  /** ISO local date-time */
  createdAt: string
}

/** STOMP envelope on /user/queue/notifications. */
export interface UserNotificationMessage {
  type: 'NOTIFICATION'
  notification: NotificationDto
  unreadCount: number
}
```

In `frontend/types/api/realtime.api.ts`:
- Delete the `OutbidPayload` interface.
- Delete the `| { type: 'OUTBID'; … }` union member.
- Remove `'OUTBID'` from `REALTIME_MESSAGE_TYPES`, which becomes:

```ts
export const REALTIME_MESSAGE_TYPES: ReadonlyArray<AuctionRealtimeMessage['type']> = [
  'BID_PLACED', 'AUCTION_EXTENDED', 'AUCTION_ENDED', 'AUCTION_CANCELLED',
]
```

Replace `frontend/lib/realtime/dispatch.ts` with:

```ts
import {
  REALTIME_MESSAGE_TYPES,
  type AuctionCancelledPayload,
  type AuctionEndedPayload,
  type AuctionExtendedPayload,
  type AuctionRealtimeMessage,
  type BidPlacedPayload,
} from '@/types/api/realtime.api'
import type { NotificationDto } from '@/types/api/notification.api'

export interface RealtimeHandlers {
  bidPlaced(p: BidPlacedPayload): void
  extended(p: AuctionExtendedPayload): void
  ended(p: AuctionEndedPayload): void
  cancelled(p: AuctionCancelledPayload): void
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseJson(body: string): unknown {
  try {
    return JSON.parse(body)
  } catch {
    return null
  }
}

/** Validates the envelope only; payload fields are trusted per the media-service contract (spec §6). */
export function parseRealtimeMessage(body: string): AuctionRealtimeMessage | null {
  const parsed = parseJson(body)
  if (!isRecord(parsed)) return null
  const { type, auctionId, payload } = parsed
  if (typeof type !== 'string' || typeof auctionId !== 'string' || !isRecord(payload)) return null
  if (!(REALTIME_MESSAGE_TYPES as ReadonlyArray<string>).includes(type)) return null
  return parsed as unknown as AuctionRealtimeMessage
}

/** The stored notification inside a `/user/queue/notifications` NOTIFICATION envelope, or null. */
export function parseUserNotification(body: string): NotificationDto | null {
  const parsed = parseJson(body)
  if (!isRecord(parsed) || parsed.type !== 'NOTIFICATION' || !isRecord(parsed.notification)) return null
  const n = parsed.notification
  if (typeof n.id !== 'string' || typeof n.type !== 'string' || typeof n.message !== 'string') return null
  return n as unknown as NotificationDto
}

/** Returns true when a handler ran. Messages for another auction are ignored. */
export function dispatchRealtimeMessage(
  msg: AuctionRealtimeMessage,
  auctionId: string,
  h: RealtimeHandlers,
): boolean {
  if (msg.auctionId !== auctionId) return false
  switch (msg.type) {
    case 'BID_PLACED':        h.bidPlaced(msg.payload); return true
    case 'AUCTION_EXTENDED':  h.extended(msg.payload);  return true
    case 'AUCTION_ENDED':     h.ended(msg.payload);     return true
    case 'AUCTION_CANCELLED': h.cancelled(msg.payload); return true
  }
}
```

In `frontend/hooks/useAuctionSocket.ts`:
1. Change the dispatch import to:
   ```ts
   import { dispatchRealtimeMessage, parseRealtimeMessage, parseUserNotification, type RealtimeHandlers } from '@/lib/realtime/dispatch'
   ```
2. Remove the `formatCurrency` import, which is now unused.
3. Delete the `outbid: (outbidAuctionId, p) => { … },` entry from `handlers`.
4. After the `onMessage` definition, add:
   ```ts
    // Outbid alerts are stored notifications (batched per 5 minutes by media-service). Story 8's notification
    // center takes this toast over; until then the auction page shows it.
    const onUserMessage = (message: IMessage) => {
      const n = parseUserNotification(message.body)
      if (n?.type !== 'BID_OUTBID' || !n.auctionId) return
      // The store no-ops unless this is the open auction; the toast shows for any auction.
      useAuctionStore.getState().outbid(n.auctionId)
      toast.warning(n.message)
    }
   ```
5. In `onConnect`, change `if (userId) client.subscribe(USER_QUEUE, onMessage)` to `if (userId) client.subscribe(USER_QUEUE, onUserMessage)`.
6. Update the hook's doc comment: "the private notification queue" → "the private notification queue (outbid toasts)".

- [ ] **Step 5: Run the frontend checks**

Run (from `frontend/`): `npx vitest run lib/realtime/dispatch.test.ts`
Expected: PASS (6 tests).

Run: `npm run lint && npm run build`
Expected: both succeed. If the build reports leftover `OutbidPayload`/`outbid` references, run `grep -rn "OutbidPayload\|'OUTBID'" frontend --include=*.ts --include=*.tsx --exclude-dir=node_modules`. Remove each match outside `lib/realtime/dispatch.test.ts`. `applyOutbid` and the store's `outbid` action stay.

- [ ] **Step 6: Update the docs**

In `docs/architecture.md` (Private queue bullet, around line 65), replace:

```
plus the legacy per-bid `OUTBID` `{auctionTitle, currentPrice, newLeaderName}` until Story 5 removes it.
```

with:

```
outbid (`BID_OUTBID`, previous leader) and seller new-bid (`NEW_BID`) alerts are stored notifications batched per user and auction: the first in a quiet 5-minute window is sent immediately, later ones are summed into one notification when the window closes (state in media-service's Redis, `notification.outbid.batch-window-seconds`).
```

In `docs/diagrams/03-bidding-antisniping-flow.md`, replace the line:

```
        MS-->>App: /user/queue/notifications: OUTBID (previous leader only)
```

with:

```
        MS-->>App: /user/queue/notifications: NOTIFICATION BID_OUTBID (previous leader; batched per 5 min)
```

In `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`, append to the end of the `7.5 STOMP hook` bullet:

```
 *(NOTIF-105: the per-bid `OUTBID` user push is gone; the hook toasts `NOTIFICATION` messages of type `BID_OUTBID` from `/user/queue/notifications` instead.)*
```

- [ ] **Step 7: Commit**: skipped (the user commits).

---

### Task 6: Story verification and roadmap

**Files:**
- Modify: `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`

- [ ] **Step 1: Run the full suites**

Run (from `backend/`): `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS.

Run (Docker required): `mvn -q -pl media-service -am test -Dtest=BidBatcherRedisIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (9 tests).

Run (from `frontend/`): `npm test && npm run lint && npm run build`
Expected: all succeed.

- [ ] **Step 2: Update the roadmap**

In Story 5 of the roadmap:
- Tick tasks 5.1–5.4. Mark 5.5 "(manual smoke handed to the user)".
- Add a "Refinements" paragraph:

```
**Refinements (implemented):** immediate alerts are keyed per bid (`DedupKeys.bidAlert` = `{TYPE}:BID:{bidId}`), batched ones per window (`DedupKeys.batch`); the record script remembers each bid's outcome so a redelivered immediate bid re-dispatches (DB dedup absorbs it) and a redelivered batched bid is not counted twice; claim is one Lua script (ZREM + HGETALL + DEL) so a bid arriving during a flush is never lost; alerts are in-app only; the auction page's outbid toast now comes from the `BID_OUTBID` NOTIFICATION push (Story 8 must take it over to avoid double toasts).
```

In Story 8's file list, add this bullet:

```
- `hooks/useAuctionSocket.ts`: remove the interim `BID_OUTBID` toast (`onUserMessage`, NOTIF-105) once the global notification toasts land, so outbids are not toasted twice.
```

In "Risks carried forward", add:

```
- **Batched alerts are single-node Redis only.** The record/claim scripts touch a batch hash and the shared due set, which live in different hash slots; Redis Cluster would reject them. Use hash tags (`{notif}`) before moving to a cluster. A batched alert can also land up to one window after the auction ended, and a dispatch failure after a claim loses that one batch (ERROR log).
```

- [ ] **Step 3: Manual smoke (handed to the user; a merge gate)**

The user runs this with `docker compose up` (Redis healthy) and `notification.outbid.batch-window-seconds: 30` set locally:
1. User A bids, then users B, C and D each outbid quickly (within 30 s).
2. A's auction page shows one "You've been outbid" toast immediately. About 30–45 s later a second toast appears: "You've been outbid 2 more times … Current price: $D".
3. `media_notifications` holds exactly 2 `BID_OUTBID` rows for A: one keyed `BID_OUTBID:BID:{bidId}` and one keyed `BID_OUTBID:{auctionId}:{windowStart}`.
4. The seller has 1 `FIRST_BID`, 1 immediate `NEW_BID` and 1 batched `NEW_BID` ("received 1 more bid", or more depending on timing).
5. Stop Redis (`docker stop bidnow-redis`) and place two bids. Both alerts arrive immediately, and the media log shows `Bid batching unavailable … sending immediately`.

- [ ] **Step 4: Commit**: skipped. The user commits `feat(notification): batch outbid and new-bid alerts (NOTIF-105)`.
```
