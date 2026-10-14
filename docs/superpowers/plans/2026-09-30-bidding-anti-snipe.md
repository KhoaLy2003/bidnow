# Story 4 — BID-104: Anti-Sniping Auto-Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a bid lands in the final `window` (120 s) before an auction ends, extend the end time by `extension` (300 s) in the same row-locked transaction as the bid. Record the extension and publish `AuctionExtendedEvent` after commit. Make the closure job reschedule itself instead of closing an extended auction early. Let bidding-service evict its cached context on extension.

**Architecture:**
- **Extension in apply-bid.** `AuctionBidService.applyBid` in auction-service already holds `SELLER … FOR UPDATE` on the auction row (Story 1). After it applies the bid, a new `extendIfInSnipeWindow` step compares `endTime - now` against `AntiSnipeProperties.window`. When the remaining time is strictly less than the window, it:
  - moves `end_time` forward by `extension`;
  - increments `extension_count`;
  - saves an `AuctionExtension` row, using the existing entity and table;
  - registers the event publish through `AfterCommit`.
- **Response.** `ApplyBidResponse.extended` becomes real. bidding-service already persists the bid flag, publishes it in `BidPlacedEvent` and writes the new `endTime` into its cache (Story 3).
- **Closure reschedule.** `AuctionClosureService.close` re-checks `now < endTime` under the row lock. If the auction was extended, it schedules a new closure job at the new end time and returns. JobRunr ignores a second job with an existing ID, so the job ID becomes deterministic per `(auctionId, closeAt)` rather than per auction.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data JPA, JobRunr, Spring Kafka, Lombok, JUnit 5, Mockito, AssertJ, Cucumber (auction-service BDD).

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (§2 decision, §4 anti-snipe config + `AuctionExtendedEvent`, §7 closure row). Roadmap: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (Story 4). Builds on Story 1 (`AuctionBidService`, `findByIdForUpdate`), Story 3 (`AfterCommit`, bidding `AuctionLifecycleConsumer`).

## Global Constraints

- **Scope:**
  - auction-service
  - common: add a new `AuctionExtendedEvent` class only
  - bidding-service: `AuctionLifecycleConsumer` and its test only
  - docs
- **Config:** `auction.anti-snipe.window-seconds: 120` and `auction.anti-snipe.extension-seconds: 300` are bound by `AntiSnipeProperties`. Never hardcode them.
- **Extension rule:** extend when `Duration.between(now, endTime) < window`, which is strictly less than. The new end time is `newEnd = endTime + extension`. Every qualifying bid extends again.
- **Where extension happens:** only inside `applyBid`, under the existing row lock, after the bid passes validation and has been applied. A replayed `bidId` never extends and returns `extended = false`.
- **`AuctionExtension` row:**
  - `auction` is the auction
  - `previousEndTime` and `newEndTime`
  - `extensionDurationSeconds` equals `extension-seconds`
  - `triggeredByBidId` equals the bid's `bidId`
  - `triggeredByUserId` equals the bidder
- **`AuctionExtendedEvent`:**
  - Topic `auction-extended-topic`, keyed by `auctionId`.
  - Fields: `auctionId`, `auctionTitle`, `previousEndTime` (Instant), `newEndTime` (Instant), `extensionCount` (Integer), `triggeredByBidId`, `triggeredByUserId`.
  - Published only after commit, via `AfterCommit.run`.
  - A send failure is logged at ERROR with a `CRITICAL:` prefix.
- **Closure:**
  - `close()` compares against `OffsetDateTime.now(clock)`, using an injected `Clock`.
  - If `now < endTime`, it calls `scheduleClosureJob(auctionId, endTime)` and returns with no status change.
  - `closureJobId(UUID auctionId, Instant closeAt)` is `UUID.nameUUIDFromBytes("auction-closure:" + auctionId + ":" + closeAt.toEpochMilli())`.
- **Do not run `git commit` or `git add`.** Leave everything uncommitted on the working branch.
- **Maven** runs from `backend/`:
  - Unit tests: `mvn -q -pl <module> -am test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`
  - Full suite: `mvn -q -pl <module> -am test`
  - BDD needs `-Pbdd` and Docker. Run it only if the controller says so.

---

## File Map

Paths are relative to `backend/`.

| File | Action | Responsibility |
|---|---|---|
| `common/src/main/java/com/bidnow/common/dto/event/AuctionExtendedEvent.java` | Create | Event DTO |
| `auction-service/src/main/java/com/bidnow/auction/kafka/AuctionKafkaProducer.java` | Modify | `publishAuctionExtended` |
| `auction-service/src/test/java/com/bidnow/auction/kafka/AuctionKafkaProducerTest.java` | Create | Producer test |
| `auction-service/src/main/java/com/bidnow/auction/config/AntiSnipeProperties.java` | Create | `@ConfigurationProperties` record |
| `auction-service/src/main/java/com/bidnow/auction/config/AntiSnipeConfig.java` | Create | Enables the properties |
| `auction-service/src/main/resources/application.yml` | Modify | `auction.anti-snipe.*` |
| `auction-service/src/main/java/com/bidnow/auction/repository/AuctionExtensionRepository.java` | Create | Spring Data repo |
| `auction-service/src/main/java/com/bidnow/auction/service/AuctionBidService.java` | Modify | Extension step |
| `auction-service/src/test/java/com/bidnow/auction/service/AuctionBidServiceTest.java` | Modify | Extension tests |
| `auction-service/src/main/java/com/bidnow/auction/service/AuctionClosureService.java` | Modify | `Clock`, end-time re-check, job ID |
| `auction-service/src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java` | Modify | Clock + reschedule tests |
| `bidding-service/src/main/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumer.java` | Modify | Listen to `auction-extended-topic` |
| `bidding-service/src/test/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumerTest.java` | Modify | Extended → evict |
| `auction-service/src/test/resources/db/changelog/test-data.sql` | Modify | Seed auction for anti-snipe BDD |
| `auction-service/src/test/resources/features/internal-bid-api.feature` | Modify | Anti-snipe + deferred-closure scenarios |
| `auction-service/src/test/java/com/bidnow/auction/bdd/steps/InternalBidSteps.java` | Modify | New steps; close-race uses force-close |
| repo root: `docs/architecture.md`, spec, roadmap | Modify | Docs |

---

### Task 1: `AuctionExtendedEvent` + producer method

**Files:**
- Create: `common/src/main/java/com/bidnow/common/dto/event/AuctionExtendedEvent.java`
- Modify: `auction-service/src/main/java/com/bidnow/auction/kafka/AuctionKafkaProducer.java`
- Test: `auction-service/src/test/java/com/bidnow/auction/kafka/AuctionKafkaProducerTest.java`

**Interfaces:**
- Produces `AuctionExtendedEvent` (`@Data @Builder @NoArgsConstructor @AllArgsConstructor`) with fields `UUID auctionId`, `String auctionTitle`, `Instant previousEndTime`, `Instant newEndTime`, `Integer extensionCount`, `UUID triggeredByBidId` and `UUID triggeredByUserId`.
- Produces `AuctionKafkaProducer.publishAuctionExtended(AuctionExtendedEvent)`. It sends to `auction-extended-topic` with the key `auctionId`, and logs a failure as `CRITICAL:` without throwing.

- [ ] **Step 1: Write the failing test**

`auction-service/src/test/java/com/bidnow/auction/kafka/AuctionKafkaProducerTest.java`:

```java
package com.bidnow.auction.kafka;

import com.bidnow.common.dto.event.AuctionExtendedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AuctionKafkaProducer producer;

    @Test
    void publishAuctionExtended_sendsToExtendedTopicKeyedByAuction() {
        AuctionExtendedEvent event = AuctionExtendedEvent.builder().auctionId(UUID.randomUUID()).build();
        when(kafkaTemplate.send("auction-extended-topic", event.getAuctionId().toString(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishAuctionExtended(event);

        verify(kafkaTemplate).send("auction-extended-topic", event.getAuctionId().toString(), event);
    }

    @Test
    void publishAuctionExtended_asyncFailureIsLoggedNotThrown() {
        AuctionExtendedEvent event = AuctionExtendedEvent.builder().auctionId(UUID.randomUUID()).build();
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture.failedFuture(new KafkaException("down"));
        when(kafkaTemplate.send("auction-extended-topic", event.getAuctionId().toString(), event)).thenReturn(failed);

        assertThatCode(() -> producer.publishAuctionExtended(event)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionKafkaProducerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`common/src/main/java/com/bidnow/common/dto/event/AuctionExtendedEvent.java`:

```java
package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Anti-sniping extension of an auction's end time, published by auction-service after commit. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionExtendedEvent {
    private UUID auctionId;
    private String auctionTitle;
    private Instant previousEndTime;
    private Instant newEndTime;
    private Integer extensionCount;
    private UUID triggeredByBidId;
    private UUID triggeredByUserId;
}
```

In `AuctionKafkaProducer`, add `import com.bidnow.common.dto.event.AuctionExtendedEvent;`, the constant `private static final String AUCTION_EXTENDED_TOPIC = "auction-extended-topic";`, and this method:

```java
    public void publishAuctionExtended(AuctionExtendedEvent event) {
        kafkaTemplate.send(AUCTION_EXTENDED_TOPIC, event.getAuctionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("CRITICAL: Failed to publish AuctionExtendedEvent for auction {} (new end {})",
                                event.getAuctionId(), event.getNewEndTime(), ex);
                    } else {
                        log.info("Published AuctionExtendedEvent for auction {} (new end {})",
                                event.getAuctionId(), event.getNewEndTime());
                    }
                });
    }
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionKafkaProducerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests).

---

### Task 2: Anti-snipe config + extension inside `applyBid`

**Files:**
- Create: `auction-service/src/main/java/com/bidnow/auction/config/AntiSnipeProperties.java`
- Create: `auction-service/src/main/java/com/bidnow/auction/config/AntiSnipeConfig.java`
- Modify: `auction-service/src/main/resources/application.yml`
- Create: `auction-service/src/main/java/com/bidnow/auction/repository/AuctionExtensionRepository.java`
- Modify: `auction-service/src/main/java/com/bidnow/auction/service/AuctionBidService.java`
- Modify: `auction-service/src/test/java/com/bidnow/auction/service/AuctionBidServiceTest.java`

**Interfaces:**
- Consumes these:
  - Task 1: `AuctionKafkaProducer.publishAuctionExtended`, `AuctionExtendedEvent`.
  - Story 3: `AfterCommit.run`.
  - Existing: the `AuctionExtension` entity (`auction`, `previousEndTime`, `newEndTime`, `extensionDurationSeconds`, `triggeredByBidId`, `triggeredByUserId`, `createdAt` default now).
- Produces these:
  - `record AntiSnipeProperties(long windowSeconds, long extensionSeconds)` with `Duration window()` and `Duration extension()`.
  - `AuctionExtensionRepository extends JpaRepository<AuctionExtension, UUID>`.
  - The `AuctionBidService` constructor becomes `(AuctionItemRepository, AuctionExtensionRepository, AuctionKafkaProducer, AntiSnipeProperties, Clock)` via `@RequiredArgsConstructor`, in field order.
  - `ApplyBidResponse.extended` and `extensionCount` now reflect the real state.

- [ ] **Step 1: Update the test class to the new constructor and add extension tests**

In `AuctionBidServiceTest`:
- Add the imports `com.bidnow.auction.config.AntiSnipeProperties`, `com.bidnow.auction.domain.entity.AuctionExtension`, `com.bidnow.auction.kafka.AuctionKafkaProducer`, `com.bidnow.auction.repository.AuctionExtensionRepository`, `com.bidnow.common.dto.event.AuctionExtendedEvent`, `org.junit.jupiter.api.AfterEach`, `org.mockito.ArgumentCaptor`, `org.springframework.transaction.support.TransactionSynchronization` and `org.springframework.transaction.support.TransactionSynchronizationManager`.
- Add two mocks and replace `setUp`:

```java
    @Mock
    private AuctionExtensionRepository auctionExtensionRepository;
    @Mock
    private AuctionKafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        service = new AuctionBidService(auctionItemRepository, auctionExtensionRepository, kafkaProducer,
                new AntiSnipeProperties(120, 300), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private AuctionItem endingIn(long secondsLeft) {
        AuctionItem auction = activeAuction(1, "100.00");
        auction.setEndTime(OffsetDateTime.ofInstant(NOW.plusSeconds(secondsLeft), ZoneOffset.UTC));
        return auction;
    }
```

The existing `@BeforeEach void setUp()` must be replaced, not duplicated. Existing tests keep working, because their fixture ends 3600 s after `NOW`, which is outside the window.

Add these tests:

```java
    @Test
    void bidInsideSnipeWindow_extendsEndTimeAndRecordsExtension() {
        AuctionItem auction = endingIn(119);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        ApplyBidRequest request = bid("105.00");

        ApplyBidResponse response = service.applyBid(auction.getId(), request);

        OffsetDateTime expectedEnd = previousEnd.plusSeconds(300);
        assertThat(auction.getEndTime()).isEqualTo(expectedEnd);
        assertThat(auction.getExtensionCount()).isEqualTo(1);
        assertThat(response.isExtended()).isTrue();
        assertThat(response.getEndTime()).isEqualTo(expectedEnd);
        assertThat(response.getExtensionCount()).isEqualTo(1);

        ArgumentCaptor<AuctionExtension> extension = ArgumentCaptor.forClass(AuctionExtension.class);
        verify(auctionExtensionRepository).save(extension.capture());
        assertThat(extension.getValue().getAuction()).isSameAs(auction);
        assertThat(extension.getValue().getPreviousEndTime()).isEqualTo(previousEnd);
        assertThat(extension.getValue().getNewEndTime()).isEqualTo(expectedEnd);
        assertThat(extension.getValue().getExtensionDurationSeconds()).isEqualTo(300);
        assertThat(extension.getValue().getTriggeredByBidId()).isEqualTo(request.getBidId());
        assertThat(extension.getValue().getTriggeredByUserId()).isEqualTo(BIDDER_ID);
    }

    @Test
    void extensionEvent_isPublishedOnlyAfterCommit() {
        AuctionItem auction = endingIn(30);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        ApplyBidRequest request = bid("105.00");

        service.applyBid(auction.getId(), request);

        verify(kafkaProducer, never()).publishAuctionExtended(any());
        triggerAfterCommit();
        ArgumentCaptor<AuctionExtendedEvent> event = ArgumentCaptor.forClass(AuctionExtendedEvent.class);
        verify(kafkaProducer).publishAuctionExtended(event.capture());
        assertThat(event.getValue().getAuctionId()).isEqualTo(auction.getId());
        assertThat(event.getValue().getAuctionTitle()).isEqualTo("Vintage Watch");
        assertThat(event.getValue().getPreviousEndTime()).isEqualTo(previousEnd.toInstant());
        assertThat(event.getValue().getNewEndTime()).isEqualTo(previousEnd.plusSeconds(300).toInstant());
        assertThat(event.getValue().getExtensionCount()).isEqualTo(1);
        assertThat(event.getValue().getTriggeredByBidId()).isEqualTo(request.getBidId());
        assertThat(event.getValue().getTriggeredByUserId()).isEqualTo(BIDDER_ID);
    }

    @Test
    void bidExactlyAtWindowBoundary_doesNotExtend() {
        AuctionItem auction = endingIn(120);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(response.isExtended()).isFalse();
        assertThat(auction.getEndTime()).isEqualTo(previousEnd);
        assertThat(auction.getExtensionCount()).isZero();
        verify(auctionExtensionRepository, never()).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void bidOutsideSnipeWindow_doesNotExtend() {
        AuctionItem auction = endingIn(121);
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(response.isExtended()).isFalse();
        verify(auctionExtensionRepository, never()).save(any());
    }

    @Test
    void repeatedInWindowBids_extendAgain() {
        AuctionItem auction = endingIn(10);
        auction.setExtensionCount(1);
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(response.isExtended()).isTrue();
        assertThat(auction.getExtensionCount()).isEqualTo(2);
    }

    @Test
    void rejectedBidInsideWindow_doesNotExtend() {
        AuctionItem auction = endingIn(10);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("101.00")))
                .isInstanceOf(BidTooLowException.class);

        assertThat(auction.getEndTime()).isEqualTo(previousEnd);
        verify(auctionExtensionRepository, never()).save(any());
    }

    @Test
    void replayInsideWindow_doesNotExtendAgain() {
        AuctionItem auction = endingIn(10);
        UUID bidId = UUID.randomUUID();
        auction.setLastBidId(bidId);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(),
                new ApplyBidRequest(bidId, BIDDER_ID, new BigDecimal("105.00")));

        assertThat(response.isExtended()).isFalse();
        assertThat(auction.getEndTime()).isEqualTo(previousEnd);
        verify(auctionExtensionRepository, never()).save(any());
    }
```

`activeAuction(1, "100.00")` has minimum 105, so `bid("101.00")` is rejected as too low.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionBidServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (the new constructor, `AntiSnipeProperties` and `AuctionExtensionRepository` don't exist yet).

- [ ] **Step 3: Config and repository**

`auction-service/src/main/java/com/bidnow/auction/config/AntiSnipeProperties.java`:

```java
package com.bidnow.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Anti-sniping: a bid with less than {@code window} left extends the auction by {@code extension}. */
@ConfigurationProperties("auction.anti-snipe")
public record AntiSnipeProperties(long windowSeconds, long extensionSeconds) {

    public Duration window() {
        return Duration.ofSeconds(windowSeconds);
    }

    public Duration extension() {
        return Duration.ofSeconds(extensionSeconds);
    }
}
```

`auction-service/src/main/java/com/bidnow/auction/config/AntiSnipeConfig.java`:

```java
package com.bidnow.auction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AntiSnipeProperties.class)
public class AntiSnipeConfig {
}
```

In `auction-service/src/main/resources/application.yml`, add a new top-level block. First check that no `auction:` key exists yet; if one does, merge into it.

```yaml
auction:
  anti-snipe:
    window-seconds: 120
    extension-seconds: 300
```

`auction-service/src/main/java/com/bidnow/auction/repository/AuctionExtensionRepository.java`:

```java
package com.bidnow.auction.repository;

import com.bidnow.auction.domain.entity.AuctionExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionExtensionRepository extends JpaRepository<AuctionExtension, UUID> {
}
```

- [ ] **Step 4: Extension step in `AuctionBidService`**

Make these changes to `AuctionBidService`:

1. Add these fields, in this order after `auctionItemRepository` and before `clock`:

   ```java
       private final AuctionExtensionRepository auctionExtensionRepository;
       private final AuctionKafkaProducer kafkaProducer;
       private final AntiSnipeProperties antiSnipe;
   ```

   Also add the matching imports: `AntiSnipeProperties`, `AuctionExtension`, `AuctionExtensionRepository`, `AuctionKafkaProducer`, `AfterCommit`, `AuctionExtendedEvent` and `java.time.Duration`.

2. In `applyBid`, replace the tail, from `UUID previousWinnerId = …` through `return toResponse(auction, previousWinnerId);`, with:

   ```java
           UUID previousWinnerId = auction.getCurrentWinnerId();
           auction.setCurrentPrice(request.getAmount());
           auction.setCurrentWinnerId(request.getBidderId());
           auction.setTotalBids(auction.getTotalBids() + 1);
           auction.setLastBidId(request.getBidId());
           boolean extended = extendIfInSnipeWindow(auction, request, now);
           auctionItemRepository.save(auction);

           log.info("Applied bid {} on auction {}: price={}, bidder={}, totalBids={}, extended={}",
                   request.getBidId(), auctionId, request.getAmount(), request.getBidderId(), auction.getTotalBids(), extended);
           return toResponse(auction, previousWinnerId, extended);
   ```

3. In the replay branch, change `return toResponse(auction, null);` to `return toResponse(auction, null, false);`.

4. Add the extension method:

   ```java
       /**
        * Anti-sniping: a bid with less than {@code window} remaining pushes the end time out by
        * {@code extension}. Runs under the same row lock as the bid, so it is atomic with it and
        * serialized against closure.
        */
       private boolean extendIfInSnipeWindow(AuctionItem auction, ApplyBidRequest request, OffsetDateTime now) {
           OffsetDateTime previousEnd = auction.getEndTime();
           if (Duration.between(now, previousEnd).compareTo(antiSnipe.window()) >= 0) {
               return false;
           }
           OffsetDateTime newEnd = previousEnd.plus(antiSnipe.extension());
           auction.setEndTime(newEnd);
           auction.setExtensionCount(auction.getExtensionCount() + 1);
           auctionExtensionRepository.save(AuctionExtension.builder()
                   .auction(auction)
                   .previousEndTime(previousEnd)
                   .newEndTime(newEnd)
                   .extensionDurationSeconds((int) antiSnipe.extension().toSeconds())
                   .triggeredByBidId(request.getBidId())
                   .triggeredByUserId(request.getBidderId())
                   .build());

           AuctionExtendedEvent event = AuctionExtendedEvent.builder()
                   .auctionId(auction.getId())
                   .auctionTitle(auction.getTitle())
                   .previousEndTime(previousEnd.toInstant())
                   .newEndTime(newEnd.toInstant())
                   .extensionCount(auction.getExtensionCount())
                   .triggeredByBidId(request.getBidId())
                   .triggeredByUserId(request.getBidderId())
                   .build();
           AfterCommit.run(() -> kafkaProducer.publishAuctionExtended(event));
           log.info("Anti-sniping: auction {} extended from {} to {} by bid {}",
                   auction.getId(), previousEnd, newEnd, request.getBidId());
           return true;
       }
   ```

5. Change `toResponse` to take the flag:

   ```java
       private static ApplyBidResponse toResponse(AuctionItem auction, UUID previousWinnerId, boolean extended) {
           return ApplyBidResponse.builder()
                   .auctionId(auction.getId())
                   .currentPrice(auction.getCurrentPrice())
                   .currentWinnerId(auction.getCurrentWinnerId())
                   .previousWinnerId(previousWinnerId)
                   .totalBids(auction.getTotalBids())
                   .endTime(auction.getEndTime())
                   .extended(extended)
                   .extensionCount(auction.getExtensionCount())
                   .build();
       }
   ```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionBidServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. That is the 19 existing tests plus 7 new ones.

---

### Task 3: Closure re-checks the end time and reschedules an extended auction

**Files:**
- Modify: `auction-service/src/main/java/com/bidnow/auction/service/AuctionClosureService.java`
- Modify: `auction-service/src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java`

**Interfaces:**
- Consumes: the `Clock` bean (Story 1 `ClockConfig`).
- Produces:
  - `AuctionClosureService(AuctionItemRepository, AuctionStatusHistoryRepository, AuctionKafkaProducer, Clock)`, via `@RequiredArgsConstructor`.
  - `static UUID closureJobId(UUID auctionId, Instant closeAt)`.
  - `scheduleClosureJob(UUID, Instant)` keeps its signature, so callers are unchanged.

- [ ] **Step 1: Update the test class**

In `AuctionClosureServiceTest`:
- Remove `@InjectMocks` from `closureService`, and drop the now-unused `InjectMocks` import.
- Add these imports: `java.time.Clock`, `java.time.Instant`, `java.time.OffsetDateTime`, `java.time.ZoneOffset`.
- Add the constants and extend the existing `@BeforeEach`:

```java
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final OffsetDateTime PAST_END = OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC);
```

```java
    @BeforeEach
    void initTransactionSync() {
        TransactionSynchronizationManager.initSynchronization();
        closureService = new AuctionClosureService(auctionItemRepository, auctionStatusHistoryRepository,
                kafkaProducer, Clock.fixed(NOW, ZoneOffset.UTC));
    }
```

- Add `.endTime(PAST_END)` to the `AuctionItem.builder()` fixture in each test that reaches the closing logic: `close_whenActiveWithBids_completesAuction`, `close_whenActiveWithNoBids_failsAuction` and `close_readsAuctionWithRowLock`.

Then add these tests:

```java
    @Test
    void close_beforeExtendedEndTime_reschedulesInsteadOfClosing() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(3)
                .currentWinnerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("300.00"))
                .title("Extended Auction")
                .sellerId(UUID.randomUUID())
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC))
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        verify(auctionItemRepository, never()).save(any());
        verify(auctionStatusHistoryRepository, never()).save(any());
        // exactly one after-commit hook: the rescheduled closure job (not triggered here - it calls JobRunr)
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }

    @Test
    void close_exactlyAtEndTime_closes() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(0)
                .title("Ending Now")
                .sellerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .endTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
    }

    @Test
    void closureJobId_isDeterministicPerAuctionAndCloseTime() {
        UUID auctionId = UUID.randomUUID();
        Instant end = NOW;

        assertThat(AuctionClosureService.closureJobId(auctionId, end))
                .isEqualTo(AuctionClosureService.closureJobId(auctionId, end));
        assertThat(AuctionClosureService.closureJobId(auctionId, end))
                .isNotEqualTo(AuctionClosureService.closureJobId(auctionId, end.plusSeconds(300)));
        assertThat(AuctionClosureService.closureJobId(auctionId, end))
                .isNotEqualTo(AuctionClosureService.closureJobId(UUID.randomUUID(), end));
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionClosureServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (the 4-argument constructor and `closureJobId(UUID, Instant)` don't exist yet).

- [ ] **Step 3: Implement**

In `AuctionClosureService`:

1. Add `import java.time.Clock;` and the field `private final Clock clock;` as the **last** field.

2. Replace `closureJobId`:

   ```java
       /**
        * Deterministic, name-based closure job ID per (auction, close time). JobRunr ignores a second
        * job with an existing ID, so a rescheduled closure (after an anti-sniping extension) needs a
        * different ID, while repeated scheduling for the same end time stays idempotent.
        */
       static UUID closureJobId(UUID auctionId, Instant closeAt) {
           return UUID.nameUUIDFromBytes(
                   ("auction-closure:" + auctionId + ":" + closeAt.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
       }
   ```

3. In `scheduleClosureJob`, change `UUID jobId = closureJobId(auctionId);` to `UUID jobId = closureJobId(auctionId, closeAt);`.

4. In `close`, directly after the `status != ACTIVE` early return, add:

   ```java
           OffsetDateTime now = OffsetDateTime.now(clock);
           if (now.isBefore(auction.getEndTime())) {
               // Extended by anti-sniping since this job was scheduled: close at the new end time instead.
               log.info("Closure deferred — auction {} now ends at {}", auctionId, auction.getEndTime());
               scheduleClosureJob(auctionId, auction.getEndTime().toInstant());
               return;
           }
   ```

   Then delete the later `OffsetDateTime now = OffsetDateTime.now();` line, so the rest of the method uses the clock-based `now`.

5. Update the Javadoc of `close` to mention the deferral.

- [ ] **Step 4: Run the tests and the full auction-service suite**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionClosureServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (5 existing + 3 new).

Run: `mvn -q -pl auction-service -am test`
Expected: BUILD SUCCESS. Other tests mock `AuctionClosureService`, so they are unaffected.

---

### Task 4: bidding-service evicts its cached context on extension

**Files:**
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumer.java`
- Modify: `bidding-service/src/test/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumerTest.java`

**Interfaces:**
- Consumes: `AuctionExtendedEvent` (Task 1) and `AuctionContextCacheService.evict`.
- Produces: a listener on `auction-extended-topic`.

- [ ] **Step 1: Failing test**

Add to `AuctionLifecycleConsumerTest`, together with the import `com.bidnow.common.dto.event.AuctionExtendedEvent`:

```java
    @Test
    void auctionExtended_evictsContext() {
        UUID auctionId = UUID.randomUUID();

        consumer.onAuctionExtended(AuctionExtendedEvent.builder().auctionId(auctionId).build());

        verify(contextCache).evict(auctionId);
    }
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `mvn -q -pl bidding-service -am test -Dtest=AuctionLifecycleConsumerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

In `AuctionLifecycleConsumer`, add the import `com.bidnow.common.dto.event.AuctionExtendedEvent` and this method:

```java
    @KafkaListener(topics = "auction-extended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionExtended(AuctionExtendedEvent event) {
        log.info("Auction {} extended to {} - evicting bid context", event.getAuctionId(), event.getNewEndTime());
        contextCache.evict(event.getAuctionId());
    }
```

Also update the class Javadoc to: `Drops the cached bid context when an auction closes or its end time changes, so pre-validation uses fresh state.`

- [ ] **Step 4: Run it and confirm it passes**

Run: `mvn -q -pl bidding-service -am test`
Expected: BUILD SUCCESS.

---

### Task 5: auction-service BDD — extension and deferred closure

**Files:**
- Modify: `auction-service/src/test/resources/db/changelog/test-data.sql`
- Modify: `auction-service/src/test/resources/features/internal-bid-api.feature`
- Modify: `auction-service/src/test/java/com/bidnow/auction/bdd/steps/InternalBidSteps.java`

**Interfaces:**
- Consumes: the running service; `AdminAuctionService` (for force-close) and `AuctionClosureService.close`, both injected into the steps.
- Produces: none.

**Why the existing close-race scenario changes.** `close()` now defers while `now < endTime`. The Story 1 race auctions end in 7 days, so racing `close()` against bids would only reschedule. The race needs a closing path that doesn't wait for the end time, and admin force-close is one. It takes the same row lock and requires `total_bids > 0`, which holds because the race auctions are seeded with 1 bid.

- [ ] **Step 1: Seed a dedicated anti-snipe auction**

Append to `test-data.sql`:

```sql

-- changeset bidnow:bdd-anti-snipe-auctions
-- comment: Auctions for anti-sniping BDD (end times are moved by steps at runtime)
INSERT INTO auction_items (id, seller_id, title, description, category_id,
                           starting_price, bid_increment, deposit_amount,
                           current_price, total_bids, current_winner_id,
                           status, start_time, end_time, original_end_time,
                           created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-00000000000d'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Anti-Snipe Auction', 'Anti-sniping extension',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        100.00, 10.00, 20.00, 100.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-00000000000e'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Deferred Closure Auction', 'Closure after extension',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        100.00, 10.00, 20.00, 100.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()) ON CONFLICT (id) DO NOTHING;
```

- [ ] **Step 2: Steps**

In `InternalBidSteps`:
- Add a constructor dependency, `private final AdminAuctionService adminAuctionService;` (`com.bidnow.auction.service.AdminAuctionService`).
- Add the import `java.time.OffsetDateTime` if it is missing.
- Add these steps:

```java
    @Given("auction {string} ends in {int} seconds")
    public void auctionEndsIn(String auctionId, int seconds) {
        jdbcTemplate.update("UPDATE auction_items SET end_time = NOW() + (? * INTERVAL '1 second') WHERE id = ?::uuid",
                seconds, auctionId);
    }

    @Then("auction {string} should end about {int} seconds from now")
    public void auctionEndsAbout(String auctionId, int seconds) {
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT EXTRACT(EPOCH FROM (end_time - NOW()))::bigint FROM auction_items WHERE id = ?::uuid",
                Long.class, auctionId);
        assertThat(remaining).isBetween((long) seconds - 5, (long) seconds + 1);
    }

    @Then("auction {string} should have {int} extension(s) recorded")
    public void extensionsRecorded(String auctionId, int count) {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auction_extensions WHERE auction_id = ?::uuid", Integer.class, auctionId);
        Integer counter = jdbcTemplate.queryForObject(
                "SELECT extension_count FROM auction_items WHERE id = ?::uuid", Integer.class, auctionId);
        assertThat(rows).isEqualTo(count);
        assertThat(counter).isEqualTo(count);
    }

    @When("the closure job runs for auction {string}")
    public void closureRuns(String auctionId) {
        closureService.close(UUID.fromString(auctionId));
    }

    @Then("auction {string} should still be ACTIVE")
    public void stillActive(String auctionId) {
        assertThat(row(auctionId).get("status")).isEqualTo("ACTIVE");
    }
```

Also add `io.cucumber.java.en.Given` to the imports.

In `closeWhileBidding`, replace the closure task body `closureService.close(id);` with:

```java
            adminAuctionService.forceCloseAuction(UUID.fromString("550e8400-e29b-41d4-a716-000000000001"), id, null);
```

That ID is the BDD admin ID already used in `AdminAuctionSteps`. Keep the `-1` marker. Force-close throws `BadRequestException` when the auction is no longer ACTIVE, which can't happen before the race. If a race interleaving ever makes it throw, catch `BadRequestException` inside the closure task and return `-1`, so the race assertions stay about bids.

- [ ] **Step 3: Scenarios**

Append to `internal-bid-api.feature`:

```gherkin
  @anti-snipe
  Scenario: A bid inside the final two minutes extends the auction by five minutes
    Given auction "b0000000-0000-0000-0000-00000000000d" ends in 60 seconds
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "b0000000-0000-0000-0000-00000000000d"
    Then the response status should be 200
    And the response field "data.extended" should equal "true"
    And the response field "data.extensionCount" should equal "1"
    And auction "b0000000-0000-0000-0000-00000000000d" should end about 360 seconds from now
    And auction "b0000000-0000-0000-0000-00000000000d" should have 1 extension recorded

  @anti-snipe
  Scenario: The closure job does not close an extended auction early
    Given auction "b0000000-0000-0000-0000-00000000000e" ends in 30 seconds
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "b0000000-0000-0000-0000-00000000000e"
    And the closure job runs for auction "b0000000-0000-0000-0000-00000000000e"
    Then auction "b0000000-0000-0000-0000-00000000000e" should still be ACTIVE
```

In the existing scenario "First bid at the starting price, then increment enforcement" on auction `…005`, which ends in 7 days, add this line after the first `Then the response status should be 200`:

```gherkin
    And the response field "data.extended" should equal "false"
```

- [ ] **Step 4: Compile**

Run: `mvn -q -pl auction-service -am test-compile`
Expected: BUILD SUCCESS. Run `-Pbdd` only if the controller says so.

---

### Task 6: Documentation

**Files** (repo root):
- `docs/architecture.md`
- `docs/superpowers/specs/2026-07-04-bidding-service-design.md`
- `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`

- [ ] **Step 1: architecture.md**

In the "Service-to-Service Internal APIs" table, append this sentence to the purpose cell of the `POST /api/v1/internal/auctions/{id}/bids` row: `Anti-sniping: a bid with < 120 s left extends end_time by 300 s (auction.anti-snipe.*), recorded in auction_extensions; response extended=true.`

In the bidding events table (the "Bidding Service (public API & events)" subsection), change the consumed-topics row to include `auction-extended-topic`. Below that table, add:

```markdown
| `auction-extended-topic` | published by auction-service (after commit, key = auctionId) | `AuctionExtendedEvent {auctionId, auctionTitle, previousEndTime, newEndTime, extensionCount, triggeredByBidId, triggeredByUserId}` |
```

- [ ] **Step 2: Spec**

In §2, after the paragraph describing closure re-checking `now >= endTime`, add: `Closure job IDs are name-based on (auctionId, closeAt) so the deferred job for a new end time is a distinct JobRunr job; the stale job for the old end time finds now < endTime and reschedules (idempotent).`

- [ ] **Step 3: Roadmap**

In Story 4, tick 4.1, 4.2 and 4.3. Leave 4.4 unticked unless the controller reports a green `-Pbdd` run. Under 4.4, append: `BDD scenarios written (auction-service internal-bid-api.feature @anti-snipe); close-race scenario now races admin force-close because close() defers before endTime.`

---

## Self-review notes

- **Roadmap coverage:**
  - 4.1 is Tasks 1 and 2.
  - 4.2 is Task 3.
  - 4.3 is Task 4. The bid flag and event were already done in Story 3.
  - 4.4 is Task 5.
- **Spec coverage:**
  - The §4 config keys and the event fields are covered by Tasks 1 and 2.
  - The §7 row "closure job fires on an extended auction → reschedule" is covered by Task 3.
- **Callers are unchanged.** `scheduleClosureJob(UUID, Instant)` keeps its signature, so activation, publish and startup recovery are untouched. Jobs already scheduled with the old per-auction ID still fire and call `close()`, which now handles both cases.
- **Known limitation.** Extensions are unbounded, which is the intended rule because each one needs a real new bid. There is no maximum-extension cap, and none was requested.
