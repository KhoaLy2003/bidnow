# Auction Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close ACTIVE auctions at `endTime` via a scheduled JobRunr job, and recover any auctions missed during server downtime via a startup scan.

**Architecture:** Mirror the existing `AuctionActivationJob` pattern. A new `AuctionClosureService` owns closure logic and the `scheduleClosureJob` helper. A new `AuctionClosureJob` wraps it for JobRunr. `AuctionStartupRecoveryService` runs once on `ApplicationReadyEvent` to close overdue ACTIVE auctions and activate/close overdue SCHEDULED auctions.

**Tech Stack:** Spring Boot 3.2.4, JobRunr (`jobrunr-spring-boot-3-starter`), Spring Data JPA, Kafka, JUnit 5 + Mockito

## Global Constraints

- All new production classes under `com.bidnow.auction.*` matching existing package structure
- Follow existing Lombok + `@RequiredArgsConstructor` pattern — no manual constructors
- `@Transactional` on every service method that writes to DB
- No new DB migrations — no schema changes required
- Unit tests use `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` (no Spring context)
- Test run command: `cd backend && mvn test -pl auction-service -Dtest=<ClassName>`
- Build command: `cd backend && mvn clean install -pl common,auction-service -DskipTests`

---

## File Map

**New files:**
- `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionClosureService.java`
- `backend/auction-service/src/main/java/com/bidnow/auction/job/AuctionClosureJob.java`
- `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionStartupRecoveryService.java`
- `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java`
- `backend/auction-service/src/test/java/com/bidnow/auction/job/AuctionClosureJobTest.java`
- `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionStartupRecoveryServiceTest.java`

**Modified files:**
- `backend/auction-service/src/main/java/com/bidnow/auction/kafka/AuctionKafkaProducer.java`
- `backend/auction-service/src/main/java/com/bidnow/auction/repository/AuctionItemRepository.java`
- `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionActivationService.java`
- `backend/auction-service/src/main/java/com/bidnow/auction/service/impl/AuctionServiceImpl.java`
- `backend/auction-service/src/test/java/com/bidnow/auction/service/impl/AuctionServiceImplTest.java`

---

### Task 1: Infrastructure prerequisites — Kafka topic + Repository queries

**Files:**
- Modify: `backend/auction-service/src/main/java/com/bidnow/auction/kafka/AuctionKafkaProducer.java`
- Modify: `backend/auction-service/src/main/java/com/bidnow/auction/repository/AuctionItemRepository.java`

**Interfaces:**
- Produces:
  - `AuctionKafkaProducer.publishAuctionEnded(AuctionEndedEvent event): void`
  - `AuctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(AuctionStatus, OffsetDateTime): List<AuctionItem>`
  - `AuctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(AuctionStatus, OffsetDateTime): List<AuctionItem>`

- [ ] **Step 1: Add `publishAuctionEnded` to `AuctionKafkaProducer`**

Add import alongside existing event imports:
```java
import com.bidnow.common.dto.event.AuctionEndedEvent;
```

Add below the existing `AUCTION_CANCELLED_TOPIC` constant and `publishAuctionCancelled` method:
```java
private static final String AUCTION_ENDED_TOPIC = "auction-ended-topic";

public void publishAuctionEnded(AuctionEndedEvent event) {
    kafkaTemplate.send(AUCTION_ENDED_TOPIC, event.getAuctionId().toString(), event);
    log.info("Published AuctionEndedEvent for auction: {}", event.getAuctionId());
}
```

- [ ] **Step 2: Add overdue-auction queries to `AuctionItemRepository`**

Add imports alongside existing imports:
```java
import java.time.OffsetDateTime;
import java.util.List;
```

Add below the existing `countByStatusGroupByCategory` method:
```java
List<AuctionItem> findByStatusAndEndTimeBeforeAndDeletedAtIsNull(AuctionStatus status, OffsetDateTime endTime);

List<AuctionItem> findByStatusAndStartTimeBeforeAndDeletedAtIsNull(AuctionStatus status, OffsetDateTime startTime);
```

- [ ] **Step 3: Build to verify no compilation errors**

```bash
cd backend && mvn clean install -pl common,auction-service -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add backend/auction-service/src/main/java/com/bidnow/auction/kafka/AuctionKafkaProducer.java
git add backend/auction-service/src/main/java/com/bidnow/auction/repository/AuctionItemRepository.java
git commit -m "feat(auction): add AuctionEndedEvent publisher and overdue-auction queries"
```

---

### Task 2: `AuctionClosureService` — core closure logic (TDD)

**Files:**
- Create: `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionClosureService.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java`

**Interfaces:**
- Consumes: `AuctionItemRepository.findByIdAndDeletedAtIsNull`, `AuctionItemRepository.save`, `AuctionStatusHistoryRepository.save`, `AuctionKafkaProducer.publishAuctionEnded` (Task 1)
- Produces:
  - `AuctionClosureService.close(UUID auctionId): void`
  - `AuctionClosureService.scheduleClosureJob(UUID auctionId, Instant closeAt): void`

- [ ] **Step 1: Write failing tests**

Create `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosureServiceTest {

    @Mock private AuctionItemRepository auctionItemRepository;
    @Mock private AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    @Mock private AuctionKafkaProducer kafkaProducer;

    @InjectMocks
    private AuctionClosureService closureService;

    @Test
    void close_whenActiveWithBids_completesAuction() {
        UUID auctionId = UUID.randomUUID();
        UUID currentWinnerId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(2)
                .currentWinnerId(currentWinnerId)
                .currentPrice(new BigDecimal("300.00"))
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .build();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        assertThat(auction.getWinnerId()).isEqualTo(currentWinnerId);
        assertThat(auction.getCompletedAt()).isNotNull();
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionStatusHistory> historyCaptor = ArgumentCaptor.forClass(AuctionStatusHistory.class);
        verify(auctionStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo("ACTIVE");
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<AuctionEndedEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEndedEvent.class);
        verify(kafkaProducer).publishAuctionEnded(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getWinnerId()).isEqualTo(currentWinnerId);
        assertThat(eventCaptor.getValue().getWinningBidAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(eventCaptor.getValue().getLoserIds()).isEmpty();
    }

    @Test
    void close_whenActiveWithNoBids_failsAuction() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(0)
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .build();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        assertThat(auction.getWinnerId()).isNull();
        verify(auctionItemRepository).save(auction);

        ArgumentCaptor<AuctionEndedEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEndedEvent.class);
        verify(kafkaProducer).publishAuctionEnded(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getWinnerId()).isNull();
        assertThat(eventCaptor.getValue().getWinningBidAmount()).isNull();
        assertThat(eventCaptor.getValue().getLoserIds()).isEmpty();
    }

    @Test
    void close_whenAuctionNotFound_skipsGracefully() {
        UUID auctionId = UUID.randomUUID();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.empty());

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).save(any());
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }

    @Test
    void close_whenAlreadyCompleted_isNoop() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.COMPLETED)
                .build();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).save(any());
        verify(kafkaProducer, never()).publishAuctionEnded(any());
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
cd backend && mvn test -pl auction-service -Dtest=AuctionClosureServiceTest
```

Expected: `COMPILATION ERROR` — `AuctionClosureService` does not exist yet.

- [ ] **Step 3: Implement `AuctionClosureService`**

Create `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionClosureService.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.job.AuctionClosureJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.BackgroundJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosureService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final AuctionKafkaProducer kafkaProducer;

    @Transactional
    public void close(UUID auctionId) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElse(null);
        if (auction == null) {
            log.warn("Closure skipped — auction {} not found or deleted", auctionId);
            return;
        }
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            log.info("Closure skipped — auction {} is already in status {}", auctionId, auction.getStatus());
            return;
        }

        boolean hasWinner = auction.getTotalBids() > 0;
        AuctionStatus newStatus = hasWinner ? AuctionStatus.COMPLETED : AuctionStatus.FAILED;
        OffsetDateTime now = OffsetDateTime.now();

        if (hasWinner) {
            auction.setWinnerId(auction.getCurrentWinnerId());
            auction.setCompletedAt(now);
        }
        auction.setStatus(newStatus);
        auctionItemRepository.save(auction);

        auctionStatusHistoryRepository.save(AuctionStatusHistory.builder()
                .auction(auction)
                .fromStatus(AuctionStatus.ACTIVE.name())
                .toStatus(newStatus.name())
                .triggeredBy(null)
                .reason("Auction end time reached")
                .build());

        kafkaProducer.publishAuctionEnded(AuctionEndedEvent.builder()
                .auctionId(auction.getId())
                .auctionTitle(auction.getTitle())
                .sellerId(auction.getSellerId())
                .winnerId(hasWinner ? auction.getWinnerId() : null)
                .winningBidAmount(hasWinner ? auction.getCurrentPrice() : null)
                .loserIds(List.of())
                .build());

        log.info("Closed auction {} (ACTIVE → {})", auctionId, newStatus);
    }

    public void scheduleClosureJob(UUID auctionId, Instant closeAt) {
        UUID jobId = closureJobId(auctionId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                BackgroundJob.<AuctionClosureJob>schedule(jobId, closeAt, job -> job.closeAuction(auctionId));
                log.info("Scheduled closure job {} for auction {} at {}", jobId, auctionId, closeAt);
            }
        });
    }

    static UUID closureJobId(UUID auctionId) {
        return UUID.nameUUIDFromBytes(
                ("auction-closure:" + auctionId).getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
cd backend && mvn test -pl auction-service -Dtest=AuctionClosureServiceTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionClosureService.java
git add backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java
git commit -m "feat(auction): implement AuctionClosureService"
```

---

### Task 3: `AuctionClosureJob` — JobRunr wrapper (TDD)

**Files:**
- Create: `backend/auction-service/src/main/java/com/bidnow/auction/job/AuctionClosureJob.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/job/AuctionClosureJobTest.java`

**Interfaces:**
- Consumes: `AuctionClosureService.close(UUID): void` (Task 2)
- Produces: `AuctionClosureJob.closeAuction(UUID auctionId): void`

- [ ] **Step 1: Write failing test**

Create `backend/auction-service/src/test/java/com/bidnow/auction/job/AuctionClosureJobTest.java`:

```java
package com.bidnow.auction.job;

import com.bidnow.auction.service.AuctionClosureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionClosureJobTest {

    @Mock private AuctionClosureService closureService;

    @InjectMocks
    private AuctionClosureJob auctionClosureJob;

    @Test
    void closeAuction_delegatesToService() {
        UUID auctionId = UUID.randomUUID();
        auctionClosureJob.closeAuction(auctionId);
        verify(closureService).close(auctionId);
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
cd backend && mvn test -pl auction-service -Dtest=AuctionClosureJobTest
```

Expected: `COMPILATION ERROR` — `AuctionClosureJob` does not exist yet.

- [ ] **Step 3: Implement `AuctionClosureJob`**

Create `backend/auction-service/src/main/java/com/bidnow/auction/job/AuctionClosureJob.java`:

```java
package com.bidnow.auction.job;

import com.bidnow.auction.service.AuctionClosureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosureJob {

    private final AuctionClosureService closureService;

    @Job(name = "Close auction %0", retries = 3)
    public void closeAuction(UUID auctionId) {
        closureService.close(auctionId);
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && mvn test -pl auction-service -Dtest=AuctionClosureJobTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/auction-service/src/main/java/com/bidnow/auction/job/AuctionClosureJob.java
git add backend/auction-service/src/test/java/com/bidnow/auction/job/AuctionClosureJobTest.java
git commit -m "feat(auction): add AuctionClosureJob"
```

---

### Task 4: Wire `scheduleClosureJob` into activation and publish flows

**Files:**
- Modify: `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionActivationService.java`
- Modify: `backend/auction-service/src/main/java/com/bidnow/auction/service/impl/AuctionServiceImpl.java`
- Modify: `backend/auction-service/src/test/java/com/bidnow/auction/service/impl/AuctionServiceImplTest.java`

**Interfaces:**
- Consumes: `AuctionClosureService.scheduleClosureJob(UUID, Instant): void` (Task 2)

- [ ] **Step 1: Update `AuctionActivationService`**

Add import:
```java
import com.bidnow.auction.service.AuctionClosureService;
```

Add new field after existing fields (Lombok picks it up automatically):
```java
private final AuctionClosureService closureService;
```

In `activate()`, add the closure job scheduling before the final log statement. Replace:
```java
        log.info("Activated auction {} (SCHEDULED → ACTIVE)", auctionId);
```
With:
```java
        closureService.scheduleClosureJob(auctionId, auction.getEndTime().toInstant());
        log.info("Activated auction {} (SCHEDULED → ACTIVE)", auctionId);
```

- [ ] **Step 2: Update `AuctionServiceImpl` — `publishAuction`**

Add import:
```java
import com.bidnow.auction.service.AuctionClosureService;
```

Add new field after existing fields:
```java
private final AuctionClosureService auctionClosureService;
```

In `publishAuction()`, find the block that handles `newStatus == ACTIVE` and add one line after `publishAuctionCreated`. Replace:
```java
        if (newStatus == AuctionStatus.ACTIVE) {
            auctionKafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .title(auction.getTitle())
                    .startingPrice(auction.getStartingPrice())
                    .endTime(auction.getEndTime().toInstant())
                    .build());
        } else if (newStatus == AuctionStatus.SCHEDULED) {
            scheduleActivationJob(auction.getId(), auction.getStartTime().toInstant());
        }
```
With:
```java
        if (newStatus == AuctionStatus.ACTIVE) {
            auctionKafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .title(auction.getTitle())
                    .startingPrice(auction.getStartingPrice())
                    .endTime(auction.getEndTime().toInstant())
                    .build());
            auctionClosureService.scheduleClosureJob(auction.getId(), auction.getEndTime().toInstant());
        } else if (newStatus == AuctionStatus.SCHEDULED) {
            scheduleActivationJob(auction.getId(), auction.getStartTime().toInstant());
        }
```

- [ ] **Step 3: Update `AuctionServiceImpl` — `createAuction`**

Same pattern. Find:
```java
        if (status == AuctionStatus.ACTIVE) {
            auctionKafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .title(auction.getTitle())
                    .startingPrice(auction.getStartingPrice())
                    .endTime(auction.getEndTime().toInstant())
                    .build());
        } else if (status == AuctionStatus.SCHEDULED) {
            scheduleActivationJob(auction.getId(), auction.getStartTime().toInstant());
        }
```
Replace with:
```java
        if (status == AuctionStatus.ACTIVE) {
            auctionKafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .title(auction.getTitle())
                    .startingPrice(auction.getStartingPrice())
                    .endTime(auction.getEndTime().toInstant())
                    .build());
            auctionClosureService.scheduleClosureJob(auction.getId(), auction.getEndTime().toInstant());
        } else if (status == AuctionStatus.SCHEDULED) {
            scheduleActivationJob(auction.getId(), auction.getStartTime().toInstant());
        }
```

- [ ] **Step 4: Update `AuctionServiceImplTest` — add missing mock**

In `AuctionServiceImplTest`, `@InjectMocks` constructs `AuctionServiceImpl` via its all-args constructor. Adding `AuctionClosureService` as a field requires a matching mock. Add alongside existing `@Mock` declarations:

```java
@Mock private AuctionClosureService auctionClosureService;
```

Add import:
```java
import com.bidnow.auction.service.AuctionClosureService;
```

- [ ] **Step 5: Run existing tests — verify nothing broken**

```bash
cd backend && mvn test -pl auction-service -Dtest=AuctionServiceImplTest
```

Expected: same pass count as before, 0 failures.

- [ ] **Step 6: Build**

```bash
cd backend && mvn clean install -pl common,auction-service -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionActivationService.java
git add backend/auction-service/src/main/java/com/bidnow/auction/service/impl/AuctionServiceImpl.java
git add backend/auction-service/src/test/java/com/bidnow/auction/service/impl/AuctionServiceImplTest.java
git commit -m "feat(auction): schedule closure job when auction becomes ACTIVE"
```

---

### Task 5: `AuctionStartupRecoveryService` — startup scan (TDD)

**Files:**
- Create: `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionStartupRecoveryService.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionStartupRecoveryServiceTest.java`

**Interfaces:**
- Consumes:
  - `AuctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(AuctionStatus, OffsetDateTime)` (Task 1)
  - `AuctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(AuctionStatus, OffsetDateTime)` (Task 1)
  - `AuctionClosureService.close(UUID)` (Task 2)
  - `AuctionActivationService.activate(UUID)` (existing)

- [ ] **Step 1: Write failing tests**

Create `backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionStartupRecoveryServiceTest.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionStartupRecoveryServiceTest {

    @Mock private AuctionItemRepository auctionItemRepository;
    @Mock private AuctionClosureService closureService;
    @Mock private AuctionActivationService activationService;

    @InjectMocks
    private AuctionStartupRecoveryService recoveryService;

    @Test
    void recoverOverdueAuctions_withOverdueActiveAuction_callsClose() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder().id(id).build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction));
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.SCHEDULED), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        recoveryService.recoverOverdueAuctions();

        verify(closureService).close(id);
        verify(activationService, never()).activate(any());
    }

    @Test
    void recoverOverdueAuctions_scheduledPastStartFutureEnd_activatesOnly() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(id)
                .endTime(OffsetDateTime.now().plusHours(1))
                .build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.SCHEDULED), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction));

        recoveryService.recoverOverdueAuctions();

        verify(activationService).activate(id);
        verify(closureService, never()).close(any());
    }

    @Test
    void recoverOverdueAuctions_scheduledPastBothTimes_activatesAndCloses() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(id)
                .endTime(OffsetDateTime.now().minusHours(1))
                .build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.SCHEDULED), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction));

        recoveryService.recoverOverdueAuctions();

        verify(activationService).activate(id);
        verify(closureService).close(id);
    }

    @Test
    void recoverOverdueAuctions_noOverdueAuctions_doesNothing() {
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());

        recoveryService.recoverOverdueAuctions();

        verify(closureService, never()).close(any());
        verify(activationService, never()).activate(any());
    }

    @Test
    void recoverOverdueAuctions_closeThrows_continuesNextAuction() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        AuctionItem auction1 = AuctionItem.builder().id(id1).build();
        AuctionItem auction2 = AuctionItem.builder().id(id2).build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction1, auction2));
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("DB error")).when(closureService).close(id1);

        recoveryService.recoverOverdueAuctions();

        verify(closureService).close(id1);
        verify(closureService).close(id2);
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
cd backend && mvn test -pl auction-service -Dtest=AuctionStartupRecoveryServiceTest
```

Expected: `COMPILATION ERROR` — `AuctionStartupRecoveryService` does not exist yet.

- [ ] **Step 3: Implement `AuctionStartupRecoveryService`**

Create `backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionStartupRecoveryService.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStartupRecoveryService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionClosureService closureService;
    private final AuctionActivationService activationService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOverdueAuctions() {
        OffsetDateTime now = OffsetDateTime.now();
        log.info("Starting auction recovery scan at {}", now);

        List<AuctionItem> overdueActive = auctionItemRepository
                .findByStatusAndEndTimeBeforeAndDeletedAtIsNull(AuctionStatus.ACTIVE, now);
        log.info("Found {} overdue ACTIVE auctions", overdueActive.size());
        for (AuctionItem auction : overdueActive) {
            try {
                closureService.close(auction.getId());
            } catch (Exception e) {
                log.error("Failed to close overdue ACTIVE auction {}: {}", auction.getId(), e.getMessage());
            }
        }

        List<AuctionItem> overdueScheduled = auctionItemRepository
                .findByStatusAndStartTimeBeforeAndDeletedAtIsNull(AuctionStatus.SCHEDULED, now);
        log.info("Found {} overdue SCHEDULED auctions", overdueScheduled.size());
        for (AuctionItem auction : overdueScheduled) {
            try {
                activationService.activate(auction.getId());
                if (!auction.getEndTime().isAfter(now)) {
                    closureService.close(auction.getId());
                }
            } catch (Exception e) {
                log.error("Failed to recover overdue SCHEDULED auction {}: {}", auction.getId(), e.getMessage());
            }
        }

        log.info("Auction recovery scan complete");
    }
}
```

- [ ] **Step 4: Run Task 5 tests — expect pass**

```bash
cd backend && mvn test -pl auction-service -Dtest=AuctionStartupRecoveryServiceTest
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Run full test suite**

```bash
cd backend && mvn test -pl auction-service
```

Expected: All tests passing, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/auction-service/src/main/java/com/bidnow/auction/service/AuctionStartupRecoveryService.java
git add backend/auction-service/src/test/java/com/bidnow/auction/service/AuctionStartupRecoveryServiceTest.java
git commit -m "fix(auction): recover auctions missed during server downtime"
```
