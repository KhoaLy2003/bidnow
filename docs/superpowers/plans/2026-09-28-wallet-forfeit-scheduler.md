# WALLET-306 Forfeit Scheduler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every 5 minutes, find `PENDING_PAYMENT` payment holds past their deadline and forfeit each one atomically. The held remainder goes back to the winner, the whole locked deposit moves to the platform wallet, the hold and the deposit lock become `FORFEITED`, and `PaymentEvent{FAILED}` is sent after commit.

**Architecture:** A non-transactional `ForfeitScheduler` (`@Scheduled`) reads a batch of expired hold auction ids and calls `PaymentService.forfeitExpiredHold(auctionId)` once per id, with each call in its own try/catch. That method is one transaction:
- It locks the hold row with `FOR UPDATE SKIP LOCKED`, so parallel instances step around each other.
- It re-checks that the hold is still pending and past its deadline.
- It locks the winner and platform wallets in ascending `UUID.compareTo` order.
- It moves the money and writes the ledger rows.

**Tech Stack:** Java 17, Spring Boot 3.2.4 (`@EnableScheduling`), Spring Data JPA (Hibernate), PostgreSQL, Lombok, JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-28-wallet-forfeit-scheduler-design.md`

## Global Constraints

- **Do not run `git add`, `git commit`, `git stash`, `git reset`, or any other git write.** Leave all changes uncommitted on branch `feature/wallet-deposit-lock`.
- Scope is wallet-service only. There is no migration, since `payment_holds.status` is `VARCHAR(20)` with no value check. Do not change auction-service, media-service or the `common` module.
- The forfeit amount is the whole `LOCKED` `deposit_locks.amount`, even when it is larger than the bid. The held remainder (when `fundsHeld`) goes back to the winner's available balance. The recipient is the platform wallet: the wallet of `wallet.platform-user-id`.
- With no `LOCKED` deposit, nothing is forfeited, but the hold still becomes `FORFEITED` and the event carries `depositAmount = 0`.
- **Lock order:** take the `payment_holds` row first, via `findByAuctionIdForUpdateSkipLocked`. Then take the winner and platform wallets via `findByIdForUpdate`, lower `UUID` (by `compareTo`) first.
- The expiry test is `now.isAfter(hold.getDeadline())`. Anything not PENDING_PAYMENT or not yet due is a silent no-op.
- Money is conserved: the winner's total falls by exactly `forfeited` and the platform's rises by exactly `forfeited`. Both keep `total = available + locked`.
- Event: `PaymentEvent{paymentType="FAILED", auctionId, userId=winner, sellerId, amount=totalAmount, depositAmount=forfeited, remaining=remainingAmount}` goes through the existing `PaymentApplicationEvent`, and is sent after commit on `payment-event-topic`.
- Config: `wallet.payment.forfeit-interval-ms: 300000` and `wallet.payment.forfeit-batch-size: 100`.
- Scheduler failures: each hold has its own try/catch with an ERROR log. The hold is retried on the next tick.
- Run Maven from `backend/`: `mvn -q -pl wallet-service -am test …`.

---

## File Map

Paths are relative to `backend/wallet-service/` unless they start with `docs/`.

| File | Action | Task |
|---|---|---|
| `src/main/java/com/bidnow/wallet/domain/enums/PaymentHoldStatus.java` | Modify (+`FORFEITED`) | 1 |
| `src/main/java/com/bidnow/wallet/repository/PaymentHoldRepository.java` | Modify (+2 queries) | 1 |
| `src/main/java/com/bidnow/wallet/service/PaymentService.java` | Modify (+`forfeitExpiredHold`) | 1 |
| `src/main/java/com/bidnow/wallet/service/impl/PaymentServiceImpl.java` | Modify | 1 |
| `src/test/java/com/bidnow/wallet/service/impl/PaymentServiceImplTest.java` | Modify | 1 |
| `src/main/resources/application.yml` | Modify (2 keys) | 2 |
| `src/main/java/com/bidnow/wallet/config/SchedulingConfig.java` | Create | 2 |
| `src/main/java/com/bidnow/wallet/scheduler/ForfeitScheduler.java` | Create | 2 |
| `src/test/java/com/bidnow/wallet/scheduler/ForfeitSchedulerTest.java` | Create | 2 |
| `docs/epics/wallet/epic.md`, `docs/architecture.md` | Modify | 3 |

---

### Task 1: `forfeitExpiredHold` — atomic per-hold forfeit

**Files:** `PaymentHoldStatus`, `PaymentHoldRepository`, `PaymentService`, `PaymentServiceImpl`, `PaymentServiceImplTest` (see the File Map).

**Interfaces:**
- Consumes (existing):
  - `PaymentHold` getters and setters, `isFundsHeld()`
  - `WalletRepository.findIdByUserId(UUID): Optional<UUID>` and `findByIdForUpdate(UUID)`
  - `DepositLockRepository.findById`
  - `TransactionType.HOLD_CANCEL`/`FORFEIT`, `TransactionStatus.COMPLETED`
  - `DepositLockStatus.LOCKED`/`FORFEITED`
  - `PaymentApplicationEvent`, `PaymentEvent` builder
  - `NotFoundException`, `WalletErrorCodes.WALLET_NOT_FOUND`
  - the private `PaymentServiceImpl.lockWallet(UUID)` from WALLET-305
  - test helpers in `PaymentServiceImplTest`: `wallet(id, userId, total, available, locked)`, `depositLock(amount, status)`, `hold(total, applied, remaining, fundsHeld, depositLockId, deadline, status)`, `capturedEvent()`
- Produces:
  - `PaymentHoldStatus.FORFEITED`
  - `PaymentHoldRepository.findExpiredAuctionIds(PaymentHoldStatus status, LocalDateTime now, Pageable page): List<UUID>`
  - `PaymentHoldRepository.findByAuctionIdForUpdateSkipLocked(UUID auctionId): Optional<PaymentHold>`
  - `PaymentService.forfeitExpiredHold(UUID auctionId): void`
  - The `PaymentServiceImpl` field `@Value("${wallet.platform-user-id}") private String platformUserId;` (non-final, not in the constructor)

- [ ] **Step 1: Enum, repository and interface**

In `src/main/java/com/bidnow/wallet/domain/enums/PaymentHoldStatus.java`, replace the enum body so it reads:

```java
public enum PaymentHoldStatus {
    PENDING_PAYMENT, COMPLETED, CANCELLED, FORFEITED
}
```

In `src/main/java/com/bidnow/wallet/repository/PaymentHoldRepository.java`, add these imports:

```java
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.LocalDateTime;
```

Then add these methods to the interface:

```java
    /** Auction ids of holds in {@code status} whose deadline is before {@code now}, earliest first. */
    @Query("SELECT h.auctionId FROM PaymentHold h WHERE h.status = :status AND h.deadline < :now ORDER BY h.deadline ASC")
    List<UUID> findExpiredAuctionIds(@Param("status") PaymentHoldStatus status,
                                     @Param("now") LocalDateTime now, Pageable page);

    /** SELECT ... FOR UPDATE SKIP LOCKED (Hibernate lock timeout -2): a hold locked by another instance is skipped. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT h FROM PaymentHold h WHERE h.auctionId = :auctionId")
    Optional<PaymentHold> findByAuctionIdForUpdateSkipLocked(@Param("auctionId") UUID auctionId);
```

In `src/main/java/com/bidnow/wallet/service/PaymentService.java`, add:

```java
    void forfeitExpiredHold(UUID auctionId);
```

- [ ] **Step 2: Write the failing tests**

In `src/test/java/com/bidnow/wallet/service/impl/PaymentServiceImplTest.java`:

(a) Add the import `org.springframework.test.util.ReflectionTestUtils`.

(b) Append the following before the final closing brace:

```java
    // ── forfeitExpiredHold ────────────────────────────────────────────────────

    private final UUID platformUserId = UUID.randomUUID();
    private UUID platformWalletId = UUID.randomUUID();

    private void usePlatform() {
        ReflectionTestUtils.setField(paymentService, "platformUserId", platformUserId.toString());
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.of(platformWalletId));
    }

    private void stubForfeit(PaymentHold h, Wallet winner, Wallet platform) {
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));
        usePlatform();
        when(walletRepository.findByIdForUpdate(winnerWalletId)).thenReturn(Optional.of(winner));
        when(walletRepository.findByIdForUpdate(platformWalletId)).thenReturn(Optional.of(platform));
    }

    private List<Transaction> savedTransactions(int count) {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void forfeitExpiredHold_fundsHeldWithDeposit_returnsRemainingAndMovesDepositToPlatform() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", true, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "150.00", "500.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "1000.00", "1000.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("600.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("600.00");
        assertThat(platform.getAvailableBalance()).isEqualByComparingTo("1050.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("1050.00");
        verify(walletRepository).save(winner);
        verify(walletRepository).save(platform);

        List<Transaction> txs = savedTransactions(3);
        assertThat(txs.get(0).getType()).isEqualTo(TransactionType.HOLD_CANCEL);
        assertThat(txs.get(0).getWalletId()).isEqualTo(winnerWalletId);
        assertThat(txs.get(0).getAmount()).isEqualByComparingTo("450.00");
        assertThat(txs.get(0).getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(txs.get(0).getAvailableBalanceAfter()).isEqualByComparingTo("600.00");
        assertThat(txs.get(1).getType()).isEqualTo(TransactionType.FORFEIT);
        assertThat(txs.get(1).getWalletId()).isEqualTo(winnerWalletId);
        assertThat(txs.get(1).getAmount()).isEqualByComparingTo("50.00");
        assertThat(txs.get(1).getAvailableBalanceBefore()).isEqualByComparingTo("600.00");
        assertThat(txs.get(1).getAvailableBalanceAfter()).isEqualByComparingTo("600.00");
        assertThat(txs.get(2).getType()).isEqualTo(TransactionType.FORFEIT);
        assertThat(txs.get(2).getWalletId()).isEqualTo(platformWalletId);
        assertThat(txs.get(2).getAmount()).isEqualByComparingTo("50.00");
        assertThat(txs.get(2).getAvailableBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(txs.get(2).getAvailableBalanceAfter()).isEqualByComparingTo("1050.00");
        txs.forEach(tx -> assertThat(tx.getReferenceId()).isEqualTo(auctionId));

        assertThat(lock.getStatus()).isEqualTo(DepositLockStatus.FORFEITED);
        assertThat(lock.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(lock);
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.FORFEITED);
        assertThat(h.getCompletedAt()).isNotNull();
        verify(paymentHoldRepository).save(h);

        PaymentEvent event = capturedEvent();
        assertThat(event.getPaymentType()).isEqualTo("FAILED");
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getUserId()).isEqualTo(winnerUserId);
        assertThat(event.getSellerId()).isEqualTo(sellerUserId);
        assertThat(event.getAmount()).isEqualByComparingTo("500.00");
        assertThat(event.getDepositAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getRemaining()).isEqualByComparingTo("450.00");
    }

    @Test
    void forfeitExpiredHold_notFunded_forfeitsDepositWithoutHoldCancel() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", false, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "150.00", "100.00", "50.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("100.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("50.00");
        List<Transaction> txs = savedTransactions(2);
        assertThat(txs).extracting(Transaction::getType).containsOnly(TransactionType.FORFEIT);
    }

    @Test
    void forfeitExpiredHold_depositLargerThanBid_forfeitsWholeDeposit() {
        DepositLock lock = depositLock("600.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "500.00", "0.00", true, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "0.00", "600.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("0.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("600.00");
        assertThat(capturedEvent().getDepositAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void forfeitExpiredHold_noDepositLock_releasesHeldFundsAndForfeitsNothing() {
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("500.00");
        List<Transaction> txs = savedTransactions(1);
        assertThat(txs.get(0).getType()).isEqualTo(TransactionType.HOLD_CANCEL);
        verify(walletRepository, never()).save(platform);
        verifyNoInteractions(depositLockRepository);
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.FORFEITED);
        assertThat(capturedEvent().getDepositAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void forfeitExpiredHold_depositNoLongerLocked_forfeitsNothingButClosesHold() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.RELEASED);
        PaymentHold h = hold("500.00", "50.00", "450.00", false, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "100.00", "100.00", "0.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        verify(transactionRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(depositLockRepository, never()).save(any());
        assertThat(lock.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.FORFEITED);
        verify(paymentHoldRepository).save(h);
        assertThat(capturedEvent().getDepositAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void forfeitExpiredHold_notYetDue_isNoOp() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.PENDING_PAYMENT);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository, depositLockRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_alreadyCompleted_isNoOp() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.COMPLETED);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository, depositLockRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_lockedByAnotherInstance_isNoOp() {
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.empty());

        paymentService.forfeitExpiredHold(auctionId);

        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository, depositLockRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_platformWalletMissing_throwsAndChangesNothing() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));
        ReflectionTestUtils.setField(paymentService, "platformUserId", platformUserId.toString());
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.forfeitExpiredHold(auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.PENDING_PAYMENT);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(transactionRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_locksHoldThenWinnerFirstWhenWinnerIdIsLower() {
        winnerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        stubForfeit(h, wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00"),
                wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00"));

        paymentService.forfeitExpiredHold(auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdateSkipLocked(auctionId);
        order.verify(walletRepository).findByIdForUpdate(winnerWalletId);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
    }

    @Test
    void forfeitExpiredHold_locksPlatformFirstWhenPlatformIdIsLower() {
        winnerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        stubForfeit(h, wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00"),
                wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00"));

        paymentService.forfeitExpiredHold(auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdateSkipLocked(auctionId);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
        order.verify(walletRepository).findByIdForUpdate(winnerWalletId);
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `PaymentServiceImpl` doesn't implement `forfeitExpiredHold`.

- [ ] **Step 4: Implement**

In `src/main/java/com/bidnow/wallet/service/impl/PaymentServiceImpl.java`:

(a) Add this field below the existing `deadlineHours` field:

```java
    @Value("${wallet.platform-user-id}")
    private String platformUserId;
```

(b) Add this method after `confirmPayment`, before the private `lockWallet` helper:

```java
    @Override
    @Transactional
    public void forfeitExpiredHold(UUID auctionId) {
        // Lock order: hold row (skipped if another instance holds it), then wallets in ascending id order.
        Optional<PaymentHold> maybeHold = paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId);
        if (maybeHold.isEmpty()) {
            return;
        }
        PaymentHold hold = maybeHold.get();
        LocalDateTime now = LocalDateTime.now();
        if (hold.getStatus() != PaymentHoldStatus.PENDING_PAYMENT || !now.isAfter(hold.getDeadline())) {
            return;
        }

        UUID platformWalletId = walletRepository.findIdByUserId(UUID.fromString(platformUserId))
                .orElseThrow(() -> new NotFoundException("Platform wallet not found for userId: " + platformUserId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
        Wallet winner;
        Wallet platform;
        if (hold.getWinnerWalletId().compareTo(platformWalletId) < 0) {
            winner = lockWallet(hold.getWinnerWalletId());
            platform = lockWallet(platformWalletId);
        } else {
            platform = lockWallet(platformWalletId);
            winner = lockWallet(hold.getWinnerWalletId());
        }

        boolean winnerChanged = false;
        BigDecimal remaining = hold.getRemainingAmount();
        if (hold.isFundsHeld() && remaining.signum() > 0) {
            BigDecimal balanceBefore = winner.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.add(remaining);
            winner.setAvailableBalance(balanceAfter);
            winner.setLockedBalance(winner.getLockedBalance().subtract(remaining));
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.HOLD_CANCEL)
                    .amount(remaining)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Payment hold released on forfeit for auction " + auctionId)
                    .build());
            winnerChanged = true;
        }

        BigDecimal forfeited = BigDecimal.ZERO;
        if (hold.getDepositLockId() != null) {
            Optional<DepositLock> maybeLock = depositLockRepository.findById(hold.getDepositLockId())
                    .filter(l -> l.getStatus() == DepositLockStatus.LOCKED);
            if (maybeLock.isPresent()) {
                DepositLock lock = maybeLock.get();
                forfeited = lock.getAmount();
                if (forfeited.signum() > 0) {
                    winner.setLockedBalance(winner.getLockedBalance().subtract(forfeited));
                    winner.setTotalBalance(winner.getTotalBalance().subtract(forfeited));
                    transactionRepository.save(Transaction.builder()
                            .walletId(winner.getId())
                            .type(TransactionType.FORFEIT)
                            .amount(forfeited)
                            .availableBalanceBefore(winner.getAvailableBalance())
                            .availableBalanceAfter(winner.getAvailableBalance())
                            .referenceId(auctionId)
                            .status(TransactionStatus.COMPLETED)
                            .description("Deposit forfeited for auction " + auctionId)
                            .build());
                    BigDecimal platformBefore = platform.getAvailableBalance();
                    platform.setAvailableBalance(platformBefore.add(forfeited));
                    platform.setTotalBalance(platform.getTotalBalance().add(forfeited));
                    walletRepository.save(platform);
                    transactionRepository.save(Transaction.builder()
                            .walletId(platform.getId())
                            .type(TransactionType.FORFEIT)
                            .amount(forfeited)
                            .availableBalanceBefore(platformBefore)
                            .availableBalanceAfter(platform.getAvailableBalance())
                            .referenceId(auctionId)
                            .status(TransactionStatus.COMPLETED)
                            .description("Forfeited deposit from auction " + auctionId)
                            .build());
                    winnerChanged = true;
                }
                lock.setStatus(DepositLockStatus.FORFEITED);
                lock.setReleasedAt(now);
                depositLockRepository.save(lock);
            }
        }
        if (winnerChanged) {
            walletRepository.save(winner);
        }

        hold.setStatus(PaymentHoldStatus.FORFEITED);
        hold.setCompletedAt(now);
        paymentHoldRepository.save(hold);

        eventPublisher.publishEvent(new PaymentApplicationEvent(this, PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(hold.getWinnerUserId())
                .sellerId(hold.getSellerUserId())
                .amount(hold.getTotalAmount())
                .depositAmount(forfeited)
                .remaining(remaining)
                .paymentType("FAILED")
                .build()));

        log.info("Payment hold forfeited for auctionId={}, winner={}, forfeited={}",
                auctionId, hold.getWinnerUserId(), forfeited);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. That's the 27 existing tests plus the 11 new ones, 38 in total. Report the exact number you see.

---

### Task 2: `ForfeitScheduler` with scheduling enabled

**Files:** `application.yml`, `SchedulingConfig`, `ForfeitScheduler`, `ForfeitSchedulerTest` (see the File Map).

**Interfaces:**
- Consumes (Task 1): `PaymentHoldRepository.findExpiredAuctionIds(PaymentHoldStatus, LocalDateTime, Pageable)`, `PaymentService.forfeitExpiredHold(UUID)` and `PaymentHoldStatus.PENDING_PAYMENT`.
- Produces:
  - `ForfeitScheduler(PaymentHoldRepository, PaymentService, int batchSize)`, an explicit constructor where `batchSize` comes from `@Value("${wallet.payment.forfeit-batch-size:100}")`
  - `ForfeitScheduler.forfeitExpiredHolds(): void`
  - `SchedulingConfig` (`@Configuration @EnableScheduling`)

- [ ] **Step 1: Add the config keys**

In `src/main/resources/application.yml`, the `wallet:` block currently reads:

```yaml
wallet:
  platform-user-id: 87754524-54c7-4aa4-b6fb-43d359c2121f
  payment:
    deadline-hours: 48
```

Replace it with:

```yaml
wallet:
  platform-user-id: 87754524-54c7-4aa4-b6fb-43d359c2121f
  payment:
    deadline-hours: 48
    forfeit-interval-ms: 300000
    forfeit-batch-size: 100
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/bidnow/wallet/scheduler/ForfeitSchedulerTest.java`:

```java
package com.bidnow.wallet.scheduler;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForfeitSchedulerTest {

    @Mock
    private PaymentHoldRepository paymentHoldRepository;

    @Mock
    private PaymentService paymentService;

    private ForfeitScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ForfeitScheduler(paymentHoldRepository, paymentService, 25);
    }

    @Test
    void forfeitExpiredHolds_queriesPendingExpiredWithBatchSize() {
        when(paymentHoldRepository.findExpiredAuctionIds(any(), any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        scheduler.forfeitExpiredHolds();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentHoldRepository).findExpiredAuctionIds(eq(PaymentHoldStatus.PENDING_PAYMENT), now.capture(), page.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
        assertThat(page.getValue()).isEqualTo(PageRequest.of(0, 25));
        verifyNoInteractions(paymentService);
    }

    @Test
    void forfeitExpiredHolds_forfeitsEveryExpiredHold() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findExpiredAuctionIds(any(), any(), any())).thenReturn(List.of(a, b));

        scheduler.forfeitExpiredHolds();

        verify(paymentService).forfeitExpiredHold(a);
        verify(paymentService).forfeitExpiredHold(b);
    }

    @Test
    void forfeitExpiredHolds_continuesAfterOneFailure() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(paymentHoldRepository.findExpiredAuctionIds(any(), any(), any())).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("db down")).when(paymentService).forfeitExpiredHold(a);

        scheduler.forfeitExpiredHolds();

        verify(paymentService).forfeitExpiredHold(b);
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl wallet-service -am test -Dtest=ForfeitSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, because `ForfeitScheduler` doesn't exist yet.

- [ ] **Step 4: Implement**

`src/main/java/com/bidnow/wallet/config/SchedulingConfig.java`:

```java
package com.bidnow.wallet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

`src/main/java/com/bidnow/wallet/scheduler/ForfeitScheduler.java`:

```java
package com.bidnow.wallet.scheduler;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Forfeits payment holds whose 48h deadline passed without payment. Not transactional: each hold is
 * forfeited in its own transaction; a failure is logged and the hold is retried on the next run.
 * Safe on several instances — {@code forfeitExpiredHold} locks the hold row with SKIP LOCKED.
 */
@Slf4j
@Component
public class ForfeitScheduler {

    private final PaymentHoldRepository paymentHoldRepository;
    private final PaymentService paymentService;
    private final int batchSize;

    public ForfeitScheduler(PaymentHoldRepository paymentHoldRepository,
                            PaymentService paymentService,
                            @Value("${wallet.payment.forfeit-batch-size:100}") int batchSize) {
        this.paymentHoldRepository = paymentHoldRepository;
        this.paymentService = paymentService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${wallet.payment.forfeit-interval-ms:300000}")
    public void forfeitExpiredHolds() {
        List<UUID> auctionIds = paymentHoldRepository.findExpiredAuctionIds(
                PaymentHoldStatus.PENDING_PAYMENT, LocalDateTime.now(), PageRequest.of(0, batchSize));
        int succeeded = 0;
        for (UUID auctionId : auctionIds) {
            try {
                paymentService.forfeitExpiredHold(auctionId);
                succeeded++;
            } catch (RuntimeException ex) {
                log.error("Forfeit failed for auctionId={}", auctionId, ex);
            }
        }
        if (!auctionIds.isEmpty()) {
            log.info("Forfeit run: {} expired holds found, {} processed without error", auctionIds.size(), succeeded);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -pl wallet-service -am test -Dtest=ForfeitSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 tests).

---

### Task 3: Documentation

**Files:** `docs/epics/wallet/epic.md`, `docs/architecture.md`

**Interfaces:** none. This task is docs only.

- [ ] **Step 1: Wallet epic — forfeit section**

In `docs/epics/wallet/epic.md`, replace the whole `**Forfeit Logic (Non-Payment):**` block, from its heading line through the line `  - Notification to seller: "Auction failed. Winner did not pay."`, with:

```markdown
**Forfeit Logic (Non-Payment) — WALLET-306:**

- [ ] `ForfeitScheduler` runs every `wallet.payment.forfeit-interval-ms` (5 min), reads up to `forfeit-batch-size` (100) `payment_holds` in `PENDING_PAYMENT` with `deadline < now`
- [ ] Each hold forfeited in its own transaction: hold row `FOR UPDATE SKIP LOCKED` (safe on several instances), re-check still pending and past deadline, then winner + platform wallets locked in ascending id order
- [ ] Held remainder returned to the winner (HOLD_CANCEL ledger row)
- [ ] Whole LOCKED deposit moved to the platform wallet (`wallet.platform-user-id`): FORFEIT rows on both wallets; `deposit_locks.status = FORFEITED`
- [ ] No LOCKED deposit → nothing forfeited, hold still FORFEITED
- [ ] `payment_holds.status = FORFEITED`; after commit `PaymentEvent{paymentType=FAILED, depositAmount=forfeited}` on `payment-event-topic`
- [ ] Failures logged per hold and retried on the next run
- [ ] Notifications (winner: deposit forfeited; seller: auction failed) — media-service, later story
```

- [ ] **Step 2: Wallet epic — follow-ups and known gaps**

In the same file:

(a) Replace the line starting with `- [ ] Follow-ups: auction-service consumes \`PaymentEvent\` to set` (in the WALLET-305 block) with:

```markdown
- [ ] Follow-ups: auction-service consumes `PaymentEvent` (`REQUIRED` → `payment_deadline`, `COMPLETED` → `winner_paid_at`, `FAILED` → mark the sale failed)
```

(b) Replace the known-gaps bullet that starts with `- WALLET-306 (forfeit) must ship with WALLET-305:` with:

```markdown
- Lock ordering rule for any future flow touching payment holds: lock the `payment_holds` row before wallets, and order wallet locks with Java `UUID.compareTo` (as confirm, cancel and forfeit do). Forfeited deposits go entirely to the platform wallet; a seller share is an open question.
```

- [ ] **Step 3: Architecture**

In `docs/architecture.md`, in the `### Wallet Events (Kafka)` table, change the `payment-event-topic` row's payload cell from `` `PaymentEvent { paymentType: REQUIRED \| COMPLETED, … }` `` to `` `PaymentEvent { paymentType: REQUIRED \| COMPLETED \| FAILED, … }` ``. Change its purpose cell to:

```markdown
Payment required (with deadline, `insufficientFunds`) / payment completed / payment failed (deposit forfeited by the 5-minute forfeit scheduler); after commit.
```

---

### Task 4: Full verification

**Files:** none modified.

- [ ] **Step 1: Run the full wallet-service suite**

Run (from `backend/`): `mvn -pl wallet-service -am clean test`
Expected: BUILD SUCCESS with 0 failures. The new tests are `PaymentServiceImplTest` (+11) and `ForfeitSchedulerTest` (3).

- [ ] **Step 2: Manual smoke test (only if Postgres, Kafka and wallet-service are running)**

1. Create a hold by running the WALLET-305 flow (publish an `AuctionEndedEvent` with a winner). Then set it to expired: `UPDATE payment_holds SET deadline = now() - interval '1 minute' WHERE auction_id = '<id>';`
2. Wait one interval, or restart with `WALLET_PAYMENT_FORFEIT_INTERVAL_MS=10000`. Expected:
   - the hold and the deposit lock are FORFEITED
   - the winner's held remainder is back in available
   - the platform wallet grew by the deposit
   - `payment-event-topic` has a `FAILED` message
   - the logs show that the query rendered `for update skip locked`
3. Call `POST /api/v1/wallets/payments/confirm` for that auction. Expected: 409 `PAYMENT_NOT_PENDING`.

If no stack is available, skip this step and report it as not run.
