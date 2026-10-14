# WALLET-304 Deposit Refund Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refund locked deposits automatically: to every non-winner when an auction ends, and to every bidder when an auction is cancelled. Also emit a `DepositRefundedEvent` per refund, and give wallet-service Kafka retries with a dead-letter topic.

**Architecture:** A non-transactional Kafka consumer (`AuctionLifecycleEventConsumer`) listens on `auction-ended-topic` and `auction-cancelled-topic`. It finds all `LOCKED` `deposit_locks` rows for the auction, skips the winner's, and calls `DepositLockService.releaseDeposit` once per lock. Each call is its own DB transaction: it locks the wallet row first, then moves the funds back from locked to available, writes a REFUND transaction, marks the lock RELEASED, and raises an in-process event. After commit, that event becomes a Kafka `DepositRefundedEvent`. If some locks fail, the consumer rethrows once the loop is done. A `DefaultErrorHandler` then retries with exponential backoff and finally dead-letters the record. Locks that were already released skip on redelivery.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Kafka, Spring Data JPA (Hibernate), PostgreSQL, Lombok, JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-28-wallet-deposit-refund-design.md`

## Global Constraints

- **Do not run `git add`, `git commit`, `git stash`, `git reset`, or any other git write.** Leave every change uncommitted in the working tree on branch `feature/wallet-deposit-lock`. The user commits.
- Scope: wallet-service, plus one new DTO in `common` (`DepositRefundedEvent`). Do not change auction-service or media-service.
- Consumed topics: `auction-ended-topic` (`AuctionEndedEvent`) and `auction-cancelled-topic` (`AuctionCancelledEvent`). Produced topic: `deposit-refunded-topic`, keyed by `userId`.
- Who gets refunded: every `LOCKED` `deposit_locks` row for the auction, except the winner's wallet on end. On cancel, every row. Ignore `AuctionEndedEvent.loserIds`.
- The refund amount is the lock row's `amount`.
- One DB transaction per lock. Read the wallet row with `findByIdForUpdate` **before** reading the lock row inside that transaction.
- A zero-amount lock is still set to RELEASED, but with no balance change, no REFUND transaction and no event.
- Suspended wallets are refunded.
- Refunding never changes `total_balance`. Only `available += amount` and `locked -= amount`.
- `RefundReason` values: `AUCTION_LOST` (end) and `AUCTION_CANCELLED` (cancel). The event's `reason` field is the enum name.
- Retry: exponential backoff of 3 retries, starting at 1000 ms, multiplier 2.0, then `DeadLetterPublishingRecoverer` to `<topic>.DLT`.
- Value deserializer: `ErrorHandlingDeserializer` delegating to `JsonDeserializer`.
- Run Maven from `backend/`: `mvn -q -pl wallet-service -am test …`. The `-am` flag builds `common` first.

---

## File Map

Paths are relative to `backend/wallet-service/` unless they start with `backend/common/` or `docs/`.

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/bidnow/wallet/domain/enums/RefundReason.java` | Create | `AUCTION_LOST, AUCTION_CANCELLED` |
| `src/main/java/com/bidnow/wallet/repository/DepositLockRepository.java` | Modify | + `findByAuctionIdAndStatus` |
| `src/main/java/com/bidnow/wallet/repository/WalletRepository.java` | Modify | + `findByIdForUpdate` |
| `src/main/java/com/bidnow/wallet/kafka/DepositRefundedApplicationEvent.java` | Create | In-process refund event |
| `src/main/java/com/bidnow/wallet/service/DepositLockService.java` | Modify | + `releaseDeposit` |
| `src/main/java/com/bidnow/wallet/service/impl/DepositLockServiceImpl.java` | Modify | Implement `releaseDeposit` |
| `src/test/java/com/bidnow/wallet/service/impl/DepositLockServiceImplTest.java` | Modify | Release tests |
| `backend/common/src/main/java/com/bidnow/common/dto/event/DepositRefundedEvent.java` | Create | Kafka payload |
| `src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java` | Modify | + `onDepositRefunded` |
| `src/test/java/com/bidnow/wallet/kafka/WalletEventPublisherTest.java` | Create | Publisher test |
| `src/main/java/com/bidnow/wallet/exception/DepositReleaseException.java` | Create | Partial-failure signal |
| `src/main/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumer.java` | Create | End/cancel listeners |
| `src/test/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumerTest.java` | Create | Consumer tests |
| `src/main/java/com/bidnow/wallet/config/KafkaConsumerConfig.java` | Create | Error handler bean |
| `src/test/java/com/bidnow/wallet/config/KafkaConsumerConfigTest.java` | Create | Config test |
| `src/main/resources/application.yml` | Modify | `ErrorHandlingDeserializer` |
| `docs/architecture.md`, `docs/epics/wallet/epic.md` | Modify | Docs |

---

### Task 1: `releaseDeposit` — the per-lock refund transaction

**Files:**
- Create: `src/main/java/com/bidnow/wallet/domain/enums/RefundReason.java`
- Create: `src/main/java/com/bidnow/wallet/kafka/DepositRefundedApplicationEvent.java`
- Modify: `src/main/java/com/bidnow/wallet/repository/DepositLockRepository.java`
- Modify: `src/main/java/com/bidnow/wallet/repository/WalletRepository.java`
- Modify: `src/main/java/com/bidnow/wallet/service/DepositLockService.java`
- Modify: `src/main/java/com/bidnow/wallet/service/impl/DepositLockServiceImpl.java`
- Test: `src/test/java/com/bidnow/wallet/service/impl/DepositLockServiceImplTest.java`

**Interfaces:**
- Consumes (existing): `Wallet` (with `getId()`, `getUserId()`, balances), `DepositLock` (`getId()`, `getWalletId()`, `getAuctionId()`, `getAmount()`, `getStatus()`, `setStatus`, `setReleasedAt`), `DepositLockStatus`, `Transaction` builder, `TransactionType.REFUND`, `TransactionStatus.COMPLETED`, `TransactionRepository`.
- Produces:
  - `enum RefundReason { AUCTION_LOST, AUCTION_CANCELLED }`
  - `DepositLockRepository.findByAuctionIdAndStatus(UUID auctionId, DepositLockStatus status): List<DepositLock>`
  - `WalletRepository.findByIdForUpdate(UUID id): Optional<Wallet>` (PESSIMISTIC_WRITE)
  - `DepositRefundedApplicationEvent(Object source, UUID userId, UUID walletId, UUID auctionId, BigDecimal amount, RefundReason reason, Instant refundedAt)`, with getters for each field
  - `DepositLockService.releaseDeposit(UUID lockId, UUID walletId, RefundReason reason): void`

- [ ] **Step 1: Create the enum and the application event**

`src/main/java/com/bidnow/wallet/domain/enums/RefundReason.java`:

```java
package com.bidnow.wallet.domain.enums;

public enum RefundReason {
    AUCTION_LOST, AUCTION_CANCELLED
}
```

`src/main/java/com/bidnow/wallet/kafka/DepositRefundedApplicationEvent.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.wallet.domain.enums.RefundReason;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class DepositRefundedApplicationEvent extends ApplicationEvent {

    private final UUID userId;
    private final UUID walletId;
    private final UUID auctionId;
    private final BigDecimal amount;
    private final RefundReason reason;
    private final Instant refundedAt;

    public DepositRefundedApplicationEvent(Object source, UUID userId, UUID walletId, UUID auctionId,
                                           BigDecimal amount, RefundReason reason, Instant refundedAt) {
        super(source);
        this.userId = userId;
        this.walletId = walletId;
        this.auctionId = auctionId;
        this.amount = amount;
        this.reason = reason;
        this.refundedAt = refundedAt;
    }
}
```

- [ ] **Step 2: Add the repository methods**

In `src/main/java/com/bidnow/wallet/repository/DepositLockRepository.java`, add the imports `com.bidnow.wallet.domain.enums.DepositLockStatus` and `java.util.List`, and add this method to the interface:

```java
    List<DepositLock> findByAuctionIdAndStatus(UUID auctionId, DepositLockStatus status);
```

In `src/main/java/com/bidnow/wallet/repository/WalletRepository.java`, add this after `findByUserIdForUpdate`. The imports it needs are already present.

```java
    /** Loads the wallet by id with SELECT ... FOR UPDATE. Same first-read rule as findByUserIdForUpdate. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
```

- [ ] **Step 3: Add the interface method**

In `src/main/java/com/bidnow/wallet/service/DepositLockService.java`, add the import `com.bidnow.wallet.domain.enums.RefundReason`, and add this method:

```java
    void releaseDeposit(UUID lockId, UUID walletId, RefundReason reason);
```

- [ ] **Step 4: Write the failing tests**

In `src/test/java/com/bidnow/wallet/service/impl/DepositLockServiceImplTest.java`:

(a) Add these imports:

```java
import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.kafka.DepositRefundedApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
```

Also add this static import:

```java
import static org.mockito.Mockito.verifyNoInteractions;
```

(b) Add this mock field after the `transactionRepository` mock. `@InjectMocks` passes it to the constructor.

```java
    @Mock
    private ApplicationEventPublisher eventPublisher;
```

(c) Append these tests before the class's final closing brace:

```java
    // ── releaseDeposit ────────────────────────────────────────────────────────

    @Test
    void releaseDeposit_lockedLock_refundsMarksReleasedAndPublishesEvent() {
        Wallet w = wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE);
        DepositLock l = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("200.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(w.getTotalBalance()).isEqualByComparingTo("200.00");
        verify(walletRepository).save(w);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction refund = txCaptor.getValue();
        assertThat(refund.getWalletId()).isEqualTo(walletId);
        assertThat(refund.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(refund.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(refund.getAmount()).isEqualByComparingTo("50.00");
        assertThat(refund.getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(refund.getAvailableBalanceAfter()).isEqualByComparingTo("200.00");
        assertThat(refund.getReferenceId()).isEqualTo(auctionId);
        assertThat(refund.getDescription()).contains(auctionId.toString()).contains("AUCTION_LOST");

        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(l.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(l);

        ArgumentCaptor<DepositRefundedApplicationEvent> eventCaptor =
                ArgumentCaptor.forClass(DepositRefundedApplicationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DepositRefundedApplicationEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getWalletId()).isEqualTo(walletId);
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getReason()).isEqualTo(RefundReason.AUCTION_LOST);
        assertThat(event.getRefundedAt()).isNotNull();
    }

    @Test
    void releaseDeposit_readsWalletOnlyThroughRowLock() {
        DepositLock l = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_CANCELLED);

        verify(walletRepository).findByIdForUpdate(walletId);
        verify(walletRepository, never()).findById(any());
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void releaseDeposit_alreadyReleased_isSilentNoOp() {
        Wallet w = wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE);
        DepositLock l = lock("50.00", DepositLockStatus.RELEASED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("200.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_forfeited_isSilentNoOp() {
        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet("150.00", "150.00", "0.00", WalletStatus.ACTIVE)));
        DepositLock l = lock("50.00", DepositLockStatus.FORFEITED);
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.FORFEITED);
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_lockMissing_isSilentNoOp() {
        UUID lockId = UUID.randomUUID();
        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findById(lockId)).thenReturn(Optional.empty());

        depositLockService.releaseDeposit(lockId, walletId, RefundReason.AUCTION_LOST);

        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_walletMissing_isSilentNoOp() {
        UUID lockId = UUID.randomUUID();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.empty());

        depositLockService.releaseDeposit(lockId, walletId, RefundReason.AUCTION_LOST);

        verify(depositLockRepository, never()).findById(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_zeroAmount_releasesWithoutTransactionOrEvent() {
        Wallet w = wallet("0.00", "0.00", "0.00", WalletStatus.ACTIVE);
        DepositLock l = lock("0.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(l.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(l);
        assertThat(w.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_suspendedWallet_isStillRefunded() {
        Wallet w = wallet("200.00", "150.00", "50.00", WalletStatus.SUSPENDED);
        DepositLock l = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_CANCELLED);

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("200.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
    }
```

- [ ] **Step 5: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=DepositLockServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `DepositLockServiceImpl` doesn't implement `releaseDeposit(UUID, UUID, RefundReason)`.

- [ ] **Step 6: Implement**

In `src/main/java/com/bidnow/wallet/service/impl/DepositLockServiceImpl.java`:

(a) Add these imports:

```java
import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.kafka.DepositRefundedApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
```

(b) Add the field after `transactionRepository`:

```java
    private final ApplicationEventPublisher eventPublisher;
```

(c) Add this method after `lockDeposit`:

```java
    @Override
    @Transactional
    public void releaseDeposit(UUID lockId, UUID walletId, RefundReason reason) {
        // Row lock first, same order as lockDeposit: wallet row, then deposit lock row.
        Optional<Wallet> maybeWallet = walletRepository.findByIdForUpdate(walletId);
        if (maybeWallet.isEmpty()) {
            log.warn("Deposit release skipped: wallet {} not found (lockId={})", walletId, lockId);
            return;
        }
        Wallet wallet = maybeWallet.get();

        Optional<DepositLock> maybeLock = depositLockRepository.findById(lockId);
        if (maybeLock.isEmpty() || maybeLock.get().getStatus() != DepositLockStatus.LOCKED) {
            log.debug("Deposit release skipped: lock {} missing or not LOCKED", lockId);
            return;
        }
        DepositLock lock = maybeLock.get();
        BigDecimal amount = lock.getAmount();

        if (amount.signum() > 0) {
            BigDecimal balanceBefore = wallet.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.add(amount);
            wallet.setAvailableBalance(balanceAfter);
            wallet.setLockedBalance(wallet.getLockedBalance().subtract(amount));
            walletRepository.save(wallet);

            transactionRepository.save(Transaction.builder()
                    .walletId(wallet.getId())
                    .type(TransactionType.REFUND)
                    .amount(amount)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(lock.getAuctionId())
                    .status(TransactionStatus.COMPLETED)
                    .description("Deposit refund for auction " + lock.getAuctionId() + " (" + reason + ")")
                    .build());
        }

        Instant now = Instant.now();
        lock.setStatus(DepositLockStatus.RELEASED);
        lock.setReleasedAt(LocalDateTime.now());
        depositLockRepository.save(lock);

        if (amount.signum() > 0) {
            eventPublisher.publishEvent(new DepositRefundedApplicationEvent(this, wallet.getUserId(),
                    wallet.getId(), lock.getAuctionId(), amount, reason, now));
        }

        log.info("Deposit released for walletId={}, auctionId={}, amount={}, reason={}",
                wallet.getId(), lock.getAuctionId(), amount, reason);
    }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=DepositLockServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. That's the 14 existing lock tests plus the 8 new release tests, 22 in total.

---

### Task 2: `DepositRefundedEvent` published to Kafka after commit

**Files:**
- Create: `backend/common/src/main/java/com/bidnow/common/dto/event/DepositRefundedEvent.java`
- Modify: `src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java`
- Test: `src/test/java/com/bidnow/wallet/kafka/WalletEventPublisherTest.java`

**Interfaces:**
- Consumes (Task 1): `DepositRefundedApplicationEvent` getters (`getUserId`, `getWalletId`, `getAuctionId`, `getAmount`, `getReason`, `getRefundedAt`) and `RefundReason`.
- Produces:
  - `DepositRefundedEvent` (Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`), with fields `UUID userId, UUID walletId, UUID auctionId, BigDecimal amount, String reason, Instant refundedAt`
  - `WalletEventPublisher.onDepositRefunded(DepositRefundedApplicationEvent)`, which sends to `deposit-refunded-topic` keyed by `userId.toString()`

- [ ] **Step 1: Create the shared DTO**

`backend/common/src/main/java/com/bidnow/common/dto/event/DepositRefundedEvent.java`:

```java
package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRefundedEvent {
    private UUID userId;
    private UUID walletId;
    private UUID auctionId;
    private BigDecimal amount;
    private String reason; // AUCTION_LOST | AUCTION_CANCELLED
    private Instant refundedAt;
}
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/bidnow/wallet/kafka/WalletEventPublisherTest.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.wallet.domain.enums.RefundReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private WalletEventPublisher publisher;

    @Test
    void onDepositRefunded_sendsEventKeyedByUserId() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID auctionId = UUID.randomUUID();
        Instant refundedAt = Instant.parse("2026-09-28T10:00:00Z");
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);

        publisher.onDepositRefunded(new DepositRefundedApplicationEvent(this, userId, walletId, auctionId,
                new BigDecimal("50.00"), RefundReason.AUCTION_CANCELLED, refundedAt));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("deposit-refunded-topic"), eq(userId.toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(DepositRefundedEvent.class);
        DepositRefundedEvent event = (DepositRefundedEvent) payload.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getWalletId()).isEqualTo(walletId);
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getReason()).isEqualTo("AUCTION_CANCELLED");
        assertThat(event.getRefundedAt()).isEqualTo(refundedAt);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletEventPublisherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `onDepositRefunded` doesn't exist yet.

- [ ] **Step 4: Implement**

In `src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java`:

(a) Add the import `com.bidnow.common.dto.event.DepositRefundedEvent`.

(b) Add this constant under `DEPOSIT_RECEIVED_TOPIC`:

```java
    private static final String DEPOSIT_REFUNDED_TOPIC = "deposit-refunded-topic";
```

(c) Add this method after `onDepositCompleted`:

```java
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepositRefunded(DepositRefundedApplicationEvent event) {
        DepositRefundedEvent kafkaEvent = DepositRefundedEvent.builder()
                .userId(event.getUserId())
                .walletId(event.getWalletId())
                .auctionId(event.getAuctionId())
                .amount(event.getAmount())
                .reason(event.getReason().name())
                .refundedAt(event.getRefundedAt())
                .build();
        kafkaTemplate.send(DEPOSIT_REFUNDED_TOPIC, event.getUserId().toString(), kafkaEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish DepositRefundedEvent for userId={}, auctionId={}",
                                event.getUserId(), event.getAuctionId(), ex);
                    } else {
                        log.info("Published DepositRefundedEvent for userId={}, auctionId={}",
                                event.getUserId(), event.getAuctionId());
                    }
                });
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletEventPublisherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (1 test).

---

### Task 3: `AuctionLifecycleEventConsumer` — refund sweep on end and cancel

**Files:**
- Create: `src/main/java/com/bidnow/wallet/exception/DepositReleaseException.java`
- Create: `src/main/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumer.java`
- Test: `src/test/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumerTest.java`

**Interfaces:**
- Consumes (Task 1): `DepositLockRepository.findByAuctionIdAndStatus(UUID, DepositLockStatus)`, `DepositLockService.releaseDeposit(UUID lockId, UUID walletId, RefundReason)`, and `RefundReason`. Also existing: `WalletRepository.findByUserId(UUID)`, and from common, `AuctionEndedEvent` (`getAuctionId()`, `getWinnerId()`, `getLoserIds()`) and `AuctionCancelledEvent` (`getAuctionId()`). Both are Lombok `@Builder`.
- Produces:
  - `DepositReleaseException(UUID auctionId, List<UUID> failedLockIds)` extends `RuntimeException`, with getters `getAuctionId()` and `getFailedLockIds()`
  - `AuctionLifecycleEventConsumer.onAuctionEnded(AuctionEndedEvent)` and `.onAuctionCancelled(AuctionCancelledEvent)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumerTest.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.exception.DepositReleaseException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.DepositLockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleEventConsumerTest {

    @Mock
    private DepositLockRepository depositLockRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DepositLockService depositLockService;

    @InjectMocks
    private AuctionLifecycleEventConsumer consumer;

    private final UUID auctionId = UUID.randomUUID();
    private final UUID winnerUserId = UUID.randomUUID();
    private final UUID winnerWalletId = UUID.randomUUID();
    private final UUID walletA = UUID.randomUUID();
    private final UUID walletB = UUID.randomUUID();

    private DepositLock lockFor(UUID walletId) {
        return DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .auctionId(auctionId)
                .amount(new BigDecimal("50.00"))
                .status(DepositLockStatus.LOCKED)
                .build();
    }

    private AuctionEndedEvent ended(UUID winnerId, List<UUID> loserIds) {
        return AuctionEndedEvent.builder().auctionId(auctionId).winnerId(winnerId).loserIds(loserIds).build();
    }

    @Test
    void onAuctionEnded_withWinner_releasesLosersOnly() {
        DepositLock winnerLock = lockFor(winnerWalletId);
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(winnerLock, lockA, lockB));
        when(walletRepository.findByUserId(winnerUserId))
                .thenReturn(Optional.of(Wallet.builder().id(winnerWalletId).userId(winnerUserId).build()));

        consumer.onAuctionEnded(ended(winnerUserId, List.of()));

        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_LOST);
        verify(depositLockService, never()).releaseDeposit(eq(winnerLock.getId()), any(), any());
        verify(depositLockService, times(2)).releaseDeposit(any(), any(), any());
    }

    @Test
    void onAuctionEnded_noWinner_releasesAllLocks() {
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA, lockB));

        consumer.onAuctionEnded(ended(null, null));

        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_LOST);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void onAuctionEnded_ignoresLoserIdsAndUsesDepositLocks() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));

        consumer.onAuctionEnded(ended(null, List.of(UUID.randomUUID(), UUID.randomUUID())));

        verify(depositLockService, times(1)).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
    }

    @Test
    void onAuctionEnded_noLocks_doesNothing() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionEnded(ended(winnerUserId, List.of()));

        verifyNoInteractions(depositLockService);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void onAuctionCancelled_releasesAllLocksWithCancelledReason() {
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA, lockB));

        consumer.onAuctionCancelled(AuctionCancelledEvent.builder().auctionId(auctionId).build());

        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_CANCELLED);
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_CANCELLED);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void oneReleaseFails_othersStillReleased_thenThrowsWithFailedLockIds() {
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA, lockB));
        doThrow(new RuntimeException("db down"))
                .when(depositLockService).releaseDeposit(eq(lockA.getId()), any(), any());

        assertThatThrownBy(() -> consumer.onAuctionCancelled(
                AuctionCancelledEvent.builder().auctionId(auctionId).build()))
                .isInstanceOfSatisfying(DepositReleaseException.class, ex -> {
                    assertThat(ex.getAuctionId()).isEqualTo(auctionId);
                    assertThat(ex.getFailedLockIds()).containsExactly(lockA.getId());
                });
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_CANCELLED);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=AuctionLifecycleEventConsumerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `AuctionLifecycleEventConsumer` and `DepositReleaseException` don't exist yet.

- [ ] **Step 3: Implement the exception**

`src/main/java/com/bidnow/wallet/exception/DepositReleaseException.java`:

```java
package com.bidnow.wallet.exception;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** Thrown after a refund sweep in which some locks failed, so the Kafka error handler redelivers the record. */
@Getter
public class DepositReleaseException extends RuntimeException {

    private final UUID auctionId;
    private final List<UUID> failedLockIds;

    public DepositReleaseException(UUID auctionId, List<UUID> failedLockIds) {
        super("Failed to release " + failedLockIds.size() + " deposit lock(s) for auction "
                + auctionId + ": " + failedLockIds);
        this.auctionId = auctionId;
        this.failedLockIds = List.copyOf(failedLockIds);
    }
}
```

- [ ] **Step 4: Implement the consumer**

`src/main/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumer.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.exception.DepositReleaseException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.DepositLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Releases deposit locks when an auction ends (all but the winner) or is cancelled (everyone).
 * Deliberately not transactional: each lock is released in its own transaction by
 * {@link DepositLockService#releaseDeposit}, so one failure never rolls back the others.
 * Losers are derived from deposit_locks; AuctionEndedEvent.loserIds is not used.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleEventConsumer {

    private final DepositLockRepository depositLockRepository;
    private final WalletRepository walletRepository;
    private final DepositLockService depositLockService;

    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionEnded(AuctionEndedEvent event) {
        log.info("Received AuctionEndedEvent for auctionId={}, winnerId={}", event.getAuctionId(), event.getWinnerId());
        releaseAll(event.getAuctionId(), event.getWinnerId(), RefundReason.AUCTION_LOST);
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionCancelled(AuctionCancelledEvent event) {
        log.info("Received AuctionCancelledEvent for auctionId={}", event.getAuctionId());
        releaseAll(event.getAuctionId(), null, RefundReason.AUCTION_CANCELLED);
    }

    private void releaseAll(UUID auctionId, UUID excludeUserId, RefundReason reason) {
        List<DepositLock> locks = depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED);
        if (locks.isEmpty()) {
            log.debug("No LOCKED deposits for auctionId={}", auctionId);
            return;
        }

        UUID excludedWalletId = excludeUserId == null ? null
                : walletRepository.findByUserId(excludeUserId).map(Wallet::getId).orElse(null);

        List<UUID> failedLockIds = new ArrayList<>();
        for (DepositLock lock : locks) {
            if (Objects.equals(lock.getWalletId(), excludedWalletId)) {
                continue;
            }
            try {
                depositLockService.releaseDeposit(lock.getId(), lock.getWalletId(), reason);
            } catch (RuntimeException ex) {
                log.error("Failed to release deposit lockId={}, walletId={}, auctionId={}",
                        lock.getId(), lock.getWalletId(), auctionId, ex);
                failedLockIds.add(lock.getId());
            }
        }

        if (!failedLockIds.isEmpty()) {
            throw new DepositReleaseException(auctionId, failedLockIds);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=AuctionLifecycleEventConsumerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (6 tests).

---

### Task 4: Kafka error handling — backoff, dead-letter topic, and ErrorHandlingDeserializer

**Files:**
- Create: `src/main/java/com/bidnow/wallet/config/KafkaConsumerConfig.java`
- Modify: `src/main/resources/application.yml` (the `spring.kafka.consumer` block)
- Test: `src/test/java/com/bidnow/wallet/config/KafkaConsumerConfigTest.java`

**Interfaces:**
- Consumes: Spring Boot's auto-configured `KafkaTemplate<String, Object>`, which `WalletEventPublisher` already injects.
- Produces:
  - A `DefaultErrorHandler` bean named `kafkaErrorHandler`. Spring Boot applies a `CommonErrorHandler` bean to the auto-configured listener container factory.
  - The package-private `static ExponentialBackOffWithMaxRetries retryBackOff()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bidnow/wallet/config/KafkaConsumerConfigTest.java`:

```java
package com.bidnow.wallet.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    void retryBackOff_isThreeRetriesStartingAtOneSecondDoubling() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerConfig.retryBackOff();

        assertThat(backOff.getMaxRetries()).isEqualTo(3);
        assertThat(backOff.getInitialInterval()).isEqualTo(1_000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaErrorHandler_isBuilt() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(template);

        assertThat(handler).isNotNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=KafkaConsumerConfigTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `KafkaConsumerConfig` doesn't exist yet.

- [ ] **Step 3: Implement the config**

`src/main/java/com/bidnow/wallet/config/KafkaConsumerConfig.java`:

```java
package com.bidnow.wallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retries failed records with exponential backoff (1s, 2s, 4s), then publishes them to
 * {@code <topic>.DLT}. Spring Boot applies this CommonErrorHandler to every wallet listener.
 */
@Configuration
public class KafkaConsumerConfig {

    static final int MAX_RETRIES = 3;
    static final long INITIAL_INTERVAL_MS = 1_000L;
    static final double MULTIPLIER = 2.0;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), retryBackOff());
    }

    static ExponentialBackOffWithMaxRetries retryBackOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        return backOff;
    }
}
```

- [ ] **Step 4: Switch to ErrorHandlingDeserializer**

In `src/main/resources/application.yml`, replace the `value-deserializer` line and the `properties` block under `spring.kafka.consumer`. The current text is:

```yaml
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
        reconnect.backoff.ms: 5000
        reconnect.backoff.max.ms: 30000
```

Replace it with:

```yaml
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "*"
        reconnect.backoff.ms: 5000
        reconnect.backoff.max.ms: 30000
```

Leave the indentation and every other key unchanged.

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl wallet-service -am test -Dtest=KafkaConsumerConfigTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests).

---

### Task 5: Documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/epics/wallet/epic.md`

**Interfaces:** none. This task is docs only.

- [ ] **Step 1: Architecture — wallet events**

In `docs/architecture.md`, find the `### Service-to-Service Internal APIs` section (around line 63). Its table ends with the `GET /api/v1/internal/wallet/balance/{userId}` row. Insert this new section after that table and before the `---` that follows it:

```markdown

### Wallet Events (Kafka)

| Direction | Topic | Payload | Purpose |
|---|---|---|---|
| Consumes | `auction-ended-topic` | `AuctionEndedEvent` | Refund every `LOCKED` deposit for the auction except the winner's (`AUCTION_LOST`). Losers are derived from `deposit_locks`; `loserIds` is not used. |
| Consumes | `auction-cancelled-topic` | `AuctionCancelledEvent` | Refund every `LOCKED` deposit for the auction (`AUCTION_CANCELLED`). |
| Produces | `deposit-refunded-topic` (key `userId`) | `DepositRefundedEvent { userId, walletId, auctionId, amount, reason, refundedAt }` | One per non-zero refund, published after commit. |

Each refund is its own DB transaction. Wallet listeners retry failed records 3 times with exponential backoff (1s, 2s, 4s), then publish them to `<topic>.DLT`. Undeserializable records go straight to `<topic>.DLT` via `ErrorHandlingDeserializer`. Redelivery is safe because released locks are skipped.
```

- [ ] **Step 2: Wallet epic — refund logic**

In `docs/epics/wallet/epic.md`, replace the whole `**Refund Logic (Losing Bidders):**` block (around lines 109-119, up to and including the `- [ ] Notification to bidder: "Your deposit has been refunded"` line) with:

```markdown
**Refund Logic (Losing Bidders & Cancelled Auctions) — WALLET-304:**

- [ ] Wallet consumes `auction-ended-topic` (`AuctionEndedEvent`) and `auction-cancelled-topic` (`AuctionCancelledEvent`)
- [ ] Refund targets are derived from `deposit_locks` (`status = LOCKED` for the auction): on end, everyone except the winner; on cancel, everyone. `AuctionEndedEvent.loserIds` is not used (it is empty until the bidding-service exists)
- [ ] For each lock, in its own DB transaction (wallet row locked first):
  - `available_balance += amount`, `locked_balance -= amount` (total unchanged)
  - Create REFUND transaction (before/after balances, `reference_id = auctionId`)
  - Update `deposit_locks.status = RELEASED`, `released_at = now`
  - After commit, emit `DepositRefundedEvent` on `deposit-refunded-topic` (reason `AUCTION_LOST` / `AUCTION_CANCELLED`)
- [ ] Zero-amount locks are released without a transaction or event
- [ ] Failures: other locks still refunded; record retried (1s/2s/4s) then sent to `<topic>.DLT`; replay is idempotent
- [ ] Notification to bidder: "Your deposit has been refunded" (media-service consumes `deposit-refunded-topic` — later story)
```

---

### Task 6: Full verification

**Files:** none modified.

- [ ] **Step 1: Run the full wallet-service suite**

Run (from `backend/`): `mvn -pl wallet-service -am clean test`
Expected: BUILD SUCCESS with 0 failures. The new tests are `DepositLockServiceImplTest` (+8), `WalletEventPublisherTest` (1), `AuctionLifecycleEventConsumerTest` (6) and `KafkaConsumerConfigTest` (2).

- [ ] **Step 2: Manual smoke test (only if Docker, Kafka and Postgres are available)**

With the stack running (see `backend/README.md`), for an auction that has at least two LOCKED deposit locks:

1. Publish an `AuctionEndedEvent` JSON to `auction-ended-topic`, naming one bidder as `winnerId`. Expected: the other bidders' locks become `RELEASED`, their balances return, and a `deposit-refunded-topic` message appears per loser. The winner's lock stays `LOCKED`.
2. Publish the same event again. Expected: no balance changes and no new REFUND rows.
3. Publish a malformed (non-JSON) record to `auction-ended-topic`. Expected: it appears on `auction-ended-topic.DLT`, and the consumer keeps processing.

If no stack is available, skip this step and report it as not run.
