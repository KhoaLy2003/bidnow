# WALLET-305 — Winner Payment Flow

**Issue:** [KhoaLy2003/bidnow#103](https://github.com/KhoaLy2003/bidnow/issues/103)
**Epic:** #49 — Wallet & Financial Management
**Depends on:** WALLET-303 (`deposit_locks`), WALLET-304 (`AuctionLifecycleEventConsumer`, Kafka retry/DLT)
**Consumed by:** WALLET-306 (forfeit scheduler queries `payment_holds`)
**Date:** 2026-09-28

---

## Scope

**In scope (wallet-service + `PaymentEvent` fields in `common`):**
- `payment_holds` table (migration 04), entity, repository
- `PaymentService`: `createPaymentHold`, `confirmPayment`, `getPendingPayments`, `cancelPaymentHold`
- Hook into `AuctionLifecycleEventConsumer` (ended → create hold; cancelled → void hold)
- `PaymentController`: `GET /api/v1/wallets/payments/pending`, `POST /api/v1/wallets/payments/confirm`
- `PaymentEvent` (REQUIRED / COMPLETED) on `payment-event-topic` after commit
- Unit tests, docs

**Out of scope:**
- Any auction-service change (setting `winner_paid_at` / `payment_deadline` from `PaymentEvent` → follow-up story)
- Forfeit on deadline expiry (WALLET-306)
- Media-service email content (existing TODO in `NotificationServiceImpl.handlePaymentEvent`)
- Platform/seller fees

---

## Corrections to the Issue

| Issue says | Reality | Decision |
|---|---|---|
| Consume `auction-ended-with-winner` `{…, depositAmount}` | No such topic; `AuctionEndedEvent` on `auction-ended-topic` has `winnerId`, `winningBidAmount`, `sellerId`; no `depositAmount` | Reuse `auction-ended-topic`; deposit comes from the winner's `deposit_locks` row |
| HOLD transaction with `PENDING_PAYMENT` + `deadline` column added to WALLET-301 migration | Migration already applied; `TransactionStatus` has no PENDING_PAYMENT; 1b would write a HOLD row with no money moved | New `payment_holds` table holds state; `transactions` stays an append-only ledger written only when money moves |
| `GET/POST /api/v1/wallet/payment/...` | Gateway routes only `/api/v1/wallets/**` | `/api/v1/wallets/payments/pending`, `/api/v1/wallets/payments/confirm` |
| Feign: Auction Service status → COMPLETED | Auction is already COMPLETED at close; auction-service has no internal endpoints | Emit `PaymentEvent{COMPLETED}`; auction-side consumption is a follow-up |
| New events `WINNER_PAYMENT_PENDING`, `PAYMENT_COMPLETED` | `PaymentEvent` on `payment-event-topic` already exists and media-service consumes it | Reuse `PaymentEvent` with `paymentType` REQUIRED / COMPLETED + optional fields |

---

## Decisions

| Topic | Decision |
|---|---|
| Pending-payment state | `payment_holds` row, one per auction (`UNIQUE auction_id`) |
| Deposit counted | `deposit_applied = min(LOCKED lock amount, total)`; 0 if no LOCKED lock (warn) |
| Remaining | `total − deposit_applied` (≥ 0) |
| Funds held at creation | If `available ≥ remaining` → move to locked + HOLD ledger row; else `funds_held = false`, no money moves (Scenario 1b) |
| Deadline | `now + wallet.payment.deadline-hours` (default 48) |
| Deposit excess (`deposit > total`) | Returned to winner's available on confirm (REFUND ledger row) |
| Deposit lock after payment | `RELEASED` |
| Seller credit | Exactly `total_amount`, into available; no fee |
| Cancel after hold | Void hold: return held funds (HOLD_CANCEL ledger row), status `CANCELLED` |
| Auction sync | Event only (`PaymentEvent`); no Feign |
| Lock ordering (confirm / cancel / future forfeit) | `payment_holds` row → wallet row(s), wallets in ascending id order |

---

## Data Model

### Migration `04-init-payment-holds.sql` (changeset `bidnow:wallet_004`)

```sql
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

`PaymentHoldStatus`: `PENDING_PAYMENT, COMPLETED, CANCELLED` (WALLET-306 adds `FORFEITED`).

### `PaymentEvent` (common) — added optional fields

Existing: `auctionId, auctionTitle, userId, amount, paymentType`. Added: `sellerId (UUID)`, `depositAmount (BigDecimal)`, `remaining (BigDecimal)`, `deadline (Instant)`, `insufficientFunds (Boolean)`. `paymentType` values used here: `REQUIRED`, `COMPLETED`. Additive — existing producers/consumers unaffected.

---

## Flows

### Auction ended — `AuctionLifecycleEventConsumer.onAuctionEnded`

```
failures = []
try releaseAll(auctionId, winnerId, AUCTION_LOST)            (WALLET-304; may throw DepositReleaseException)
catch RuntimeException → record
if winnerId != null:
   try paymentService.createPaymentHold(auctionId, winnerId, sellerId, winningBidAmount)
   catch RuntimeException → log, record
if any failure → rethrow (first DepositReleaseException, else a new PaymentHoldCreationException)
```

Both steps idempotent → redelivery safe.

### `createPaymentHold(auctionId, winnerUserId, sellerUserId, totalAmount)` — `@Transactional`

```
1. paymentHoldRepository.existsByAuctionId(auctionId) → return
2. winner = walletRepository.findByUserIdForUpdate(winnerUserId)   (absent → NotFoundException → retry/DLT)
3. lock = depositLockRepository.findByWalletIdAndAuctionId(winner.id, auctionId) with status LOCKED
     deposit = lock?.amount ?: 0   (warn when absent)
4. applied = min(deposit, total); remaining = total − applied
5. if remaining == 0                        → fundsHeld = true
   elif winner.available ≥ remaining        → available −= remaining; locked += remaining; save wallet
                                              ledger HOLD {COMPLETED, amount=remaining, ref=auctionId,
                                                           desc "Payment hold for auction <id>"}
                                              fundsHeld = true
   else                                     → fundsHeld = false
6. paymentHoldRepository.saveAndFlush(PaymentHold{PENDING_PAYMENT, deadline = now + deadlineHours, …})
7. publish PaymentRequiredApplicationEvent → after commit PaymentEvent{REQUIRED, userId=winner,
     sellerId, amount=total, depositAmount=applied, remaining, deadline, insufficientFunds=!fundsHeld}
```

### `confirmPayment(callerUserId, auctionId)` — `@Transactional`

```
1. hold = paymentHoldRepository.findByAuctionIdForUpdate(auctionId)      -- first read, PESSIMISTIC_WRITE
     absent or hold.winnerUserId != caller → 404 PAYMENT_HOLD_NOT_FOUND
     status != PENDING_PAYMENT             → 409 PAYMENT_NOT_PENDING
     now > deadline                        → 400 PAYMENT_DEADLINE_EXPIRED
2. sellerWalletId = walletRepository.findIdByUserId(hold.sellerUserId)   -- projection, no entity
     absent → 404 SELLER_WALLET_NOT_FOUND
   lock both wallets: findByIdForUpdate(min id), then findByIdForUpdate(max id)
3. if !fundsHeld and winner.available < remaining → 400 INSUFFICIENT_BALANCE {availableBalance, required}
4. if hold.depositLockId != null: lock = depositLockRepository.findById(...); status != LOCKED → 409 DEPOSIT_LOCK_CLOSED
5. Winner:
     before = winner.available
     if fundsHeld: winner.locked −= remaining  else: winner.available −= remaining
     winner.total −= remaining
     if lock: winner.locked −= lock.amount; winner.total −= applied; excess = lock.amount − applied
              winner.available += excess; lock.status = RELEASED, releasedAt = now
     ledger PAYMENT {winner wallet, amount = total, before/after = winner.available, ref=auctionId,
                     desc "Payment for auction <id>"}
     if excess > 0: ledger REFUND {amount = excess, desc "Deposit excess refund for auction <id>"}
6. Seller: sBefore = seller.available; seller.available += total; seller.total += total
     ledger PAYMENT {seller wallet, amount = total, before/after, ref=auctionId, desc "Sale proceeds for auction <id>"}
7. hold.status = COMPLETED; hold.completedAt = now
8. publish PaymentCompletedApplicationEvent → after commit PaymentEvent{COMPLETED, userId=winner, sellerId, amount=total}
Return ConfirmPaymentResponse { auctionId, amountPaid=total, depositApplied, remainingPaid=remaining,
                                availableBalance, lockedBalance }   (winner's balances after)
```

Winner's `total` decreases by exactly `total_amount`; seller's increases by exactly `total_amount`. DB checks (`chk_balances_non_negative`, `chk_balance_invariant`) back-stop both wallets.

### Auction cancelled — `onAuctionCancelled`

```
failures = []
try releaseAll(auctionId, null, AUCTION_CANCELLED) catch → record
try paymentService.cancelPaymentHold(auctionId)   catch → record
rethrow if any failed
```

`cancelPaymentHold(auctionId)` — `@Transactional`:
```
1. hold = findByAuctionIdForUpdate(auctionId); absent or status != PENDING_PAYMENT → return
2. winner = walletRepository.findByIdForUpdate(hold.winnerWalletId)
3. if fundsHeld and remaining > 0: locked −= remaining; available += remaining;
     ledger HOLD_CANCEL {amount = remaining, ref=auctionId, desc "Payment hold cancelled for auction <id>"}
4. hold.status = CANCELLED
```

The deposit itself is refunded by `releaseAll` (WALLET-304). No extra event.

### `getPendingPayments(userId)` — read-only

`findByWinnerUserIdAndStatusOrderByDeadlineAsc(userId, PENDING_PAYMENT)` → `PendingPaymentResponse { auctionId, totalAmount, depositApplied, remaining, fundsHeld, deadline, hoursLeft = max(0, floor(hours until deadline)), expired = now > deadline }`.

---

## API

| Method | Path | Auth | Body | Success | Errors |
|---|---|---|---|---|---|
| GET | `/api/v1/wallets/payments/pending` | `@AuthenticatedUserId` | — | `BaseResponse<List<PendingPaymentResponse>>` | — |
| POST | `/api/v1/wallets/payments/confirm` | `@AuthenticatedUserId` | `{ auctionId }` (`@NotNull`) | `BaseResponse<ConfirmPaymentResponse>` | 400 `PAYMENT_DEADLINE_EXPIRED`, 400 `INSUFFICIENT_BALANCE` (`errors{availableBalance, required}`), 400 `INVALID_INPUT`, 404 `PAYMENT_HOLD_NOT_FOUND`, 404 `SELLER_WALLET_NOT_FOUND`, 409 `PAYMENT_NOT_PENDING`, 409 `DEPOSIT_LOCK_CLOSED` |

Config: `wallet.payment.deadline-hours: 48`.

---

## Testing

JUnit 5 + Mockito; controller via standalone MockMvc (as `WalletInternalControllerTest`).

**`PaymentServiceImplTest`**
- create: sufficient (Scenario 1: 600/0 → 150/450, HOLD ledger, fundsHeld); insufficient (1b: no balance change, no ledger, fundsHeld=false); deposit ≥ total (remaining 0, no HOLD); no LOCKED lock (applied 0, remaining = total); duplicate (exists → nothing); winner wallet missing (throws); event fields
- confirm: success fundsHeld (Scenario 3 numbers: winner total −500, seller +500, lock RELEASED, 2 PAYMENT rows, hold COMPLETED, event); success !fundsHeld with enough available; !fundsHeld insufficient (400, nothing saved); expired (400); not pending (409); other user's hold (404); absent (404); seller wallet missing (404); deposit lock no longer LOCKED (409); deposit excess refunded (REFUND row); wallets locked in ascending id order (InOrder)
- cancel: fundsHeld → HOLD_CANCEL + CANCELLED; !fundsHeld → CANCELLED only; not pending → no-op
- pending: mapping, hoursLeft floor, expired flag

**`PaymentControllerTest`** — 200 bodies, each error code/status, validation.

**`AuctionLifecycleEventConsumerTest`** (extended) — ended with winner creates hold; hold still attempted when loser release fails, rethrows; no winner → no hold; cancelled → `cancelPaymentHold` called; hold failure rethrows after releases.

**`WalletEventPublisherTest`** (extended) — REQUIRED and COMPLETED payloads on `payment-event-topic`, key = winner userId.

**Known gap:** no DB-level test of `payment_holds` constraints, pessimistic locks, or migration 04 (no Testcontainers) — manual smoke when the stack is available.

---

## Documentation Updates

- `docs/architecture.md` — wallet produces `payment-event-topic` (REQUIRED / COMPLETED); public payment endpoints
- `docs/epics/wallet/epic.md` — winner payment flow as built; known gaps: auction-service `winner_paid_at` consumer; forfeit (WALLET-306) must lock `payment_holds` row before wallets

---

## Follow-ups

- Auction-service: consume `PaymentEvent{REQUIRED|COMPLETED}` → set `payment_deadline` / `winner_paid_at`
- WALLET-306: forfeit expired `PENDING_PAYMENT` holds (add `FORFEITED`), same lock order
- Media-service: payment emails
