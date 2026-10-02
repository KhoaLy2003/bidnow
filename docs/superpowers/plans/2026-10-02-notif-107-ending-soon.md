# NOTIF-107: Auction Ending Soon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every bidder on an auction gets an in-app "\"Vintage Watch\" ends in 15 minutes" (and "in 1 hour") notification at each platform-default threshold before the auction's *current* end time, including after anti-sniping extensions.

**Architecture:**
- **auction-service** owns the end time.
  - Whenever it schedules an auction's closure job, it also schedules one JobRunr job per configured threshold (`auction.ending-soon.thresholds-minutes: [60, 15]`).
  - Each job has a deterministic ID derived from (auction, threshold, end time) and carries the end time it was scheduled for.
  - When the job fires, it reloads the auction:
    - not ACTIVE, or already ended → it does nothing;
    - end time moved (extended) → it reschedules its threshold for the new end time;
    - otherwise → it publishes `AuctionEndingSoonEvent` after commit.
- **media-service** consumes `auction-ending-soon-topic` and sends an in-app `AUCTION_ENDING_SOON` notification to every bidder in its auction projection. The dedup key is per (auction, threshold).

**Tech Stack:** Java 17, Spring Boot 3.2.4, JobRunr (`jobrunr-spring-boot-3-starter`), Spring Kafka, JUnit 5 + Mockito (`MockedStatic<BackgroundJob>`) + AssertJ.

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`, specifically **Story 7**, **Decision 7** (fixed platform thresholds, not seller-configured) and **Decision 8** (recipients are the bidders in `media_auction_participants`). Sources: `docs/epics/notification/notification-service-mvp.md` ("Auction ending soon" is real-time, in-app) and `docs/epics/notification/issue-13.md` §2. Its seller settings, settings table and user settings page are out of scope per Decision 7.

## Global Constraints

- Thresholds are a platform default from config: `auction.ending-soon.thresholds-minutes: [60, 15]`. There is no DB column, no create/edit form field and no per-user setting. An empty list disables the feature platform-wide. Values must be positive and distinct, otherwise startup fails.
- Recipients are the bidders only (`AuctionLookup.participants`), not the seller. In-app only, no email.
- Dedup key: the existing `DedupKeys.endingSoon(auctionId, thresholdMinutes)` = `ENDING_SOON:{auctionId}:{minutes}`. Never change it.
- Kafka publishes happen after commit (`AfterCommit.run`). JobRunr jobs are registered after commit too, so a job never fires for a rolled-back change.
- New and changed event DTOs go in `common/dto/event`. Fields are only added, never renamed or removed. Time fields are `Instant`, like `AuctionExtendedEvent`.
- Topic: `auction-ending-soon-topic`, keyed by `auctionId`. media-service consumes it on the shared `${spring.kafka.consumer.group-id}`.
- No change to the bidding hot path (`AuctionBidService.applyBid`), the auction schema or the frontend.
- **Agents do not commit.** Skip every commit step. The user commits.
- Maven runs from `backend/`: `mvn -q -pl <service> -am test`. The default suites must not need Docker.
- Conventional commit for the story (the user runs it): `feat(notification): notify bidders when an auction is ending soon (NOTIF-107)`.

## Rulings (decisions this plan makes beyond the roadmap)

1. **No hook in `applyBid`.** The roadmap adds a `scheduleAll` call to the anti-snipe extension branch. It isn't needed, and it would add work inside the bid's 1-second, row-locked transaction.
   - An extension only happens inside the snipe window (2 min). By then every threshold job of at least 2 min has already fired, and the new end's thresholds are in the past.
   - A threshold job that fires after an extension sees the moved end time and reschedules itself.
   - Self-rescheduling therefore covers every configuration.
2. **One hook point.** `AuctionClosureService.scheduleClosureJob` calls `AuctionEndingSoonService.scheduleAll(auctionId, closeAt)`. Every path that gives an auction an end to wait for already goes through it:
   - create-as-ACTIVE
   - publish-as-ACTIVE
   - activation (including startup recovery, which calls `activate`)
   - closure deferral

   One call keeps the three call sites unchanged. JobRunr persists scheduled jobs, so a restart needs no extra recovery.
3. **The expected end time travels as epoch milliseconds.** The job argument is a `long`, and the comparison is done in milliseconds. Postgres keeps microseconds, so comparing `Instant`s for equality would see every job as "extended" and loop.
4. **Late or early firing.**
   - JobRunr may run a scheduled job up to one poll interval (15 s) early. That is harmless here, because the check compares end times, not clocks.
   - A job running after the auction's end (for example after downtime) publishes nothing.
   - The alert says the configured threshold ("ends in 15 minutes"). It does not recompute the remaining time.
5. **No `ENDING_SOON` STOMP broadcast.** The roadmap marks it optional. The auction page's countdown already knows the end time (YAGNI).
6. **The threshold label is humanised from minutes.** A multiple of 60 becomes hours ("1 hour", "2 hours"); anything else becomes "1 minute" / "N minutes".

## File Structure

| File | Responsibility |
|---|---|
| `backend/common/src/main/java/com/bidnow/common/dto/event/AuctionEndingSoonEvent.java` (create) | Event contract |
| `backend/auction-service/src/main/java/com/bidnow/auction/config/EndingSoonProperties.java` (create), `AuctionPropertiesConfig.java` (modify), `src/main/resources/application.yml` (modify) | Thresholds config + validation |
| `.../auction/service/AuctionEndingSoonService.java` (create) | `scheduleAll` (JobRunr jobs after commit) + `fire` (re-check, reschedule or publish) |
| `.../auction/job/AuctionEndingSoonJob.java` (create) | JobRunr entry point that delegates to `fire` |
| `.../auction/kafka/AuctionKafkaProducer.java` (modify) | `publishEndingSoon` |
| `.../auction/service/AuctionClosureService.java` (modify) | Call `scheduleAll` from `scheduleClosureJob` |
| `backend/media-service/.../kafka/NotificationKafkaConsumer.java` (modify) | `auction-ending-soon-topic` listener |
| `backend/media-service/.../notification/handler/AuctionNotificationHandler.java` (modify) | `endingSoon` → bidders, in-app |
| `docs/architecture.md`, roadmap (modify) | Contract + progress |

Paths below use `AM = backend/auction-service/src/main/java/com/bidnow/auction`, `AT = backend/auction-service/src/test/java/com/bidnow/auction`, `MM = backend/media-service/src/main/java/com/bidnow/media` and `MT = backend/media-service/src/test/java/com/bidnow/media`.

---

### Task 1: Event contract and thresholds config

**Files:**
- Create: `backend/common/src/main/java/com/bidnow/common/dto/event/AuctionEndingSoonEvent.java`, `AM/config/EndingSoonProperties.java`
- Modify: `AM/config/AuctionPropertiesConfig.java`, `backend/auction-service/src/main/resources/application.yml`
- Test: `AT/config/EndingSoonPropertiesTest.java` (create)

**Interfaces:**
- Produces:
  - `AuctionEndingSoonEvent` (Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`), with fields `UUID auctionId, String auctionTitle, UUID sellerId, Instant endTime, Integer thresholdMinutes`
  - `record EndingSoonProperties(List<Integer> thresholdsMinutes)`, bound from `auction.ending-soon`. Absent → `[60, 15]`; empty → disabled; null, non-positive or duplicate values throw `IllegalArgumentException`.

- [ ] **Step 1: Write the failing test**

Create `AT/config/EndingSoonPropertiesTest.java`:

```java
package com.bidnow.auction.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndingSoonPropertiesTest {

    private static EndingSoonProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("auction.ending-soon", EndingSoonProperties.class);
    }

    @Test
    void absent_defaultsToSixtyAndFifteenMinutes() {
        assertThat(bind(Map.of()).thresholdsMinutes()).containsExactly(60, 15);
    }

    @Test
    void configuredList_isBound() {
        assertThat(bind(Map.of("auction.ending-soon.thresholds-minutes", "30,5")).thresholdsMinutes())
                .containsExactly(30, 5);
    }

    @Test
    void emptyList_disablesTheFeature() {
        assertThat(new EndingSoonProperties(List.of()).thresholdsMinutes()).isEmpty();
    }

    @Test
    void nonPositiveOrNullOrDuplicateValues_failFast() {
        assertThatThrownBy(() -> new EndingSoonProperties(List.of(15, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> new EndingSoonProperties(Arrays.asList(15, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> new EndingSoonProperties(List.of(15, 15)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `backend/`): `mvn -q -pl auction-service -am test -Dtest=EndingSoonPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure (`EndingSoonProperties` is not defined).

- [ ] **Step 3: Implement**

Create `backend/common/src/main/java/com/bidnow/common/dto/event/AuctionEndingSoonEvent.java`:

```java
package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** An ACTIVE auction reached a platform-default "ending soon" threshold; published by auction-service after commit. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionEndingSoonEvent {
    private UUID auctionId;
    private String auctionTitle;
    private UUID sellerId;
    private Instant endTime;
    private Integer thresholdMinutes;
}
```

Create `AM/config/EndingSoonProperties.java`:

```java
package com.bidnow.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;

/**
 * "Ending soon" alerts: minutes before an auction's end time at which bidders are notified. A platform default
 * (roadmap Decision 7), not set per auction. An empty list disables the alerts; invalid values fail startup.
 */
@ConfigurationProperties("auction.ending-soon")
public record EndingSoonProperties(List<Integer> thresholdsMinutes) {

    public static final List<Integer> DEFAULT_THRESHOLDS_MINUTES = List.of(60, 15);

    public EndingSoonProperties {
        if (thresholdsMinutes == null) {
            thresholdsMinutes = DEFAULT_THRESHOLDS_MINUTES;
        }
        if (thresholdsMinutes.stream().anyMatch(m -> m == null || m <= 0)) {
            throw new IllegalArgumentException(
                    "auction.ending-soon.thresholds-minutes must be positive minutes: " + thresholdsMinutes);
        }
        if (new HashSet<>(thresholdsMinutes).size() != thresholdsMinutes.size()) {
            throw new IllegalArgumentException(
                    "auction.ending-soon.thresholds-minutes must be distinct: " + thresholdsMinutes);
        }
        thresholdsMinutes = List.copyOf(thresholdsMinutes);
    }
}
```

In `AM/config/AuctionPropertiesConfig.java`, register the new properties class, and update the Javadoc:

```java
/** Binds the {@code auction.*} properties: anti-sniping, closure and ending-soon alerts. */
@Configuration
@EnableConfigurationProperties({AntiSnipeProperties.class, ClosureProperties.class, EndingSoonProperties.class})
public class AuctionPropertiesConfig {
}
```

In `backend/auction-service/src/main/resources/application.yml`, add under `auction:` after the `closure:` block (same indentation as `closure:`):

```yaml
  ending-soon:
    # Minutes before the end time at which bidders get an in-app "ending soon" alert (NOTIF-107). [] disables.
    thresholds-minutes: [60, 15]
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl auction-service -am test -Dtest=EndingSoonPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 2: Scheduling and firing the ending-soon jobs (auction-service)

**Files:**
- Create: `AM/service/AuctionEndingSoonService.java`, `AM/job/AuctionEndingSoonJob.java`
- Modify: `AM/kafka/AuctionKafkaProducer.java`
- Test: `AT/service/AuctionEndingSoonServiceTest.java` (create), `AT/kafka/AuctionKafkaProducerTest.java` (modify)

**Interfaces:**
- Consumes:
  - `EndingSoonProperties.thresholdsMinutes()` and `AuctionEndingSoonEvent` (Task 1)
  - `AuctionItemRepository.findByIdAndDeletedAtIsNull(UUID): Optional<AuctionItem>` (existing)
  - the `AuctionItem` getters `getStatus()`, `getEndTime(): OffsetDateTime`, `getTitle()`, `getSellerId()` (existing)
  - `AfterCommit.run(Runnable)` (existing; throws `IllegalStateException` without an active transaction synchronization)
  - the `Clock` bean (existing `config/ClockConfig`)
- Produces:
  - `AuctionEndingSoonService(AuctionItemRepository, AuctionKafkaProducer, EndingSoonProperties, Clock)`
  - `void scheduleAll(UUID auctionId, Instant endTime)`: must be called inside a transaction; jobs are registered after commit
  - `@Transactional void fire(UUID auctionId, int thresholdMinutes, long expectedEndEpochMilli)`
  - `static UUID jobId(UUID auctionId, int thresholdMinutes, Instant endTime)`
  - `AuctionEndingSoonJob.notifyEndingSoon(UUID auctionId, int thresholdMinutes, long expectedEndEpochMilli)`
  - `AuctionKafkaProducer.publishEndingSoon(AuctionEndingSoonEvent)`, which sends to `auction-ending-soon-topic`

- [ ] **Step 1: Write the failing tests**

Create `AT/service/AuctionEndingSoonServiceTest.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.config.EndingSoonProperties;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.job.AuctionEndingSoonJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.common.dto.event.AuctionEndingSoonEvent;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.scheduling.BackgroundJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionEndingSoonServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionKafkaProducer kafkaProducer;

    private AuctionEndingSoonService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        service = new AuctionEndingSoonService(auctionItemRepository, kafkaProducer,
                new EndingSoonProperties(List.of(60, 15)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static AuctionItem auction(AuctionStatus status, Instant endTime) {
        return AuctionItem.builder().id(AUCTION).title("Vintage Watch").sellerId(SELLER).status(status)
                .endTime(OffsetDateTime.ofInstant(endTime, ZoneOffset.UTC)).build();
    }

    /** Runs the after-commit hooks with JobRunr's static API mocked; returns it for verification. */
    private static void assertScheduledExactly(Instant endTime, int... thresholds) {
        try (MockedStatic<BackgroundJob> backgroundJob = Mockito.mockStatic(BackgroundJob.class)) {
            triggerAfterCommit();
            for (int minutes : thresholds) {
                backgroundJob.verify(() -> BackgroundJob.<AuctionEndingSoonJob>schedule(
                        eq(AuctionEndingSoonService.jobId(AUCTION, minutes, endTime)),
                        eq(endTime.minus(Duration.ofMinutes(minutes))),
                        any(IocJobLambda.class)));
            }
            backgroundJob.verifyNoMoreInteractions();
        }
    }

    // ── scheduleAll ───────────────────────────────────────────────────────────

    @Test
    void scheduleAll_schedulesEveryFutureThresholdAfterCommit() {
        Instant end = NOW.plus(Duration.ofHours(2));

        service.scheduleAll(AUCTION, end);

        assertScheduledExactly(end, 60, 15);
    }

    @Test
    void scheduleAll_skipsThresholdsAlreadyPast() {
        Instant end = NOW.plus(Duration.ofMinutes(50)); // the 60-minute mark was 10 minutes ago

        service.scheduleAll(AUCTION, end);

        assertScheduledExactly(end, 15);
    }

    @Test
    void scheduleAll_withNoThresholds_schedulesNothing() {
        service = new AuctionEndingSoonService(auctionItemRepository, kafkaProducer,
                new EndingSoonProperties(List.of()), Clock.fixed(NOW, ZoneOffset.UTC));

        service.scheduleAll(AUCTION, NOW.plus(Duration.ofHours(2)));

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void scheduleAll_outsideATransaction_fails() {
        TransactionSynchronizationManager.clearSynchronization();
        try {
            assertThatThrownBy(() -> service.scheduleAll(AUCTION, NOW.plus(Duration.ofHours(2))))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @Test
    void jobId_isDeterministicPerAuctionThresholdAndEndTime() {
        Instant end = NOW.plus(Duration.ofHours(2));

        assertThat(AuctionEndingSoonService.jobId(AUCTION, 15, end)).isEqualTo(AuctionEndingSoonService.jobId(AUCTION, 15, end));
        assertThat(AuctionEndingSoonService.jobId(AUCTION, 15, end)).isNotEqualTo(AuctionEndingSoonService.jobId(AUCTION, 60, end));
        assertThat(AuctionEndingSoonService.jobId(AUCTION, 15, end))
                .isNotEqualTo(AuctionEndingSoonService.jobId(AUCTION, 15, end.plusSeconds(300)));
    }

    // ── fire ──────────────────────────────────────────────────────────────────

    @Test
    void fire_activeWithUnchangedEnd_publishesEndingSoonAfterCommit() {
        Instant end = NOW.plus(Duration.ofMinutes(15));
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, end)));

        service.fire(AUCTION, 15, end.toEpochMilli());
        verifyNoInteractions(kafkaProducer); // not before commit
        triggerAfterCommit();

        ArgumentCaptor<AuctionEndingSoonEvent> event = ArgumentCaptor.forClass(AuctionEndingSoonEvent.class);
        verify(kafkaProducer).publishEndingSoon(event.capture());
        assertThat(event.getValue()).isEqualTo(AuctionEndingSoonEvent.builder()
                .auctionId(AUCTION).auctionTitle("Vintage Watch").sellerId(SELLER).endTime(end)
                .thresholdMinutes(15).build());
    }

    @Test
    void fire_endTimeWithSubMillisecondPrecision_stillMatches() {
        Instant end = NOW.plus(Duration.ofMinutes(15)).plusNanos(123_456); // Postgres keeps microseconds
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, end)));

        service.fire(AUCTION, 15, end.toEpochMilli());
        triggerAfterCommit();

        verify(kafkaProducer).publishEndingSoon(any());
    }

    @Test
    void fire_extendedSinceScheduling_reschedulesForTheNewEndInsteadOfPublishing() {
        Instant oldEnd = NOW.plus(Duration.ofMinutes(15));
        Instant newEnd = NOW.plus(Duration.ofMinutes(20));
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, newEnd)));

        service.fire(AUCTION, 15, oldEnd.toEpochMilli());

        assertScheduledExactly(newEnd, 15);
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_extendedButNewThresholdAlreadyPast_doesNothing() {
        Instant oldEnd = NOW.plus(Duration.ofMinutes(15));
        Instant newEnd = NOW.plus(Duration.ofMinutes(10)); // e.g. edited earlier; its 15-min mark has passed
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, newEnd)));

        service.fire(AUCTION, 15, oldEnd.toEpochMilli());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_notActive_isNoOp() {
        Instant end = NOW.plus(Duration.ofMinutes(15));
        for (AuctionStatus status : List.of(AuctionStatus.COMPLETED, AuctionStatus.CANCELLED, AuctionStatus.FAILED)) {
            when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION)).thenReturn(Optional.of(auction(status, end)));

            service.fire(AUCTION, 15, end.toEpochMilli());
        }

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_auctionGone_isNoOp() {
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION)).thenReturn(Optional.empty());

        service.fire(AUCTION, 15, NOW.toEpochMilli());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void fire_afterTheEndTime_isNoOp() {
        Instant end = NOW.minusSeconds(1); // job ran late (e.g. after downtime)
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(AUCTION))
                .thenReturn(Optional.of(auction(AuctionStatus.ACTIVE, end)));

        service.fire(AUCTION, 15, end.toEpochMilli());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(kafkaProducer);
    }
}
```

Note: `AuctionStatus` must contain `COMPLETED`, `CANCELLED` and `FAILED`. Check `AM/domain/enums/AuctionStatus.java`. If a name differs (for example `CANCELED`), use the enum's actual non-ACTIVE terminal values in `fire_notActive_isNoOp` and record the change in the report.

Add to `AT/kafka/AuctionKafkaProducerTest.java` (and add the `AuctionEndingSoonEvent` import):

```java
    @Test
    void publishEndingSoon_sendsToEndingSoonTopicKeyedByAuction() {
        AuctionEndingSoonEvent event = AuctionEndingSoonEvent.builder()
                .auctionId(UUID.randomUUID()).thresholdMinutes(15).build();
        when(kafkaTemplate.send("auction-ending-soon-topic", event.getAuctionId().toString(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishEndingSoon(event);

        verify(kafkaTemplate).send("auction-ending-soon-topic", event.getAuctionId().toString(), event);
    }

    @Test
    void publishEndingSoon_asyncFailureIsLoggedNotThrown() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuctionKafkaProducer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AuctionEndingSoonEvent event = AuctionEndingSoonEvent.builder()
                    .auctionId(UUID.randomUUID()).thresholdMinutes(15).build();
            CompletableFuture<SendResult<String, Object>> failed = CompletableFuture.failedFuture(new KafkaException("down"));
            when(kafkaTemplate.send("auction-ending-soon-topic", event.getAuctionId().toString(), event)).thenReturn(failed);

            assertThatCode(() -> producer.publishEndingSoon(event)).doesNotThrowAnyException();

            assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().startsWith("Failed to publish AuctionEndingSoonEvent")
                    && e.getThrowableProxy() != null);
        } finally {
            logger.detachAppender(appender);
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl auction-service -am test -Dtest='AuctionEndingSoonServiceTest,AuctionKafkaProducerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure. `AuctionEndingSoonService`, `AuctionEndingSoonJob` and `publishEndingSoon` are not defined.

- [ ] **Step 3: Implement**

Add to `AM/kafka/AuctionKafkaProducer.java`:
- the import `com.bidnow.common.dto.event.AuctionEndingSoonEvent`
- the constant `private static final String AUCTION_ENDING_SOON_TOPIC = "auction-ending-soon-topic";` after `AUCTION_EXTENDED_TOPIC`
- this method at the end of the class:

```java
    public void publishEndingSoon(AuctionEndingSoonEvent event) {
        kafkaTemplate.send(AUCTION_ENDING_SOON_TOPIC, event.getAuctionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish AuctionEndingSoonEvent for auction {} ({} min) - bidders get no alert",
                                event.getAuctionId(), event.getThresholdMinutes(), ex);
                    } else {
                        log.info("Published AuctionEndingSoonEvent for auction {} ({} min)",
                                event.getAuctionId(), event.getThresholdMinutes());
                    }
                });
    }
```

Create `AM/job/AuctionEndingSoonJob.java`:

```java
package com.bidnow.auction.job;

import com.bidnow.auction.service.AuctionEndingSoonService;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuctionEndingSoonJob {

    private final AuctionEndingSoonService endingSoonService;

    /**
     * JobRunr entry point for one "ending soon" threshold. {@code expectedEndEpochMilli} is the end time the job was
     * scheduled for; {@link AuctionEndingSoonService#fire} compares it with the auction's current end time.
     */
    @Job(name = "Ending-soon alert for auction %0 (%1 min)", retries = 3)
    public void notifyEndingSoon(UUID auctionId, int thresholdMinutes, long expectedEndEpochMilli) {
        endingSoonService.fire(auctionId, thresholdMinutes, expectedEndEpochMilli);
    }
}
```

Create `AM/service/AuctionEndingSoonService.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.config.EndingSoonProperties;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.job.AuctionEndingSoonJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.util.AfterCommit;
import com.bidnow.common.dto.event.AuctionEndingSoonEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.BackgroundJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * "Ending soon" alerts (roadmap Story 7, Decision 7): one JobRunr job per configured threshold before the auction's
 * end time. A job carries the end time it was scheduled for; when it fires after an anti-sniping extension it
 * reschedules itself for the new end instead of alerting, so extensions need no hook on the bid path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionEndingSoonService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionKafkaProducer kafkaProducer;
    private final EndingSoonProperties properties;
    private final Clock clock;

    /** Deterministic per (auction, threshold, end time): rescheduling for the same end time is a JobRunr no-op. */
    static UUID jobId(UUID auctionId, int thresholdMinutes, Instant endTime) {
        return UUID.nameUUIDFromBytes(("auction-ending-soon:" + auctionId + ":" + thresholdMinutes + ":"
                + endTime.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Schedules every configured threshold still in the future for an auction ending at {@code endTime}. Jobs are
     * registered after the surrounding transaction commits, so it must run inside one.
     *
     * @throws IllegalStateException if no transaction synchronization is active
     */
    public void scheduleAll(UUID auctionId, Instant endTime) {
        for (int minutes : properties.thresholdsMinutes()) {
            scheduleOne(auctionId, minutes, endTime);
        }
    }

    /**
     * Fires one threshold. No-op unless the auction is still ACTIVE and has not ended; if its end time moved since
     * the job was scheduled (anti-sniping), the threshold is rescheduled for the new end instead.
     */
    @Transactional
    public void fire(UUID auctionId, int thresholdMinutes, long expectedEndEpochMilli) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            log.info("Ending-soon alert skipped - auction {} is gone or no longer active", auctionId);
            return;
        }
        Instant endTime = auction.getEndTime().toInstant();
        // Compare in millis: the job argument is millis, Postgres keeps microseconds
        if (endTime.toEpochMilli() != expectedEndEpochMilli) {
            log.info("Auction {} now ends at {} - {}-minute alert rescheduled", auctionId, endTime, thresholdMinutes);
            scheduleOne(auctionId, thresholdMinutes, endTime);
            return;
        }
        if (!clock.instant().isBefore(endTime)) {
            log.info("Ending-soon alert skipped - auction {} already reached its end time {}", auctionId, endTime);
            return;
        }
        AuctionEndingSoonEvent event = AuctionEndingSoonEvent.builder()
                .auctionId(auctionId)
                .auctionTitle(auction.getTitle())
                .sellerId(auction.getSellerId())
                .endTime(endTime)
                .thresholdMinutes(thresholdMinutes)
                .build();
        AfterCommit.run(() -> kafkaProducer.publishEndingSoon(event));
    }

    private void scheduleOne(UUID auctionId, int thresholdMinutes, Instant endTime) {
        Instant fireAt = endTime.minus(Duration.ofMinutes(thresholdMinutes));
        if (!fireAt.isAfter(clock.instant())) {
            log.debug("Auction {} {}-minute alert not scheduled - {} is already past", auctionId, thresholdMinutes, fireAt);
            return;
        }
        UUID jobId = jobId(auctionId, thresholdMinutes, endTime);
        long endMillis = endTime.toEpochMilli();
        AfterCommit.run(() -> {
            BackgroundJob.<AuctionEndingSoonJob>schedule(jobId, fireAt,
                    job -> job.notifyEndingSoon(auctionId, thresholdMinutes, endMillis));
            log.info("Scheduled {}-minute ending-soon job {} for auction {} at {}", thresholdMinutes, jobId, auctionId, fireAt);
        });
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl auction-service -am test -Dtest='AuctionEndingSoonServiceTest,AuctionKafkaProducerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (12 service tests plus the producer tests).

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 3: Schedule the alerts wherever the closure is scheduled

**Files:**
- Modify: `AM/service/AuctionClosureService.java`
- Test: `AT/service/AuctionClosureServiceTest.java` (modify)

**Interfaces:**
- Consumes: `AuctionEndingSoonService.scheduleAll(UUID, Instant)` (Task 2).
- Produces: `AuctionClosureService(AuctionItemRepository, AuctionStatusHistoryRepository, AuctionKafkaProducer, ClosureProperties, Clock, AuctionEndingSoonService)`. The new dependency is appended last via `@RequiredArgsConstructor` field order. `scheduleClosureJob(auctionId, closeAt)` now also calls `endingSoonService.scheduleAll(auctionId, closeAt)`.

- [ ] **Step 1: Write the failing test**

In `AT/service/AuctionClosureServiceTest.java`:
- Add a mock field: `@Mock private AuctionEndingSoonService endingSoonService;`
- In `initTransactionSync()`, change the constructor call to `new AuctionClosureService(auctionItemRepository, auctionStatusHistoryRepository, kafkaProducer, CLOSURE, Clock.fixed(NOW, ZoneOffset.UTC), endingSoonService)`.
- Add:

```java
    @Test
    void scheduleClosureJob_alsoSchedulesEndingSoonAlertsForTheSameEndTime() {
        UUID auctionId = UUID.randomUUID();
        Instant endTime = NOW.plus(Duration.ofHours(2));

        closureService.scheduleClosureJob(auctionId, endTime);

        verify(endingSoonService).scheduleAll(auctionId, endTime);
    }

    @Test
    void scheduleClosureJob_outsideATransaction_failsBeforeSchedulingAnything() {
        TransactionSynchronizationManager.clearSynchronization();
        try {
            UUID auctionId = UUID.randomUUID();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> closureService.scheduleClosureJob(auctionId, NOW.plus(Duration.ofHours(2))))
                    .isInstanceOf(IllegalStateException.class);
            org.mockito.Mockito.verifyNoInteractions(endingSoonService);
        } finally {
            TransactionSynchronizationManager.initSynchronization();
        }
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionClosureServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, because the 6-argument constructor does not exist yet.

- [ ] **Step 3: Implement**

In `AM/service/AuctionClosureService.java`:
- Add the field after `private final Clock clock;`: `private final AuctionEndingSoonService endingSoonService;`
- In `scheduleClosureJob`, after the `TransactionSynchronizationManager.registerSynchronization(...)` block (still inside the method, after the transaction check), add:

```java

        // Ending-soon alerts share the closure's end time: every path that schedules a closure (create, publish,
        // activation, startup recovery, closure deferral) schedules them too (NOTIF-107).
        endingSoonService.scheduleAll(auctionId, closeAt);
```

- Extend the method's Javadoc with one sentence: `Also schedules the auction's "ending soon" alert jobs for the same end time ({@link AuctionEndingSoonService#scheduleAll}).`

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl auction-service -am test -Dtest='AuctionClosureServiceTest,AuctionActivationServiceTest,AuctionServiceImplTest,AuctionStartupRecoveryServiceTest,AuctionClosureJobTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. The other classes mock `AuctionClosureService`, so they are unaffected.

Run: `mvn -q -pl auction-service -am test`
Expected: BUILD SUCCESS. This is the default suite; Cucumber BDD is excluded.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 4: media-service notifies bidders

**Files:**
- Modify: `MM/kafka/NotificationKafkaConsumer.java`, `MM/notification/handler/AuctionNotificationHandler.java`
- Test: `MT/kafka/NotificationKafkaConsumerTest.java`, `MT/notification/handler/AuctionNotificationHandlerTest.java` (modify)

**Interfaces:**
- Consumes:
  - `AuctionEndingSoonEvent` (Task 1)
  - `AuctionLookup.title(UUID, String)` and `AuctionLookup.participants(UUID): List<UUID>`
  - `DedupKeys.endingSoon(UUID, int)`
  - `NotificationType.AUCTION_ENDING_SOON`
  - `NotificationLinks.auctionPath(UUID)`
  - `NotificationDispatcher.dispatchAll(List<NotificationIntent>)` (all existing)
- Produces: `AuctionNotificationHandler.endingSoon(AuctionEndingSoonEvent)`, and `NotificationKafkaConsumer.consumeAuctionEndingSoon(AuctionEndingSoonEvent)` on `auction-ending-soon-topic` (shared group).

- [ ] **Step 1: Write the failing tests**

In `MT/notification/handler/AuctionNotificationHandlerTest.java`, add the import `com.bidnow.common.dto.event.AuctionEndingSoonEvent`, then these tests. `LOSER1`/`LOSER2` stand in for bidders.

```java
    @Test
    void endingSoon_notifiesEveryBidderInAppButNotTheSeller() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1, LOSER2));

        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).endTime(Instant.parse("2026-10-02T12:15:00Z")).thresholdMinutes(15).build());

        List<NotificationIntent> intents = dispatchedBatch();
        assertThat(intents).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2);
        NotificationIntent intent = intents.get(0);
        assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_ENDING_SOON);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.endingSoon(AUCTION, 15));
        assertThat(intent.title()).isEqualTo("Auction ending soon");
        assertThat(intent.message()).isEqualTo("\"Vintage Watch\" ends in 15 minutes.");
        assertThat(intent.actionUrl()).isEqualTo("/auctions/" + AUCTION);
        assertThat(intent.email()).isNull();
    }

    @Test
    void endingSoon_humanisesWholeHours() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1));

        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).thresholdMinutes(60).build());

        assertThat(dispatchedBatch().get(0).message()).isEqualTo("\"Vintage Watch\" ends in 1 hour.");
    }

    @Test
    void thresholdLabel_coversSingularAndPlural() {
        assertThat(AuctionNotificationHandler.thresholdLabel(1)).isEqualTo("1 minute");
        assertThat(AuctionNotificationHandler.thresholdLabel(15)).isEqualTo("15 minutes");
        assertThat(AuctionNotificationHandler.thresholdLabel(60)).isEqualTo("1 hour");
        assertThat(AuctionNotificationHandler.thresholdLabel(120)).isEqualTo("2 hours");
        assertThat(AuctionNotificationHandler.thresholdLabel(90)).isEqualTo("90 minutes");
    }

    @Test
    void endingSoon_withoutBidders_dispatchesNothing() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of());

        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).thresholdMinutes(15).build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void endingSoon_withoutThreshold_isSkipped() {
        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).build());

        verifyNoInteractions(dispatcher, auctions);
    }
```

In `MT/kafka/NotificationKafkaConsumerTest.java`:
- Add the import `com.bidnow.common.dto.event.AuctionEndingSoonEvent`.
- In `delegatesEachTopicToItsHandler`, add `AuctionEndingSoonEvent endingSoon = AuctionEndingSoonEvent.builder().auctionId(id).build();`, then `consumer.consumeAuctionEndingSoon(endingSoon);` and `verify(auctionHandler).endingSoon(endingSoon);`.
- In `everyListenerUsesTheSharedNotificationGroup`, change `hasSize(9)` to `hasSize(10)` and add `assertThat(listeners.get("consumeAuctionEndingSoon").topics()).containsExactly("auction-ending-soon-topic");`.

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl media-service -am test -Dtest='AuctionNotificationHandlerTest,NotificationKafkaConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure. `endingSoon`, `thresholdLabel` and `consumeAuctionEndingSoon` are not defined.

- [ ] **Step 3: Implement**

In `MM/notification/handler/AuctionNotificationHandler.java`, add the import `com.bidnow.common.dto.event.AuctionEndingSoonEvent` and these methods before `dispatchIfAny`:

```java
    /** Ending-soon (platform-default thresholds, Decision 7): every bidder, in-app only; one per auction per threshold. */
    public void endingSoon(AuctionEndingSoonEvent event) {
        UUID auctionId = event.getAuctionId();
        Integer minutes = event.getThresholdMinutes();
        if (minutes == null || minutes <= 0) {
            log.warn("AuctionEndingSoonEvent for auction {} has no threshold - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        String message = "\"" + title + "\" ends in " + thresholdLabel(minutes) + ".";
        String dedupKey = DedupKeys.endingSoon(auctionId, minutes);
        List<NotificationIntent> intents = new ArrayList<>();
        for (UUID bidder : auctions.participants(auctionId)) {
            intents.add(new NotificationIntent(bidder, NotificationType.AUCTION_ENDING_SOON, dedupKey, auctionId,
                    "Auction ending soon", message, NotificationLinks.auctionPath(auctionId), null, null));
        }
        dispatchIfAny(intents);
    }

    /** "1 minute", "15 minutes", "1 hour", "2 hours" (whole hours only; 90 → "90 minutes"). */
    static String thresholdLabel(int minutes) {
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
```

In `MM/kafka/NotificationKafkaConsumer.java`, add the import `com.bidnow.common.dto.event.AuctionEndingSoonEvent` and this listener after `consumeAuctionExtended`:

```java
    @KafkaListener(topics = "auction-ending-soon-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionEndingSoon(AuctionEndingSoonEvent event) {
        log.info("Received AuctionEndingSoonEvent for auction: {} ({} min)", event.getAuctionId(), event.getThresholdMinutes());
        auctionHandler.endingSoon(event);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test -Dtest='AuctionNotificationHandlerTest,NotificationKafkaConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

Run: `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 5: Docs, verification and roadmap

**Files:**
- Modify: `docs/architecture.md`, `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`

- [ ] **Step 1: Update the event contract**

In `docs/architecture.md`, find the topic-table row for `auction-extended-topic` (published by auction-service, around line 98). Add this row directly after it:

```
| `auction-ending-soon-topic` | published by auction-service (after commit, key = auctionId) | `AuctionEndingSoonEvent {auctionId, auctionTitle, sellerId, endTime, thresholdMinutes}` — one per configured threshold (`auction.ending-soon.thresholds-minutes`, default 60 and 15) before the current end time; JobRunr jobs reschedule themselves after anti-sniping extensions. media-service turns it into an in-app `AUCTION_ENDING_SOON` for every bidder. |
```

- [ ] **Step 2: Run the full verification**

Run (from `backend/`): `mvn -q -pl common,auction-service,media-service -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Update the roadmap**

In Story 7 of `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`:
- Tick tasks 7.1–7.3 and 7.5.
- Mark task 7.4 `(dropped — see Refinements)`.
- Mark task 7.6 `(manual smoke handed to the user)`.
- Add:

```
**Refinements (implemented):** one hook point — `AuctionClosureService.scheduleClosureJob` calls `AuctionEndingSoonService.scheduleAll`, so create/publish/activation/startup recovery/closure deferral all schedule the alerts; no `applyBid` hook (7.4 dropped): an extension only happens inside the 2-minute snipe window, after every threshold job ≥ 2 min has fired, and a job that fires after an extension reschedules its threshold for the new end. The job's expected end time is epoch millis and compared in millis (Postgres keeps microseconds). Jobs that run after the end time publish nothing. `AuctionEndingSoonEvent.endTime` is an `Instant` (like `AuctionExtendedEvent`). No `ENDING_SOON` STOMP broadcast (optional, YAGNI).
```

- [ ] **Step 4: Manual smoke (handed to the user; a merge gate)**

With `docker compose up`:
1. Create an auction ending in about 20 minutes, and bid on it as another user.
2. About 5 minutes later, the bidder gets one "\"…\" ends in 15 minutes." in-app notification. The seller does not get one, and no "1 hour" alert is sent because it was already past at creation.
3. The JobRunr dashboard (if enabled) or the `jobrunr_jobs` table shows one succeeded `Ending-soon alert for auction … (15 min)` job.

For a faster check, set `auction.ending-soon.thresholds-minutes: [2]` locally and create an auction ending in 4 minutes.

- [ ] **Step 5: Commit**: skipped. The user commits `feat(notification): notify bidders when an auction is ending soon (NOTIF-107)`.
