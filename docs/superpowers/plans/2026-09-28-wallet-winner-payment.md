# WALLET-305 Winner Payment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When an auction ends with a winner, create a 48-hour payment hold that credits the winner's deposit toward the price. Let the winner list and confirm pending payments; confirming debits the winner and credits the seller atomically. Void the hold if the auction is cancelled.

**Architecture:** A new `payment_holds` table holds the per-auction payment state. The `transactions` table stays an append-only ledger. `PaymentService` implements four operations: `createPaymentHold`, `confirmPayment`, `cancelPaymentHold` and `getPendingPayments`. Each one that moves money is a single DB transaction that locks the hold row first, then the wallet row(s) in ascending id order. `AuctionLifecycleEventConsumer` (from WALLET-304) calls create on auction end and cancel on auction cancel. Those calls are isolated from the deposit refunds, and the consumer rethrows the first failure. `PaymentEvent` (REQUIRED / COMPLETED) goes out on `payment-event-topic` after commit. The public endpoints live under `/api/v1/wallets/payments`.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data JPA (Hibernate), Liquibase, PostgreSQL, Spring Kafka, Lombok, JUnit 5, Mockito, AssertJ, standalone MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-28-wallet-winner-payment-design.md`

## Global Constraints

- **Do not run `git add`, `git commit`, `git stash`, `git reset`, or any other git write.** Leave all changes uncommitted on branch `feature/wallet-deposit-lock`.
- Scope: wallet-service, plus new optional fields on `common`'s `PaymentEvent`. Do not change auction-service or media-service.
- Endpoints: `GET /api/v1/wallets/payments/pending` and `POST /api/v1/wallets/payments/confirm` with body `{ auctionId }`. The caller comes from `@AuthenticatedUserId`, and responses are `ResponseEntity<BaseResponse<T>>`.
- Hold states: `PENDING_PAYMENT`, `COMPLETED`, `CANCELLED`. There is one hold per auction (`UNIQUE auction_id`).
- The deposit is credited only from a `LOCKED` lock on the winner's wallet for that auction; otherwise it is 0. `deposit_applied = min(deposit, total)` and `remaining = total − deposit_applied`.
- Deadline: `now + wallet.payment.deadline-hours`, default 48.
- **Lock order:** in confirm and cancel, lock the `payment_holds` row first (`findByAuctionIdForUpdate`), then the wallet(s) via `findByIdForUpdate`. When locking two wallets, take the lower `UUID` (by `compareTo`) first. In create, the winner wallet (`findByUserIdForUpdate`) is the first read.
- Balance invariant: `total = available + locked` in every wallet. On confirm, the winner's total decreases by exactly `total_amount` and the seller's increases by exactly `total_amount`. There are no fees.
- Error codes: `PAYMENT_HOLD_NOT_FOUND` (404), `PAYMENT_NOT_PENDING` (409), `PAYMENT_DEADLINE_EXPIRED` (400), `SELLER_WALLET_NOT_FOUND` (404). Also reused: `INSUFFICIENT_BALANCE` (400, `errors{availableBalance, required}` via the existing `WalletExceptionHandler`), `DEPOSIT_LOCK_CLOSED` (409) and `WALLET_NOT_FOUND` (404).
- Events: `PaymentEvent` on `payment-event-topic`, keyed by the winner's `userId`, sent after commit. `paymentType` is `REQUIRED` (hold created) or `COMPLETED` (confirmed). There is no event on cancel.
- Consumer failure handling: run each step, collect the `RuntimeException`s, and rethrow the **first** one after all steps have run. (This refines the spec's `PaymentHoldCreationException` into "rethrow first failure", so no new exception class is needed.)
- Run Maven from `backend/`: `mvn -q -pl wallet-service -am test …`.

---

## File Map

Paths are relative to `backend/wallet-service/` unless they start with `backend/common/` or `docs/`.

| File | Action | Task |
|---|---|---|
| `src/main/resources/db/changelog/migrations/04-init-payment-holds.sql` | Create | 1 |
| `src/main/resources/db/changelog/db.changelog-master.xml` | Modify | 1 |
| `src/main/resources/application.yml` | Modify (`wallet.payment.deadline-hours`) | 1 |
| `src/main/java/com/bidnow/wallet/domain/enums/PaymentHoldStatus.java` | Create | 1 |
| `src/main/java/com/bidnow/wallet/domain/entity/PaymentHold.java` | Create | 1 |
| `src/main/java/com/bidnow/wallet/repository/PaymentHoldRepository.java` | Create | 1 |
| `src/main/java/com/bidnow/wallet/repository/WalletRepository.java` | Modify (+`findIdByUserId`) | 1 |
| `src/main/java/com/bidnow/wallet/constant/WalletErrorCodes.java` | Modify | 1 |
| `backend/common/src/main/java/com/bidnow/common/dto/event/PaymentEvent.java` | Modify | 2 |
| `src/main/java/com/bidnow/wallet/kafka/PaymentApplicationEvent.java` | Create | 2 |
| `src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java` | Modify | 2 |
| `src/test/java/com/bidnow/wallet/kafka/WalletEventPublisherTest.java` | Modify | 2 |
| `src/main/java/com/bidnow/wallet/dto/response/PendingPaymentResponse.java` | Create | 3 |
| `src/main/java/com/bidnow/wallet/service/PaymentService.java` | Create | 3 (+4) |
| `src/main/java/com/bidnow/wallet/service/impl/PaymentServiceImpl.java` | Create | 3 (+4) |
| `src/test/java/com/bidnow/wallet/service/impl/PaymentServiceImplTest.java` | Create | 3 (+4) |
| `src/main/java/com/bidnow/wallet/dto/request/ConfirmPaymentRequest.java` | Create | 4 |
| `src/main/java/com/bidnow/wallet/dto/response/ConfirmPaymentResponse.java` | Create | 4 |
| `src/main/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumer.java` | Modify | 5 |
| `src/test/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumerTest.java` | Modify | 5 |
| `src/main/java/com/bidnow/wallet/controller/PaymentController.java` | Create | 6 |
| `src/test/java/com/bidnow/wallet/controller/PaymentControllerTest.java` | Create | 6 |
| `docs/architecture.md`, `docs/epics/wallet/epic.md` | Modify | 7 |

---

### Task 1: Persistence — `payment_holds`, repositories, error codes, config

**Files:** migration 04, the changelog master, `application.yml`, `PaymentHoldStatus`, `PaymentHold`, `PaymentHoldRepository`, `WalletRepository`, `WalletErrorCodes` (see the File Map).

**Interfaces:**
- Produces:
  - `enum PaymentHoldStatus { PENDING_PAYMENT, COMPLETED, CANCELLED }`
  - `PaymentHold` (Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, extends `BaseEntity`), with fields `UUID id, UUID auctionId, UUID winnerWalletId, UUID winnerUserId, UUID sellerUserId, BigDecimal totalAmount, BigDecimal depositApplied, UUID depositLockId, BigDecimal remainingAmount, boolean fundsHeld (getter isFundsHeld()), PaymentHoldStatus status, LocalDateTime deadline, LocalDateTime completedAt`
  - `PaymentHoldRepository`:
    - `boolean existsByAuctionId(UUID)`
    - `Optional<PaymentHold> findByAuctionIdForUpdate(UUID)` (PESSIMISTIC_WRITE)
    - `List<PaymentHold> findByWinnerUserIdAndStatusOrderByDeadlineAsc(UUID, PaymentHoldStatus)`
  - `WalletRepository.findIdByUserId(UUID): Optional<UUID>` (a projection, which does not load the entity)
  - `WalletErrorCodes`: `PAYMENT_HOLD_NOT_FOUND`, `PAYMENT_NOT_PENDING`, `PAYMENT_DEADLINE_EXPIRED`, `SELLER_WALLET_NOT_FOUND`
  - The config key `wallet.payment.deadline-hours: 48`

This task has no business logic, so verification is a compile plus a green run of the existing suite. Tasks 3–6 exercise the mapping.

- [ ] **Step 1: Create the migration**

`src/main/resources/db/changelog/migrations/04-init-payment-holds.sql`:

```sql
-- liquibase formatted sql

-- changeset bidnow:wallet_004
CREATE TABLE payment_holds
(
    id               UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    auction_id       UUID           NOT NULL,
    winner_wallet_id UUID           NOT NULL REFERENCES wallets (id),
    winner_user_id   UUID           NOT NULL,
    seller_user_id   UUID           NOT NULL,
    total_amount     NUMERIC(19, 4) NOT NULL,
    deposit_applied  NUMERIC(19, 4) NOT NULL,
    deposit_lock_id  UUID REFERENCES deposit_locks (id),
    remaining_amount NUMERIC(19, 4) NOT NULL,
    funds_held       BOOLEAN        NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    deadline         TIMESTAMP      NOT NULL,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_holds_auction UNIQUE (auction_id),
    CONSTRAINT chk_payment_holds_amounts
        CHECK (total_amount >= 0 AND deposit_applied >= 0 AND remaining_amount >= 0
            AND deposit_applied + remaining_amount = total_amount)
);

CREATE INDEX idx_payment_holds_winner_status ON payment_holds (winner_user_id, status);
CREATE INDEX idx_payment_holds_status_deadline ON payment_holds (status, deadline);
```

- [ ] **Step 2: Register the migration and add the config key**

In `src/main/resources/db/changelog/db.changelog-master.xml`, add this after the `03-init-deposit-locks.sql` include:

```xml
    <include file="db/changelog/migrations/04-init-payment-holds.sql"/>
```

In `src/main/resources/application.yml`, the `wallet:` block currently reads:

```yaml
wallet:
  platform-user-id: 87754524-54c7-4aa4-b6fb-43d359c2121f
```

Replace it with:

```yaml
wallet:
  platform-user-id: 87754524-54c7-4aa4-b6fb-43d359c2121f
  payment:
    deadline-hours: 48
```

- [ ] **Step 3: Create the enum and the entity**

`src/main/java/com/bidnow/wallet/domain/enums/PaymentHoldStatus.java`:

```java
package com.bidnow.wallet.domain.enums;

public enum PaymentHoldStatus {
    PENDING_PAYMENT, COMPLETED, CANCELLED
}
```

`src/main/java/com/bidnow/wallet/domain/entity/PaymentHold.java`:

```java
package com.bidnow.wallet.domain.entity;

import com.bidnow.common.entity.BaseEntity;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_holds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHold extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auction_id", nullable = false, unique = true)
    private UUID auctionId;

    @Column(name = "winner_wallet_id", nullable = false)
    private UUID winnerWalletId;

    @Column(name = "winner_user_id", nullable = false)
    private UUID winnerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "deposit_applied", nullable = false, precision = 19, scale = 4)
    private BigDecimal depositApplied;

    @Column(name = "deposit_lock_id")
    private UUID depositLockId;

    @Column(name = "remaining_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingAmount;

    @Column(name = "funds_held", nullable = false)
    private boolean fundsHeld;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentHoldStatus status;

    @Column(name = "deadline", nullable = false)
    private LocalDateTime deadline;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
```

- [ ] **Step 4: Create and extend the repositories**

`src/main/java/com/bidnow/wallet/repository/PaymentHoldRepository.java`:

```java
package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentHoldRepository extends JpaRepository<PaymentHold, UUID> {

    boolean existsByAuctionId(UUID auctionId);

    /** SELECT ... FOR UPDATE on the hold row. Lock order: hold row first, then wallet rows. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM PaymentHold h WHERE h.auctionId = :auctionId")
    Optional<PaymentHold> findByAuctionIdForUpdate(@Param("auctionId") UUID auctionId);

    List<PaymentHold> findByWinnerUserIdAndStatusOrderByDeadlineAsc(UUID winnerUserId, PaymentHoldStatus status);
}
```

In `src/main/java/com/bidnow/wallet/repository/WalletRepository.java`, add this after `findByIdForUpdate`:

```java
    /** Wallet id only — does not load the entity, so it never pollutes the persistence context before a row lock. */
    @Query("SELECT w.id FROM Wallet w WHERE w.userId = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);
```

- [ ] **Step 5: Add the error codes**

In `src/main/java/com/bidnow/wallet/constant/WalletErrorCodes.java`, add these after `DEPOSIT_LOCK_CLOSED`:

```java
    public static final String PAYMENT_HOLD_NOT_FOUND = "PAYMENT_HOLD_NOT_FOUND";
    public static final String PAYMENT_NOT_PENDING = "PAYMENT_NOT_PENDING";
    public static final String PAYMENT_DEADLINE_EXPIRED = "PAYMENT_DEADLINE_EXPIRED";
    public static final String SELLER_WALLET_NOT_FOUND = "SELLER_WALLET_NOT_FOUND";
```

- [ ] **Step 6: Compile and run the existing suite**

Run (from `backend/`): `mvn -q -pl wallet-service -am test`
Expected: BUILD SUCCESS, with all 69 existing tests passing.

---

### Task 2: Payment events published after commit

**Files:** `PaymentEvent` (common), `PaymentApplicationEvent`, `WalletEventPublisher` and `WalletEventPublisherTest` (see the File Map).

**Interfaces:**
- Produces:
  - `PaymentEvent` with new optional fields `UUID sellerId, BigDecimal depositAmount, BigDecimal remaining, Instant deadline, Boolean insufficientFunds`. The existing fields are unchanged.
  - `PaymentApplicationEvent(Object source, PaymentEvent payment)`, with `getPayment()`
  - `WalletEventPublisher.onPaymentEvent(PaymentApplicationEvent)`, which is AFTER_COMMIT and sends to `payment-event-topic` keyed by `payment.getUserId().toString()`

- [ ] **Step 1: Extend `PaymentEvent`**

Replace the class body of `backend/common/src/main/java/com/bidnow/common/dto/event/PaymentEvent.java` so the file reads:

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
public class PaymentEvent {
    private UUID auctionId;
    private String auctionTitle;
    private UUID userId;
    private BigDecimal amount;
    private String paymentType; // e.g. REQUIRED, REMINDER_24H, COMPLETED, REFUNDED

    // Optional — populated by wallet-service for REQUIRED / COMPLETED
    private UUID sellerId;
    private BigDecimal depositAmount;
    private BigDecimal remaining;
    private Instant deadline;
    private Boolean insufficientFunds;
}
```

- [ ] **Step 2: Create the application event**

`src/main/java/com/bidnow/wallet/kafka/PaymentApplicationEvent.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.PaymentEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** In-process carrier for a PaymentEvent, published to Kafka only after the DB transaction commits. */
@Getter
public class PaymentApplicationEvent extends ApplicationEvent {

    private final PaymentEvent payment;

    public PaymentApplicationEvent(Object source, PaymentEvent payment) {
        super(source);
        this.payment = payment;
    }
}
```

- [ ] **Step 3: Write the failing test**

In `src/test/java/com/bidnow/wallet/kafka/WalletEventPublisherTest.java`, add the import `com.bidnow.common.dto.event.PaymentEvent`, and append this test before the final closing brace:

```java
    @Test
    void onPaymentEvent_sendsPayloadToPaymentTopicKeyedByWinner() {
        UUID winnerId = UUID.randomUUID();
        PaymentEvent payment = PaymentEvent.builder()
                .auctionId(UUID.randomUUID())
                .userId(winnerId)
                .sellerId(UUID.randomUUID())
                .amount(new BigDecimal("500.00"))
                .depositAmount(new BigDecimal("50.00"))
                .remaining(new BigDecimal("450.00"))
                .deadline(Instant.parse("2026-09-30T10:00:00Z"))
                .insufficientFunds(false)
                .paymentType("REQUIRED")
                .build();
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);

        publisher.onPaymentEvent(new PaymentApplicationEvent(this, payment));

        verify(kafkaTemplate).send(eq("payment-event-topic"), eq(winnerId.toString()), eq(payment));
    }
```

- [ ] **Step 4: Run the test to verify it fails**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=WalletEventPublisherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `onPaymentEvent` doesn't exist yet.

- [ ] **Step 5: Implement the publisher method**

In `src/main/java/com/bidnow/wallet/kafka/WalletEventPublisher.java`:
- add the import `com.bidnow.common.dto.event.PaymentEvent`
- add the constant `private static final String PAYMENT_EVENT_TOPIC = "payment-event-topic";` below the other topic constants
- add this method after `onDepositRefunded`:

```java
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentEvent(PaymentApplicationEvent event) {
        PaymentEvent payment = event.getPayment();
        kafkaTemplate.send(PAYMENT_EVENT_TOPIC, payment.getUserId().toString(), payment)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentEvent type={} for auctionId={}",
                                payment.getPaymentType(), payment.getAuctionId(), ex);
                    } else {
                        log.info("Published PaymentEvent type={} for auctionId={}",
                                payment.getPaymentType(), payment.getAuctionId());
                    }
                });
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl wallet-service -am test -Dtest=WalletEventPublisherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests).

---

### Task 3: `PaymentService` — create hold, cancel hold, list pending

**Files:** `PendingPaymentResponse`, `PaymentService`, `PaymentServiceImpl`, `PaymentServiceImplTest` (see the File Map).

**Interfaces:**
- Consumes:
  - Task 1: `PaymentHold`, `PaymentHoldStatus`, `PaymentHoldRepository` and its methods, `WalletErrorCodes.WALLET_NOT_FOUND`.
  - Task 2: `PaymentApplicationEvent`, `PaymentEvent` builder.
  - Existing: `WalletRepository.findByUserIdForUpdate`, `WalletRepository.findByIdForUpdate`, `DepositLockRepository.findByWalletIdAndAuctionId`, `DepositLockStatus`, `Transaction`, `TransactionType.HOLD`/`HOLD_CANCEL`, `TransactionStatus.COMPLETED`, `TransactionRepository`, `NotFoundException(String, String)`.
- Produces:
  - `PendingPaymentResponse` (`@Data @Builder`), with fields `UUID auctionId, BigDecimal totalAmount, BigDecimal depositApplied, BigDecimal remaining, boolean fundsHeld, LocalDateTime deadline, long hoursLeft, boolean expired`
  - `PaymentService`:
    - `void createPaymentHold(UUID auctionId, UUID winnerUserId, UUID sellerUserId, BigDecimal totalAmount)`
    - `void cancelPaymentHold(UUID auctionId)`
    - `List<PendingPaymentResponse> getPendingPayments(UUID userId)`
  - `PaymentServiceImpl` (`@Service @RequiredArgsConstructor`), with the final fields paymentHoldRepository, walletRepository, depositLockRepository, transactionRepository and eventPublisher, and the non-final field `@Value("${wallet.payment.deadline-hours:48}") private long deadlineHours = 48;`

- [ ] **Step 1: Create the DTO and the interface**

`src/main/java/com/bidnow/wallet/dto/response/PendingPaymentResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PendingPaymentResponse {
    private UUID auctionId;
    private BigDecimal totalAmount;
    private BigDecimal depositApplied;
    private BigDecimal remaining;
    private boolean fundsHeld;
    private LocalDateTime deadline;
    private long hoursLeft;
    private boolean expired;
}
```

`src/main/java/com/bidnow/wallet/service/PaymentService.java`:

```java
package com.bidnow.wallet.service;

import com.bidnow.wallet.dto.response.PendingPaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    void createPaymentHold(UUID auctionId, UUID winnerUserId, UUID sellerUserId, BigDecimal totalAmount);

    void cancelPaymentHold(UUID auctionId);

    List<PendingPaymentResponse> getPendingPayments(UUID userId);
}
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/bidnow/wallet/service/impl/PaymentServiceImplTest.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.kafka.PaymentApplicationEvent;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentHoldRepository paymentHoldRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DepositLockRepository depositLockRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID auctionId;
    private UUID winnerUserId;
    private UUID sellerUserId;
    private UUID winnerWalletId;
    private UUID sellerWalletId;

    @BeforeEach
    void setUp() {
        auctionId = UUID.randomUUID();
        winnerUserId = UUID.randomUUID();
        sellerUserId = UUID.randomUUID();
        winnerWalletId = UUID.randomUUID();
        sellerWalletId = UUID.randomUUID();
    }

    private Wallet wallet(UUID id, UUID userId, String total, String available, String locked) {
        return Wallet.builder()
                .id(id)
                .userId(userId)
                .totalBalance(new BigDecimal(total))
                .availableBalance(new BigDecimal(available))
                .lockedBalance(new BigDecimal(locked))
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
    }

    private DepositLock depositLock(String amount, DepositLockStatus status) {
        return DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(winnerWalletId)
                .auctionId(auctionId)
                .amount(new BigDecimal(amount))
                .status(status)
                .build();
    }

    private PaymentHold hold(String total, String applied, String remaining, boolean fundsHeld,
                             UUID depositLockId, LocalDateTime deadline, PaymentHoldStatus status) {
        return PaymentHold.builder()
                .id(UUID.randomUUID())
                .auctionId(auctionId)
                .winnerWalletId(winnerWalletId)
                .winnerUserId(winnerUserId)
                .sellerUserId(sellerUserId)
                .totalAmount(new BigDecimal(total))
                .depositApplied(new BigDecimal(applied))
                .depositLockId(depositLockId)
                .remainingAmount(new BigDecimal(remaining))
                .fundsHeld(fundsHeld)
                .status(status)
                .deadline(deadline)
                .build();
    }

    private PaymentHold capturedHold() {
        ArgumentCaptor<PaymentHold> captor = ArgumentCaptor.forClass(PaymentHold.class);
        verify(paymentHoldRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private PaymentEvent capturedEvent() {
        ArgumentCaptor<PaymentApplicationEvent> captor = ArgumentCaptor.forClass(PaymentApplicationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue().getPayment();
    }

    // ── createPaymentHold ─────────────────────────────────────────────────────

    @Test
    void createPaymentHold_sufficientBalance_holdsRemainingAndRecordsHold() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "600.00", "50.00");
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId)).thenReturn(Optional.of(lock));
        LocalDateTime before = LocalDateTime.now();

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("500.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("650.00");
        verify(walletRepository).save(winner);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(TransactionType.HOLD);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getWalletId()).isEqualTo(winnerWalletId);
        assertThat(tx.getAmount()).isEqualByComparingTo("450.00");
        assertThat(tx.getAvailableBalanceBefore()).isEqualByComparingTo("600.00");
        assertThat(tx.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(tx.getReferenceId()).isEqualTo(auctionId);

        PaymentHold saved = capturedHold();
        assertThat(saved.getAuctionId()).isEqualTo(auctionId);
        assertThat(saved.getWinnerWalletId()).isEqualTo(winnerWalletId);
        assertThat(saved.getWinnerUserId()).isEqualTo(winnerUserId);
        assertThat(saved.getSellerUserId()).isEqualTo(sellerUserId);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(saved.getDepositApplied()).isEqualByComparingTo("50.00");
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("450.00");
        assertThat(saved.getDepositLockId()).isEqualTo(lock.getId());
        assertThat(saved.isFundsHeld()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(PaymentHoldStatus.PENDING_PAYMENT);
        assertThat(saved.getDeadline()).isBetween(before.plusHours(48).minusMinutes(1), before.plusHours(48).plusMinutes(1));

        PaymentEvent event = capturedEvent();
        assertThat(event.getPaymentType()).isEqualTo("REQUIRED");
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getUserId()).isEqualTo(winnerUserId);
        assertThat(event.getSellerId()).isEqualTo(sellerUserId);
        assertThat(event.getAmount()).isEqualByComparingTo("500.00");
        assertThat(event.getDepositAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getRemaining()).isEqualByComparingTo("450.00");
        assertThat(event.getDeadline()).isNotNull();
        assertThat(event.getInsufficientFunds()).isFalse();
    }

    @Test
    void createPaymentHold_insufficientBalance_createsUnfundedHoldWithoutMovingMoney() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "150.00", "100.00", "50.00");
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId))
                .thenReturn(Optional.of(depositLock("50.00", DepositLockStatus.LOCKED)));

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("50.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        PaymentHold saved = capturedHold();
        assertThat(saved.isFundsHeld()).isFalse();
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("450.00");
        assertThat(capturedEvent().getInsufficientFunds()).isTrue();
    }

    @Test
    void createPaymentHold_depositCoversTotal_remainingZeroNoHoldTransaction() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "0.00", "600.00");
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId))
                .thenReturn(Optional.of(depositLock("600.00", DepositLockStatus.LOCKED)));

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        PaymentHold saved = capturedHold();
        assertThat(saved.getDepositApplied()).isEqualByComparingTo("500.00");
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.isFundsHeld()).isTrue();
    }

    @Test
    void createPaymentHold_noLockedDeposit_appliesZeroAndHoldsFullTotal() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "600.00", "0.00");
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId))
                .thenReturn(Optional.of(depositLock("50.00", DepositLockStatus.RELEASED)));

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("500.00");
        PaymentHold saved = capturedHold();
        assertThat(saved.getDepositApplied()).isEqualByComparingTo("0.00");
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("500.00");
        assertThat(saved.getDepositLockId()).isNull();
        assertThat(saved.isFundsHeld()).isTrue();
    }

    @Test
    void createPaymentHold_holdAlreadyExists_isNoOp() {
        when(walletRepository.findByUserIdForUpdate(winnerUserId))
                .thenReturn(Optional.of(wallet(winnerWalletId, winnerUserId, "600.00", "600.00", "0.00")));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(true);

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        verify(paymentHoldRepository, never()).saveAndFlush(any());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(depositLockRepository, eventPublisher);
    }

    @Test
    void createPaymentHold_winnerWalletMissing_throwsNotFound() {
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId,
                new BigDecimal("500.00")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
        verify(paymentHoldRepository, never()).saveAndFlush(any());
    }

    // ── cancelPaymentHold ─────────────────────────────────────────────────────

    @Test
    void cancelPaymentHold_fundsHeld_returnsHeldFundsAndCancels() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, UUID.randomUUID(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "150.00", "500.00");
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));
        when(walletRepository.findByIdForUpdate(winnerWalletId)).thenReturn(Optional.of(winner));

        paymentService.cancelPaymentHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("600.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("50.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("650.00");
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionType.HOLD_CANCEL);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("450.00");
        assertThat(txCaptor.getValue().getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(txCaptor.getValue().getAvailableBalanceAfter()).isEqualByComparingTo("600.00");
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.CANCELLED);
        verify(paymentHoldRepository).save(h);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelPaymentHold_notFunded_cancelsWithoutTouchingWallet() {
        PaymentHold h = hold("500.00", "50.00", "450.00", false, UUID.randomUUID(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        paymentService.cancelPaymentHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.CANCELLED);
        verify(paymentHoldRepository).save(h);
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void cancelPaymentHold_notPending_isNoOp() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        paymentService.cancelPaymentHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.COMPLETED);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void cancelPaymentHold_noHold_isNoOp() {
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.empty());

        paymentService.cancelPaymentHold(auctionId);

        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    // ── getPendingPayments ────────────────────────────────────────────────────

    @Test
    void getPendingPayments_mapsHoldsWithHoursLeftAndExpiredFlag() {
        PaymentHold open = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10).plusMinutes(30), PaymentHoldStatus.PENDING_PAYMENT);
        PaymentHold overdue = hold("300.00", "0.00", "300.00", false, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByWinnerUserIdAndStatusOrderByDeadlineAsc(winnerUserId,
                PaymentHoldStatus.PENDING_PAYMENT)).thenReturn(List.of(overdue, open));

        List<PendingPaymentResponse> result = paymentService.getPendingPayments(winnerUserId);

        assertThat(result).hasSize(2);
        PendingPaymentResponse first = result.get(0);
        assertThat(first.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(first.isFundsHeld()).isFalse();
        assertThat(first.getHoursLeft()).isZero();
        assertThat(first.isExpired()).isTrue();
        PendingPaymentResponse second = result.get(1);
        assertThat(second.getAuctionId()).isEqualTo(auctionId);
        assertThat(second.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(second.getDepositApplied()).isEqualByComparingTo("50.00");
        assertThat(second.getRemaining()).isEqualByComparingTo("450.00");
        assertThat(second.isFundsHeld()).isTrue();
        assertThat(second.getHoursLeft()).isEqualTo(10);
        assertThat(second.isExpired()).isFalse();
        assertThat(second.getDeadline()).isEqualTo(open.getDeadline());
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `PaymentServiceImpl` doesn't exist yet.

- [ ] **Step 4: Implement**

`src/main/java/com/bidnow/wallet/service/impl/PaymentServiceImpl.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.kafka.PaymentApplicationEvent;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentHoldRepository paymentHoldRepository;
    private final WalletRepository walletRepository;
    private final DepositLockRepository depositLockRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${wallet.payment.deadline-hours:48}")
    private long deadlineHours = 48;

    @Override
    @Transactional
    public void createPaymentHold(UUID auctionId, UUID winnerUserId, UUID sellerUserId, BigDecimal totalAmount) {
        // Winner wallet row lock is the first read; duplicates serialize here.
        Wallet winner = walletRepository.findByUserIdForUpdate(winnerUserId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for userId: " + winnerUserId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
        if (paymentHoldRepository.existsByAuctionId(auctionId)) {
            log.debug("Payment hold already exists for auctionId={}", auctionId);
            return;
        }

        Optional<DepositLock> lock = depositLockRepository.findByWalletIdAndAuctionId(winner.getId(), auctionId)
                .filter(l -> l.getStatus() == DepositLockStatus.LOCKED);
        if (lock.isEmpty()) {
            log.warn("No LOCKED deposit for winner walletId={}, auctionId={}; deposit applied = 0",
                    winner.getId(), auctionId);
        }
        BigDecimal deposit = lock.map(DepositLock::getAmount).orElse(BigDecimal.ZERO);
        BigDecimal applied = deposit.min(totalAmount);
        BigDecimal remaining = totalAmount.subtract(applied);

        boolean fundsHeld;
        if (remaining.signum() == 0) {
            fundsHeld = true;
        } else if (winner.getAvailableBalance().compareTo(remaining) >= 0) {
            BigDecimal balanceBefore = winner.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(remaining);
            winner.setAvailableBalance(balanceAfter);
            winner.setLockedBalance(winner.getLockedBalance().add(remaining));
            walletRepository.save(winner);
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.HOLD)
                    .amount(remaining)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Payment hold for auction " + auctionId)
                    .build());
            fundsHeld = true;
        } else {
            fundsHeld = false;
        }

        LocalDateTime deadline = LocalDateTime.now().plusHours(deadlineHours);
        paymentHoldRepository.saveAndFlush(PaymentHold.builder()
                .auctionId(auctionId)
                .winnerWalletId(winner.getId())
                .winnerUserId(winnerUserId)
                .sellerUserId(sellerUserId)
                .totalAmount(totalAmount)
                .depositApplied(applied)
                .depositLockId(lock.map(DepositLock::getId).orElse(null))
                .remainingAmount(remaining)
                .fundsHeld(fundsHeld)
                .status(PaymentHoldStatus.PENDING_PAYMENT)
                .deadline(deadline)
                .build());

        eventPublisher.publishEvent(new PaymentApplicationEvent(this, PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(winnerUserId)
                .sellerId(sellerUserId)
                .amount(totalAmount)
                .depositAmount(applied)
                .remaining(remaining)
                .deadline(deadline.atZone(ZoneId.systemDefault()).toInstant())
                .insufficientFunds(!fundsHeld)
                .paymentType("REQUIRED")
                .build()));

        log.info("Payment hold created for auctionId={}, winner={}, total={}, remaining={}, fundsHeld={}",
                auctionId, winnerUserId, totalAmount, remaining, fundsHeld);
    }

    @Override
    @Transactional
    public void cancelPaymentHold(UUID auctionId) {
        // Lock order: hold row, then wallet row.
        Optional<PaymentHold> maybeHold = paymentHoldRepository.findByAuctionIdForUpdate(auctionId);
        if (maybeHold.isEmpty() || maybeHold.get().getStatus() != PaymentHoldStatus.PENDING_PAYMENT) {
            return;
        }
        PaymentHold hold = maybeHold.get();

        if (hold.isFundsHeld() && hold.getRemainingAmount().signum() > 0) {
            Wallet winner = walletRepository.findByIdForUpdate(hold.getWinnerWalletId())
                    .orElseThrow(() -> new NotFoundException("Wallet not found: " + hold.getWinnerWalletId(),
                            WalletErrorCodes.WALLET_NOT_FOUND));
            BigDecimal remaining = hold.getRemainingAmount();
            BigDecimal balanceBefore = winner.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.add(remaining);
            winner.setAvailableBalance(balanceAfter);
            winner.setLockedBalance(winner.getLockedBalance().subtract(remaining));
            walletRepository.save(winner);
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.HOLD_CANCEL)
                    .amount(remaining)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Payment hold cancelled for auction " + auctionId)
                    .build());
        }

        hold.setStatus(PaymentHoldStatus.CANCELLED);
        paymentHoldRepository.save(hold);
        log.info("Payment hold cancelled for auctionId={}", auctionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingPaymentResponse> getPendingPayments(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        return paymentHoldRepository
                .findByWinnerUserIdAndStatusOrderByDeadlineAsc(userId, PaymentHoldStatus.PENDING_PAYMENT)
                .stream()
                .map(h -> PendingPaymentResponse.builder()
                        .auctionId(h.getAuctionId())
                        .totalAmount(h.getTotalAmount())
                        .depositApplied(h.getDepositApplied())
                        .remaining(h.getRemainingAmount())
                        .fundsHeld(h.isFundsHeld())
                        .deadline(h.getDeadline())
                        .hoursLeft(Math.max(0, Duration.between(now, h.getDeadline()).toHours()))
                        .expired(now.isAfter(h.getDeadline()))
                        .build())
                .toList();
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (11 tests).

---

### Task 4: `confirmPayment` — atomic settlement

**Files:**
- Create: `src/main/java/com/bidnow/wallet/dto/request/ConfirmPaymentRequest.java`
- Create: `src/main/java/com/bidnow/wallet/dto/response/ConfirmPaymentResponse.java`
- Modify: `PaymentService`, `PaymentServiceImpl`, `PaymentServiceImplTest` (from Task 3)

**Interfaces:**
- Consumes:
  - Task 1: `PaymentHoldRepository.findByAuctionIdForUpdate`, `WalletRepository.findIdByUserId`, and the new error codes.
  - Task 3: `PaymentServiceImpl`, its fields and its test helpers (`wallet`, `depositLock`, `hold`, `capturedEvent`).
  - Existing: `WalletRepository.findByIdForUpdate`, `DepositLockRepository.findById`, `InsufficientBalanceException(BigDecimal, BigDecimal)`, `ConflictException(String, String)`, `BadRequestException(String, String)` (common), `NotFoundException`.
- Produces:
  - `ConfirmPaymentRequest` (`@Data @NoArgsConstructor @AllArgsConstructor`, with `@NotNull UUID auctionId`)
  - `ConfirmPaymentResponse` (`@Data @Builder`), with fields `UUID auctionId, BigDecimal amountPaid, BigDecimal depositApplied, BigDecimal remainingPaid, BigDecimal availableBalance, BigDecimal lockedBalance`
  - `PaymentService.confirmPayment(UUID callerUserId, UUID auctionId): ConfirmPaymentResponse`

- [ ] **Step 1: Create the DTOs and add the interface method**

`src/main/java/com/bidnow/wallet/dto/request/ConfirmPaymentRequest.java`:

```java
package com.bidnow.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {

    @NotNull(message = "auctionId is required")
    private UUID auctionId;
}
```

`src/main/java/com/bidnow/wallet/dto/response/ConfirmPaymentResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ConfirmPaymentResponse {
    private UUID auctionId;
    private BigDecimal amountPaid;
    private BigDecimal depositApplied;
    private BigDecimal remainingPaid;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
}
```

In `src/main/java/com/bidnow/wallet/service/PaymentService.java`, add the import `com.bidnow.wallet.dto.response.ConfirmPaymentResponse` and this method:

```java
    ConfirmPaymentResponse confirmPayment(UUID callerUserId, UUID auctionId);
```

- [ ] **Step 2: Write the failing tests**

In `src/test/java/com/bidnow/wallet/service/impl/PaymentServiceImplTest.java`:

(a) Add these imports:

```java
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import org.mockito.InOrder;
```

and these static imports:

```java
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
```

(b) Append the following before the final closing brace:

```java
    // ── confirmPayment ────────────────────────────────────────────────────────

    private DepositLock stubConfirm(PaymentHold h, Wallet winner, Wallet seller, DepositLock lock) {
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));
        when(walletRepository.findIdByUserId(sellerUserId)).thenReturn(Optional.of(sellerWalletId));
        when(walletRepository.findByIdForUpdate(winnerWalletId)).thenReturn(Optional.of(winner));
        when(walletRepository.findByIdForUpdate(sellerWalletId)).thenReturn(Optional.of(seller));
        if (lock != null) {
            when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));
        }
        return lock;
    }

    @Test
    void confirmPayment_fundsHeld_settlesWinnerAndSellerAtomically() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", true, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "150.00", "500.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        ConfirmPaymentResponse result = paymentService.confirmPayment(winnerUserId, auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("150.00");
        assertThat(seller.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(seller.getTotalBalance()).isEqualByComparingTo("500.00");
        assertThat(lock.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(lock.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(lock);
        verify(walletRepository).save(winner);
        verify(walletRepository).save(seller);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        Transaction winnerTx = txCaptor.getAllValues().get(0);
        assertThat(winnerTx.getType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(winnerTx.getWalletId()).isEqualTo(winnerWalletId);
        assertThat(winnerTx.getAmount()).isEqualByComparingTo("500.00");
        assertThat(winnerTx.getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(winnerTx.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(winnerTx.getReferenceId()).isEqualTo(auctionId);
        Transaction sellerTx = txCaptor.getAllValues().get(1);
        assertThat(sellerTx.getType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(sellerTx.getWalletId()).isEqualTo(sellerWalletId);
        assertThat(sellerTx.getAmount()).isEqualByComparingTo("500.00");
        assertThat(sellerTx.getAvailableBalanceBefore()).isEqualByComparingTo("0.00");
        assertThat(sellerTx.getAvailableBalanceAfter()).isEqualByComparingTo("500.00");

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.COMPLETED);
        assertThat(h.getCompletedAt()).isNotNull();
        verify(paymentHoldRepository).save(h);

        PaymentEvent event = capturedEvent();
        assertThat(event.getPaymentType()).isEqualTo("COMPLETED");
        assertThat(event.getUserId()).isEqualTo(winnerUserId);
        assertThat(event.getSellerId()).isEqualTo(sellerUserId);
        assertThat(event.getAmount()).isEqualByComparingTo("500.00");

        assertThat(result.getAuctionId()).isEqualTo(auctionId);
        assertThat(result.getAmountPaid()).isEqualByComparingTo("500.00");
        assertThat(result.getDepositApplied()).isEqualByComparingTo("50.00");
        assertThat(result.getRemainingPaid()).isEqualByComparingTo("450.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void confirmPayment_notFundedButToppedUp_paysRemainingFromAvailable() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", false, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "550.00", "500.00", "50.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        paymentService.confirmPayment(winnerUserId, auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("50.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("50.00");
        assertThat(seller.getTotalBalance()).isEqualByComparingTo("500.00");
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        assertThat(txCaptor.getAllValues().get(0).getAvailableBalanceBefore()).isEqualByComparingTo("500.00");
        assertThat(txCaptor.getAllValues().get(0).getAvailableBalanceAfter()).isEqualByComparingTo("50.00");
    }

    @Test
    void confirmPayment_notFundedAndInsufficient_throwsAndChangesNothing() {
        PaymentHold h = hold("500.00", "50.00", "450.00", false, UUID.randomUUID(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "150.00", "100.00", "50.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, null);

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOfSatisfying(InsufficientBalanceException.class, ex -> {
                    assertThat(ex.getAvailableBalance()).isEqualByComparingTo("100.00");
                    assertThat(ex.getRequired()).isEqualByComparingTo("450.00");
                });
        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_deadlinePassed_throwsExpired() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_DEADLINE_EXPIRED");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void confirmPayment_notPending_throwsConflict() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_NOT_PENDING");
    }

    @Test
    void confirmPayment_otherUsersHold_throwsNotFound() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> paymentService.confirmPayment(UUID.randomUUID(), auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_HOLD_NOT_FOUND");
    }

    @Test
    void confirmPayment_noHold_throwsNotFound() {
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_HOLD_NOT_FOUND");
    }

    @Test
    void confirmPayment_sellerWalletMissing_throwsNotFound() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));
        when(walletRepository.findIdByUserId(sellerUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("SELLER_WALLET_NOT_FOUND");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void confirmPayment_depositLockNoLongerLocked_throwsConflictAndChangesNothing() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.RELEASED);
        PaymentHold h = hold("500.00", "50.00", "450.00", true, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "150.00", "450.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DEPOSIT_LOCK_CLOSED");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_depositExceedsTotal_refundsExcessToWinner() {
        DepositLock lock = depositLock("600.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "500.00", "0.00", true, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "0.00", "600.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        ConfirmPaymentResponse result = paymentService.confirmPayment(winnerUserId, auctionId);

        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("100.00");
        assertThat(seller.getTotalBalance()).isEqualByComparingTo("500.00");
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(3)).save(txCaptor.capture());
        Transaction refund = txCaptor.getAllValues().get(1);
        assertThat(refund.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(refund.getWalletId()).isEqualTo(winnerWalletId);
        assertThat(refund.getAmount()).isEqualByComparingTo("100.00");
        assertThat(refund.getAvailableBalanceBefore()).isEqualByComparingTo("0.00");
        assertThat(refund.getAvailableBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void confirmPayment_locksHoldFirstThenWalletsInAscendingIdOrder() {
        winnerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        sellerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, null);

        paymentService.confirmPayment(winnerUserId, auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdate(auctionId);
        order.verify(walletRepository).findByIdForUpdate(sellerWalletId);
        order.verify(walletRepository).findByIdForUpdate(winnerWalletId);
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `PaymentServiceImpl` doesn't implement `confirmPayment`.

- [ ] **Step 4: Implement**

In `src/main/java/com/bidnow/wallet/service/impl/PaymentServiceImpl.java`, add these imports:

```java
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
```

Then add these methods after `createPaymentHold`:

```java
    @Override
    @Transactional
    public ConfirmPaymentResponse confirmPayment(UUID callerUserId, UUID auctionId) {
        // Lock order: hold row first, then both wallets in ascending id order.
        PaymentHold hold = paymentHoldRepository.findByAuctionIdForUpdate(auctionId)
                .filter(h -> h.getWinnerUserId().equals(callerUserId))
                .orElseThrow(() -> new NotFoundException("No payment hold for auction " + auctionId,
                        WalletErrorCodes.PAYMENT_HOLD_NOT_FOUND));
        if (hold.getStatus() != PaymentHoldStatus.PENDING_PAYMENT) {
            throw new ConflictException("Payment for auction " + auctionId + " is " + hold.getStatus(),
                    WalletErrorCodes.PAYMENT_NOT_PENDING);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(hold.getDeadline())) {
            throw new BadRequestException("Payment deadline for auction " + auctionId + " has passed",
                    WalletErrorCodes.PAYMENT_DEADLINE_EXPIRED);
        }

        UUID sellerWalletId = walletRepository.findIdByUserId(hold.getSellerUserId())
                .orElseThrow(() -> new NotFoundException("Seller wallet not found for userId: " + hold.getSellerUserId(),
                        WalletErrorCodes.SELLER_WALLET_NOT_FOUND));
        Wallet winner;
        Wallet seller;
        if (hold.getWinnerWalletId().compareTo(sellerWalletId) < 0) {
            winner = lockWallet(hold.getWinnerWalletId());
            seller = lockWallet(sellerWalletId);
        } else {
            seller = lockWallet(sellerWalletId);
            winner = lockWallet(hold.getWinnerWalletId());
        }

        BigDecimal total = hold.getTotalAmount();
        BigDecimal applied = hold.getDepositApplied();
        BigDecimal remaining = hold.getRemainingAmount();

        if (!hold.isFundsHeld() && winner.getAvailableBalance().compareTo(remaining) < 0) {
            throw new InsufficientBalanceException(winner.getAvailableBalance(), remaining);
        }
        DepositLock lock = null;
        if (hold.getDepositLockId() != null) {
            lock = depositLockRepository.findById(hold.getDepositLockId())
                    .filter(l -> l.getStatus() == DepositLockStatus.LOCKED)
                    .orElseThrow(() -> new ConflictException("Deposit for auction " + auctionId + " is no longer locked",
                            WalletErrorCodes.DEPOSIT_LOCK_CLOSED));
        }

        // Winner: pay the remaining part.
        BigDecimal winnerBefore = winner.getAvailableBalance();
        if (hold.isFundsHeld()) {
            winner.setLockedBalance(winner.getLockedBalance().subtract(remaining));
        } else {
            winner.setAvailableBalance(winner.getAvailableBalance().subtract(remaining));
        }
        winner.setTotalBalance(winner.getTotalBalance().subtract(remaining));

        // Winner: consume the deposit; any excess over the price goes back to available.
        BigDecimal excess = BigDecimal.ZERO;
        if (lock != null) {
            excess = lock.getAmount().subtract(applied);
            winner.setLockedBalance(winner.getLockedBalance().subtract(lock.getAmount()));
            winner.setTotalBalance(winner.getTotalBalance().subtract(applied));
            lock.setStatus(DepositLockStatus.RELEASED);
            lock.setReleasedAt(now);
            depositLockRepository.save(lock);
        }
        BigDecimal winnerAfterPayment = winner.getAvailableBalance();
        transactionRepository.save(Transaction.builder()
                .walletId(winner.getId())
                .type(TransactionType.PAYMENT)
                .amount(total)
                .availableBalanceBefore(winnerBefore)
                .availableBalanceAfter(winnerAfterPayment)
                .referenceId(auctionId)
                .status(TransactionStatus.COMPLETED)
                .description("Payment for auction " + auctionId)
                .build());
        if (excess.signum() > 0) {
            winner.setAvailableBalance(winnerAfterPayment.add(excess));
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.REFUND)
                    .amount(excess)
                    .availableBalanceBefore(winnerAfterPayment)
                    .availableBalanceAfter(winner.getAvailableBalance())
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Deposit excess refund for auction " + auctionId)
                    .build());
        }
        walletRepository.save(winner);

        // Seller: credit exactly the winning bid.
        BigDecimal sellerBefore = seller.getAvailableBalance();
        seller.setAvailableBalance(sellerBefore.add(total));
        seller.setTotalBalance(seller.getTotalBalance().add(total));
        walletRepository.save(seller);
        transactionRepository.save(Transaction.builder()
                .walletId(seller.getId())
                .type(TransactionType.PAYMENT)
                .amount(total)
                .availableBalanceBefore(sellerBefore)
                .availableBalanceAfter(seller.getAvailableBalance())
                .referenceId(auctionId)
                .status(TransactionStatus.COMPLETED)
                .description("Sale proceeds for auction " + auctionId)
                .build());

        hold.setStatus(PaymentHoldStatus.COMPLETED);
        hold.setCompletedAt(now);
        paymentHoldRepository.save(hold);

        eventPublisher.publishEvent(new PaymentApplicationEvent(this, PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(hold.getWinnerUserId())
                .sellerId(hold.getSellerUserId())
                .amount(total)
                .depositAmount(applied)
                .remaining(remaining)
                .paymentType("COMPLETED")
                .build()));

        log.info("Payment confirmed for auctionId={}, winner={}, seller={}, amount={}",
                auctionId, hold.getWinnerUserId(), hold.getSellerUserId(), total);

        return ConfirmPaymentResponse.builder()
                .auctionId(auctionId)
                .amountPaid(total)
                .depositApplied(applied)
                .remainingPaid(remaining)
                .availableBalance(winner.getAvailableBalance())
                .lockedBalance(winner.getLockedBalance())
                .build();
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found: " + walletId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (22 tests: 11 from Task 3 and 11 new).

---

### Task 5: Hook payment holds into `AuctionLifecycleEventConsumer`

**Files:** `AuctionLifecycleEventConsumer` and `AuctionLifecycleEventConsumerTest` (see the File Map).

**Interfaces:**
- Consumes: from Task 3, `PaymentService.createPaymentHold(UUID, UUID, UUID, BigDecimal)` and `cancelPaymentHold(UUID)`. From common, `AuctionEndedEvent.getSellerId()` and `getWinningBidAmount()`.
- Produces: `AuctionLifecycleEventConsumer`'s constructor gains a final `PaymentService paymentService` field (declared last). Failure handling: all steps run, and the first `RuntimeException` is rethrown.

- [ ] **Step 1: Write the failing tests**

In `src/test/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumerTest.java`:

(a) Add the import `com.bidnow.wallet.service.PaymentService`. Also add a mock field after the `depositLockService` mock, because `@InjectMocks` constructor injection needs it:

```java
    @Mock
    private PaymentService paymentService;
```

(b) Append these tests before the final closing brace:

```java
    // ── payment hold integration ──────────────────────────────────────────────

    private final UUID sellerUserId = UUID.randomUUID();

    private AuctionEndedEvent endedWithWinner() {
        return AuctionEndedEvent.builder()
                .auctionId(auctionId)
                .winnerId(winnerUserId)
                .sellerId(sellerUserId)
                .winningBidAmount(new BigDecimal("500.00"))
                .build();
    }

    @Test
    void onAuctionEnded_withWinner_createsPaymentHold() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionEnded(endedWithWinner());

        verify(paymentService).createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));
    }

    @Test
    void onAuctionEnded_noWinner_createsNoPaymentHold() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionEnded(ended(null, null));

        verifyNoInteractions(paymentService);
    }

    @Test
    void onAuctionEnded_releaseFails_stillCreatesHoldThenThrowsReleaseFailure() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));
        when(walletRepository.findByUserId(winnerUserId)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("db down"))
                .when(depositLockService).releaseDeposit(eq(lockA.getId()), any(), any());

        assertThatThrownBy(() -> consumer.onAuctionEnded(endedWithWinner()))
                .isInstanceOf(DepositReleaseException.class);
        verify(paymentService).createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));
    }

    @Test
    void onAuctionEnded_holdFails_releasesLosersThenRethrowsHoldFailure() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));
        when(walletRepository.findByUserId(winnerUserId)).thenReturn(Optional.empty());
        IllegalStateException holdFailure = new IllegalStateException("hold failed");
        doThrow(holdFailure).when(paymentService).createPaymentHold(any(), any(), any(), any());

        assertThatThrownBy(() -> consumer.onAuctionEnded(endedWithWinner())).isSameAs(holdFailure);
        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
    }

    @Test
    void onAuctionCancelled_cancelsPaymentHold() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionCancelled(AuctionCancelledEvent.builder().auctionId(auctionId).build());

        verify(paymentService).cancelPaymentHold(auctionId);
    }

    @Test
    void onAuctionCancelled_holdCancelFails_releasesDepositsThenRethrows() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));
        IllegalStateException cancelFailure = new IllegalStateException("cancel failed");
        doThrow(cancelFailure).when(paymentService).cancelPaymentHold(auctionId);

        assertThatThrownBy(() -> consumer.onAuctionCancelled(
                AuctionCancelledEvent.builder().auctionId(auctionId).build())).isSameAs(cancelFailure);
        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_CANCELLED);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=AuctionLifecycleEventConsumerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. `createPaymentHold` and `cancelPaymentHold` are never invoked, so the new verifications fail.

- [ ] **Step 3: Implement**

In `src/main/java/com/bidnow/wallet/kafka/AuctionLifecycleEventConsumer.java`:

(a) Add the import `com.bidnow.wallet.service.PaymentService` and the field below, after `depositLockService`:

```java
    private final PaymentService paymentService;
```

(b) Replace the two listener methods with:

```java
    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionEnded(AuctionEndedEvent event) {
        log.info("Received AuctionEndedEvent for auctionId={}, winnerId={}", event.getAuctionId(), event.getWinnerId());
        List<RuntimeException> failures = new ArrayList<>();
        runStep(failures, () -> releaseAll(event.getAuctionId(), event.getWinnerId(), RefundReason.AUCTION_LOST));
        if (event.getWinnerId() != null) {
            runStep(failures, () -> paymentService.createPaymentHold(event.getAuctionId(), event.getWinnerId(),
                    event.getSellerId(), event.getWinningBidAmount()));
        }
        rethrowFirst(failures);
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionCancelled(AuctionCancelledEvent event) {
        log.info("Received AuctionCancelledEvent for auctionId={}", event.getAuctionId());
        List<RuntimeException> failures = new ArrayList<>();
        runStep(failures, () -> releaseAll(event.getAuctionId(), null, RefundReason.AUCTION_CANCELLED));
        runStep(failures, () -> paymentService.cancelPaymentHold(event.getAuctionId()));
        rethrowFirst(failures);
    }

    /** Runs one independent settlement step; a failure is recorded so the other steps still run. */
    private void runStep(List<RuntimeException> failures, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ex) {
            log.error("Auction lifecycle step failed", ex);
            failures.add(ex);
        }
    }

    /** Rethrow so the Kafka error handler redelivers; every step is idempotent. */
    private static void rethrowFirst(List<RuntimeException> failures) {
        if (!failures.isEmpty()) {
            throw failures.get(0);
        }
    }
```

Leave `releaseAll` unchanged.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=AuctionLifecycleEventConsumerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (12 tests: the 6 existing ones and 6 new).

---

### Task 6: `PaymentController` — the public endpoints

**Files:** `PaymentController` and `PaymentControllerTest` (see the File Map).

**Interfaces:**
- Consumes: from Tasks 3–4, `PaymentService.getPendingPayments(UUID)`, `confirmPayment(UUID, UUID)`, `PendingPaymentResponse`, `ConfirmPaymentRequest` and `ConfirmPaymentResponse`. Existing: `@AuthenticatedUserId` (`com.bidnow.common.annotation`), `UserIdArgumentResolver` (`com.bidnow.common.resolver`, which reads the `X-User-Id` header), `WalletExceptionHandler` and `GlobalExceptionHandler`.
- Produces:
  - `GET /api/v1/wallets/payments/pending` returns `BaseResponse<List<PendingPaymentResponse>>`
  - `POST /api/v1/wallets/payments/confirm` returns `BaseResponse<ConfirmPaymentResponse>`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/bidnow/wallet/controller/PaymentControllerTest.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.exception.WalletExceptionHandler;
import com.bidnow.wallet.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String BASE = "/api/v1/wallets/payments";

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    private final UUID userId = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new WalletExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private String confirmBody() {
        return "{\"auctionId\":\"" + auctionId + "\"}";
    }

    @Test
    void getPendingPayments_returnsList() throws Exception {
        when(paymentService.getPendingPayments(userId)).thenReturn(List.of(PendingPaymentResponse.builder()
                .auctionId(auctionId)
                .totalAmount(new BigDecimal("500.00"))
                .depositApplied(new BigDecimal("50.00"))
                .remaining(new BigDecimal("450.00"))
                .fundsHeld(true)
                .deadline(LocalDateTime.of(2026, 9, 30, 10, 0))
                .hoursLeft(10)
                .expired(false)
                .build()));

        mockMvc.perform(get(BASE + "/pending").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].auctionId").value(auctionId.toString()))
                .andExpect(jsonPath("$.data[0].totalAmount").value(500.00))
                .andExpect(jsonPath("$.data[0].remaining").value(450.00))
                .andExpect(jsonPath("$.data[0].hoursLeft").value(10))
                .andExpect(jsonPath("$.data[0].expired").value(false));
    }

    @Test
    void confirmPayment_success_returns200() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId)).thenReturn(ConfirmPaymentResponse.builder()
                .auctionId(auctionId)
                .amountPaid(new BigDecimal("500.00"))
                .depositApplied(new BigDecimal("50.00"))
                .remainingPaid(new BigDecimal("450.00"))
                .availableBalance(new BigDecimal("150.00"))
                .lockedBalance(new BigDecimal("0.00"))
                .build());

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPaid").value(500.00))
                .andExpect(jsonPath("$.data.availableBalance").value(150.00));
    }

    @Test
    void confirmPayment_noHold_returns404() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new NotFoundException("none", "PAYMENT_HOLD_NOT_FOUND"));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_HOLD_NOT_FOUND"));
    }

    @Test
    void confirmPayment_expired_returns400() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new BadRequestException("late", "PAYMENT_DEADLINE_EXPIRED"));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_DEADLINE_EXPIRED"));
    }

    @Test
    void confirmPayment_notPending_returns409() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new ConflictException("done", "PAYMENT_NOT_PENDING"));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_PENDING"));
    }

    @Test
    void confirmPayment_insufficientBalance_returns400WithAmounts() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new InsufficientBalanceException(new BigDecimal("100.00"), new BigDecimal("450.00")));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.errors.availableBalance").value("100.00"))
                .andExpect(jsonPath("$.errors.required").value("450.00"));
    }

    @Test
    void confirmPayment_missingAuctionId_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(paymentService, never()).confirmPayment(any(), any());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=PaymentControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `PaymentController` doesn't exist yet.

- [ ] **Step 3: Implement**

`src/main/java/com/bidnow/wallet/controller/PaymentController.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.wallet.dto.request.ConfirmPaymentRequest;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets/payments")
@RequiredArgsConstructor
@Tag(name = "Winner Payments", description = "Pending winner payments and payment confirmation")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "List pending payments", description = "PENDING_PAYMENT holds for the current user, earliest deadline first.")
    @GetMapping("/pending")
    public ResponseEntity<BaseResponse<List<PendingPaymentResponse>>> getPendingPayments(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(paymentService.getPendingPayments(userId)));
    }

    @Operation(summary = "Confirm payment", description = "Settles a won auction: debits the winner, credits the seller.")
    @PostMapping("/confirm")
    public ResponseEntity<BaseResponse<ConfirmPaymentResponse>> confirmPayment(@AuthenticatedUserId UUID userId,
                                                                               @Valid @RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(BaseResponse.success(paymentService.confirmPayment(userId, request.getAuctionId())));
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (7 tests).

---

### Task 7: Documentation

**Files:** `docs/architecture.md`, `docs/epics/wallet/epic.md`

**Interfaces:** none. This task is docs only.

- [ ] **Step 1: Architecture**

In `docs/architecture.md`, in the `### Wallet Events (Kafka)` table, add these rows after the `deposit-refunded-topic` row:

```markdown
| Consumes | `auction-ended-topic` (winner) | `AuctionEndedEvent` | Create a 48h payment hold for the winner (`payment_holds`); deposit counts toward the price. |
| Consumes | `auction-cancelled-topic` (hold) | `AuctionCancelledEvent` | Void any `PENDING_PAYMENT` hold and return held funds. |
| Produces | `payment-event-topic` (key winner `userId`) | `PaymentEvent { paymentType: REQUIRED \| COMPLETED, … }` | Payment required (with deadline, `insufficientFunds`) / payment completed; after commit. |
```

Directly after that section's paragraph (the one starting "Each refund is its own DB transaction."), add:

```markdown

**Winner payment endpoints (public, via gateway):** `GET /api/v1/wallets/payments/pending`, `POST /api/v1/wallets/payments/confirm { auctionId }`. Confirm settles atomically: hold row locked first, then winner and seller wallets in ascending id order.
```

- [ ] **Step 2: Wallet epic**

In `docs/epics/wallet/epic.md`, insert this block immediately before the line `**Known gaps (follow-up tickets, outside WALLET-304):**`:

```markdown
**Winner Payment — WALLET-305:**

- [ ] On `auction-ended-topic` with a winner, create a `payment_holds` row (one per auction): `deposit_applied = min(LOCKED deposit, winning bid)`, `remaining = bid − deposit_applied`, deadline `now + 48h`
- [ ] If available ≥ remaining, move remaining to locked (HOLD ledger row); otherwise the hold is unfunded (`funds_held = false`) and the winner must top up before confirming
- [ ] `GET /api/v1/wallets/payments/pending` — pending holds with `deadline`, `hoursLeft`, `expired`
- [ ] `POST /api/v1/wallets/payments/confirm { auctionId }` — atomic: winner pays exactly the winning bid (deposit consumed, excess refunded), seller credited exactly the winning bid, PAYMENT ledger rows for both, hold COMPLETED
- [ ] Errors: `404 PAYMENT_HOLD_NOT_FOUND`, `400 PAYMENT_DEADLINE_EXPIRED`, `409 PAYMENT_NOT_PENDING`, `400 INSUFFICIENT_BALANCE`, `409 DEPOSIT_LOCK_CLOSED`, `404 SELLER_WALLET_NOT_FOUND`
- [ ] `PaymentEvent` on `payment-event-topic`: `REQUIRED` on hold creation, `COMPLETED` on confirm
- [ ] On `auction-cancelled-topic`, a `PENDING_PAYMENT` hold is voided (held funds returned, HOLD_CANCEL ledger row, status CANCELLED)
- [ ] Follow-ups: auction-service consumes `PaymentEvent` to set `payment_deadline` / `winner_paid_at`; WALLET-306 forfeit must lock the `payment_holds` row before wallets

```

---

### Task 8: Full verification

**Files:** none modified.

- [ ] **Step 1: Run the full wallet-service suite**

Run (from `backend/`): `mvn -pl wallet-service -am clean test`
Expected: BUILD SUCCESS with 0 failures. The new tests are `PaymentServiceImplTest` (22), `PaymentControllerTest` (7), `AuctionLifecycleEventConsumerTest` (+6) and `WalletEventPublisherTest` (+1).

- [ ] **Step 2: Manual smoke test (only if Postgres, Kafka and wallet-service are running)**

1. Start wallet-service and confirm Liquibase applies `wallet_004` without errors.
2. Give a winner wallet 600 available and a seller wallet 0, and publish an `AuctionEndedEvent` naming them, with `winningBidAmount` 500. Expected: a `payment_holds` row with PENDING_PAYMENT, the winner's available drops by the remaining amount, and a `payment-event-topic` message with `paymentType` REQUIRED.
3. Call `GET /api/v1/wallets/payments/pending` with `X-User-Id` = winner. Expected: one item.
4. Call `POST /api/v1/wallets/payments/confirm`. Expected: 200, the seller's available is 500, the hold is COMPLETED, and a `payment-event-topic` message with `paymentType` COMPLETED appears.
5. Confirm again. Expected: 409 `PAYMENT_NOT_PENDING`.

If no stack is available, skip this step and report it as not run.
