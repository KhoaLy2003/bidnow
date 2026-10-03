# WALLET-306 — Forfeit Scheduler

**Issue:** [KhoaLy2003/bidnow#104](https://github.com/KhoaLy2003/bidnow/issues/104)
**Epic:** #49 — Wallet & Financial Management
**Depends on:** WALLET-305 (`payment_holds`, `PaymentService`, `PaymentEvent` fields), WALLET-303 (`deposit_locks`)
**Date:** 2026-09-28

---

## Scope

**In scope (wallet-service only):**
- `@EnableScheduling` + `ForfeitScheduler` scanning expired `PENDING_PAYMENT` holds every 5 minutes
- `PaymentService.forfeitExpiredHold(auctionId)` — per-hold atomic forfeit
- `PaymentHoldStatus.FORFEITED`, two repository queries, two config keys
- `PaymentEvent{paymentType=FAILED}` after commit
- Unit tests, docs

**Out of scope:**
- Any auction-service change (consuming `PaymentEvent{FAILED}` to mark the auction → follow-up story, same as REQUIRED/COMPLETED)
- Payment reminders (`REMINDER_24H`)
- Seller compensation from forfeited deposits (all goes to the platform wallet)
- ShedLock / distributed scheduler locks

---

## Corrections to the Issue

| Issue says | Reality | Decision |
|---|---|---|
| Query `transactions WHERE type='HOLD' AND status='PENDING_PAYMENT' AND deadline < NOW()` | WALLET-305 stores pending payments in `payment_holds` | Query `payment_holds (status, deadline)` (index exists) |
| `DepositLockService.forfeitDeposit()` | The forfeit spans hold + deposit lock + two wallets; lives with the other hold operations | `PaymentService.forfeitExpiredHold(auctionId)` |
| Feign: Auction Service status → FAILED | Auction-service exposes no status endpoint; WALLET-305 chose events | Emit `PaymentEvent{FAILED}`; auction-side consumption is a follow-up |
| Event `PAYMENT_FAILED { winnerId, auctionId, forfeitedAmount }` | `PaymentEvent` on `payment-event-topic` already carries winner/auction/amount fields | `PaymentEvent{paymentType=FAILED, depositAmount=forfeited}` |
| Scenario 4 "Feign retry attempted separately" | No Feign call | N/A — event is best-effort after commit, like all wallet events |

---

## Decisions

| Topic | Decision |
|---|---|
| Amount forfeited | The whole LOCKED `deposit_locks.amount` (even if larger than the bid) |
| Held remaining | Returned to the winner's available (HOLD_CANCEL ledger row) |
| Recipient | Platform wallet (`wallet.platform-user-id`, seeded by `WalletInitializer`) |
| No LOCKED deposit | Nothing forfeited; hold still → `FORFEITED`; event `depositAmount = 0` |
| Multi-instance | No scheduler lock; each hold processed in its own transaction with `FOR UPDATE SKIP LOCKED` + status/deadline re-check |
| Lock order | `payment_holds` row → wallets in ascending `UUID.compareTo` order (same as confirm/cancel) |
| Expiry test | `now > deadline` (identical to confirm's rejection test) |
| Interval / batch | `wallet.payment.forfeit-interval-ms: 300000`, `wallet.payment.forfeit-batch-size: 100` |
| Failure | Per-hold try/catch, ERROR log; the hold stays PENDING+expired and is retried next tick |

---

## Components

| Unit | Change |
|---|---|
| `domain/enums/PaymentHoldStatus` | + `FORFEITED` |
| `repository/PaymentHoldRepository` | + `List<UUID> findExpiredAuctionIds(PaymentHoldStatus status, LocalDateTime now, Pageable page)`; + `Optional<PaymentHold> findByAuctionIdForUpdateSkipLocked(UUID auctionId)` |
| `service/PaymentService` | + `void forfeitExpiredHold(UUID auctionId)` |
| `service/impl/PaymentServiceImpl` | implement; new `@Value("${wallet.platform-user-id}") String platformUserId` |
| `config/SchedulingConfig` | `@Configuration @EnableScheduling` |
| `scheduler/ForfeitScheduler` | `@Scheduled(fixedDelayString = "${wallet.payment.forfeit-interval-ms:300000}")` scan + per-hold dispatch |
| `application.yml` | `wallet.payment.forfeit-interval-ms`, `wallet.payment.forfeit-batch-size` |

No migration: `payment_holds.status` is `VARCHAR(20)` with no CHECK on values.

### Repository queries

```java
@Query("SELECT h.auctionId FROM PaymentHold h WHERE h.status = :status AND h.deadline < :now ORDER BY h.deadline ASC")
List<UUID> findExpiredAuctionIds(@Param("status") PaymentHoldStatus status,
                                 @Param("now") LocalDateTime now, Pageable page);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))   // -2 = SKIP LOCKED (Hibernate)
@Query("SELECT h FROM PaymentHold h WHERE h.auctionId = :auctionId")
Optional<PaymentHold> findByAuctionIdForUpdateSkipLocked(@Param("auctionId") UUID auctionId);
```

---

## Flows

### `ForfeitScheduler.forfeitExpiredHolds()` — not transactional

```
ids = paymentHoldRepository.findExpiredAuctionIds(PENDING_PAYMENT, now, PageRequest.of(0, batchSize))
for id in ids:
    try paymentService.forfeitExpiredHold(id)
    catch RuntimeException → log.error("Forfeit failed for auctionId={}", id, ex)
if ids non-empty → log.info("Forfeit run processed {} expired holds", ids.size())
```

### `PaymentServiceImpl.forfeitExpiredHold(auctionId)` — `@Transactional`

```
1. hold = findByAuctionIdForUpdateSkipLocked(auctionId)
     absent                         → return   (locked by another instance / removed)
     status != PENDING_PAYMENT      → return   (confirmed, cancelled, already forfeited)
     !now.isAfter(deadline)         → return   (not due)
2. platformWalletId = walletRepository.findIdByUserId(platformUserId)
     absent → NotFoundException(WALLET_NOT_FOUND)  (rolls back; retried next tick)
   lock winner (hold.winnerWalletId) + platform wallets via findByIdForUpdate, lower UUID first
3. if hold.fundsHeld and remaining > 0:
     before = winner.available
     winner.locked −= remaining; winner.available += remaining
     ledger HOLD_CANCEL {winner, remaining, before/after, ref=auctionId,
                         desc "Payment hold released on forfeit for auction <id>"}
4. forfeited = 0
   if hold.depositLockId != null:
     lock = depositLockRepository.findById(depositLockId)
     if lock present and status == LOCKED:
        forfeited = lock.amount
        winner.locked −= forfeited; winner.total −= forfeited
        ledger FORFEIT {winner, forfeited, before/after = winner.available (unchanged),
                        desc "Deposit forfeited for auction <id>"}
        pBefore = platform.available
        platform.available += forfeited; platform.total += forfeited
        ledger FORFEIT {platform, forfeited, pBefore → platform.available,
                        desc "Forfeited deposit from auction <id>"}
        lock.status = FORFEITED; lock.releasedAt = now
5. save wallets (winner always if changed; platform if forfeited > 0)
   hold.status = FORFEITED; hold.completedAt = now
6. publish PaymentApplicationEvent → after commit PaymentEvent{ paymentType=FAILED,
     auctionId, userId=winnerUserId, sellerId=sellerUserId, amount=totalAmount,
     depositAmount=forfeited, remaining=remainingAmount }
```

**Invariants:** winner `total` decreases by exactly `forfeited`; platform `total` increases by exactly `forfeited`; both keep `total = available + locked`. DB checks back-stop.

**Races:**
- Confirm vs forfeit at the deadline: both take the hold row lock; whichever runs second sees a non-PENDING status (or, for confirm, the same `now > deadline` rule) and does nothing.
- Cancel vs forfeit: same hold-row serialization; cancel after forfeit hits cancel's "not PENDING" branch.
- Two instances: `SKIP LOCKED` → the second skips the row; if it reads after commit it sees FORFEITED and returns.
- Deposit refund sweep vs forfeit: both lock the winner wallet before touching the deposit lock; forfeit reads the lock only after holding the wallet.

---

## Testing

JUnit 5 + Mockito.

**`PaymentServiceImplTest` (forfeit)**
- fundsHeld + LOCKED deposit: winner 650/150/500 (hold 500 total, 50 applied, 450 remaining) → available 600, locked 0, total 600; platform +50; HOLD_CANCEL(450) + FORFEIT(winner 50) + FORFEIT(platform 50); lock FORFEITED; hold FORFEITED with completedAt; FAILED event with depositAmount 50
- not funded: no HOLD_CANCEL, deposit still forfeited
- deposit > bid (lock 600, applied 500): forfeits 600
- no deposit lock id: forfeited 0, hold FORFEITED, no FORFEIT rows, platform untouched, event depositAmount 0
- deposit lock no longer LOCKED: same as above
- not yet due / COMPLETED / CANCELLED / skip-locked empty: no-op (no saves, no event)
- platform wallet missing: throws WALLET_NOT_FOUND, nothing saved
- lock order: InOrder hold → lower-id wallet → higher-id wallet (both orderings)
- money conservation: winner Δtotal = −platform Δtotal

**`ForfeitSchedulerTest`**
- passes `PENDING_PAYMENT`, a `now` close to the current time, and `PageRequest.of(0, batchSize)`
- forfeits every returned id; continues after one throws; no ids → no service calls

**Known gaps:** `SKIP LOCKED` hint rendering, the expiry query and `@Scheduled` wiring are not exercised without a real DB/context — manual smoke: set a hold's deadline in the past, wait one interval (or temporarily lower `forfeit-interval-ms`), verify balances, statuses and `payment-event-topic`.

---

## Documentation Updates

- `docs/epics/wallet/epic.md` — Forfeit Logic section rewritten as built (WALLET-306); known gap "WALLET-306 must ship with WALLET-305" resolved; follow-up for auction-service lists `PaymentEvent{FAILED}`
- `docs/architecture.md` — `payment-event-topic` row includes `FAILED`; note the forfeit scheduler

---

## Follow-ups

- Auction-service: consume `PaymentEvent{REQUIRED|COMPLETED|FAILED}` (payment deadline, paid-at, auction payment failure)
- Payment reminders (`REMINDER_24H`)
- Decide forfeited-deposit split with the seller (epic open question)
