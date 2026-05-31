# Wallet & Financial Management — Design Spec

**Epic:** EPIC-003
**Phase:** 1 (MVP — Mock Payment Gateway)
**Date:** 2026-05-21
**Status:** Approved for implementation planning
**Currency:** USD only (Phase 2: multi-currency)

---

## 1. Scope Changes from Original Epic

The following decisions were made during design review and override the original epic (#49):

| Topic | Original Epic | This Spec |
|---|---|---|
| Currency | VND | **USD only** (Phase 2: multi-currency) |
| Platform fee | 5% of winning bid | **No fee for MVP** (Phase 2) |
| Auction registration | Explicit pre-registration step | **Removed** — first bid = implicit registration |
| Deposit → payment | Deposit + full bid required separately | **Deposit counts toward payment** |
| Forfeit logic | Deduct deposit from `available_balance` | **Cancel HOLD → restore balance → deduct deposit** |
| Refund timing | Batch every 15 min | **Near-immediate, per-bidder, event-driven** |
| Idempotency | Not covered | **Guard via `deposit_locks.status`** |
| `locked_balance` | Virtual/derived | **Stored column, updated atomically** |

---

## 2. Data Model

### `wallets`

```sql
id                UUID        PRIMARY KEY
user_id           UUID        NOT NULL UNIQUE  -- FK to identity-service user
total_balance     DECIMAL(19,4) NOT NULL DEFAULT 0
available_balance DECIMAL(19,4) NOT NULL DEFAULT 0
locked_balance    DECIMAL(19,4) NOT NULL DEFAULT 0  -- stored for query perf;
                                                     -- invariant: total = available + locked;
                                                     -- always updated atomically with others
currency          VARCHAR(3)  NOT NULL DEFAULT 'USD' -- [Phase 2] multi-currency
status            VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE | FROZEN | CLOSED
created_at        TIMESTAMP   NOT NULL
updated_at        TIMESTAMP   NOT NULL
```

> **Invariant enforced in code:** `total_balance = available_balance + locked_balance` must hold after every write. All three fields are updated in the same DB transaction.

### `transactions`

Append-only ledger — no updates or deletes after creation.

```sql
id                    UUID        PRIMARY KEY
wallet_id             UUID        NOT NULL  -- FK to wallets
type                  VARCHAR(15) NOT NULL
                      -- Phase 1: DEPOSIT | REFUND | PAYMENT | FORFEIT | HOLD | HOLD_CANCEL
                      -- Phase 2: FEE | WITHDRAWAL
amount                DECIMAL(19,4) NOT NULL
balance_before        DECIMAL(19,4) NOT NULL
balance_after         DECIMAL(19,4) NOT NULL
reference_id          VARCHAR(36)   -- auction_id, bid_id, or other reference
description           TEXT
status                VARCHAR(10) NOT NULL  -- PENDING | COMPLETED | FAILED
payment_gateway_tx_id VARCHAR(100) NULL     -- [Phase 2] VNPay / Stripe reference
metadata              JSONB        NULL     -- [Phase 2] extra context
created_at            TIMESTAMP   NOT NULL
```

### `deposit_locks`

Tracks per-user per-auction deposit state. Also serves as the **implicit registration record** and **idempotency guard** for refunds.

```sql
id           UUID        PRIMARY KEY
wallet_id    UUID        NOT NULL  -- FK to wallets
auction_id   UUID        NOT NULL
amount       DECIMAL(19,4) NOT NULL
status       VARCHAR(10) NOT NULL  -- LOCKED | RELEASED | FORFEITED
locked_at    TIMESTAMP   NOT NULL
released_at  TIMESTAMP   NULL
created_at   TIMESTAMP   NOT NULL

UNIQUE (wallet_id, auction_id)
```

> A `LOCKED` row means the user has implicitly registered for that auction.
> Before processing any refund, check `status`. If already `RELEASED` → skip (idempotency).

### `withdrawal_requests` — [Phase 2, not implemented in Phase 1]

```sql
id           UUID        PRIMARY KEY
wallet_id    UUID        NOT NULL
amount       DECIMAL(19,4) NOT NULL
status       VARCHAR(10) NOT NULL  -- PENDING | COMPLETED | FAILED
bank_details JSONB       NOT NULL  -- bank account info
gateway_ref  VARCHAR(100) NULL     -- VNPay payout reference
created_at   TIMESTAMP   NOT NULL
updated_at   TIMESTAMP   NOT NULL
```

### Platform System Wallet

A special wallet is seeded at startup for the platform account. Its `user_id` maps to a fixed system UUID defined in `application.yml` (`wallet.platform-user-id`). Forfeited deposits transfer to this wallet via standard FORFEIT transactions, keeping all financial flows in the `transactions` table.

---

## 3. Event & API Flows

### Flow 1: Wallet Initialization

**Trigger:** Kafka event `USER_REGISTERED`

```
Wallet Service consumes USER_REGISTERED { userId }
  → Check: wallet already exists for user_id? (idempotency)
  → If not: INSERT wallet (total=0, available=0, locked=0, USD, ACTIVE)
```

### Flow 2: Mock Deposit

**Trigger:** `POST /api/v1/wallet/deposit { amount }`

```
1. Create DEPOSIT transaction (status=PENDING)
2. Simulate gateway — auto-success in Phase 1
3. SELECT FOR UPDATE wallet
4. total_balance += amount
5. available_balance += amount
6. Update transaction status = COMPLETED
7. Publish DEPOSIT_RECEIVED → Media Service sends confirmation email
```

### Flow 3: First Bid = Implicit Registration + Deposit Lock

**Trigger:** Bidding Service calls Wallet Service via Feign before accepting a bid.

```
Bidding Service receives bid { userId, auctionId, bidAmount }
  → GET /api/v1/internal/wallet/deposit-lock?userId=&auctionId=
      → Row found with status=LOCKED → return OK (already registered, skip)
      → No row found → lock deposit:
           SELECT FOR UPDATE wallet
           If available_balance < deposit_amount → return 400 (bid rejected)
           INSERT deposit_locks (LOCKED, amount=deposit_amount)
           available_balance -= deposit_amount
           locked_balance    += deposit_amount
           Create HOLD transaction (amount=deposit_amount, ref=auctionId)
  → Bidding Service places bid
```

> `deposit_amount` is a field on the auction record, set by the seller at auction creation.

### Flow 4: Refund — Losing Bidders

**Trigger:** Kafka event `AUCTION_ENDED { auctionId, winnerId, loserIds[] }`

Each loser is processed independently (not as a single batch):

```
For each loserId in loserIds:
  Find deposit_lock where (wallet_id=loserId's wallet, auction_id, status=LOCKED)
  If status = RELEASED → skip (idempotency guard)
  If status = LOCKED:
    SELECT FOR UPDATE wallet
    available_balance += deposit_amount
    locked_balance    -= deposit_amount
    Create REFUND transaction
    Update deposit_lock: status=RELEASED, released_at=now
    Publish DEPOSIT_REFUNDED → Media Service notifies bidder
```

> Processing per-bidder means one failure does not block other refunds. Each is retried independently via Kafka retry/DLQ.

### Flow 5: Winner Payment

**Step 5a — On Auction End**

**Trigger:** Kafka event `AUCTION_ENDED_WITH_WINNER { auctionId, winnerId, winningBidAmount }`

```
deposit_amount  = winner's deposit_lock.amount for this auction
remaining       = winningBidAmount - deposit_amount

SELECT FOR UPDATE winner's wallet
If available_balance < remaining:
  → Notify winner to top up; still create HOLD with deadline
  → (Winner must top up wallet then call /payment/confirm)

Create HOLD transaction (amount=remaining, status=PENDING, ref=auctionId)
available_balance -= remaining
locked_balance    += remaining
Set payment_deadline = now + 48 hours
Publish WINNER_PAYMENT_PENDING → Media Service notifies winner
```

**Step 5b — Winner Confirms Payment**

**Trigger:** `POST /api/v1/wallet/payment/confirm { auctionId }`

```
Find HOLD transaction (PENDING_PAYMENT) for (winnerId, auctionId)
Find deposit_lock (LOCKED) for (winnerId, auctionId)
Validate deadline not expired

Atomically (single DB transaction):
  Deduct remaining from winner:
    total_balance     -= remaining
    locked_balance    -= remaining  (releasing the HOLD)
    Create PAYMENT transaction for winner (amount=remaining)

  Apply deposit toward payment:
    total_balance     -= deposit_amount
    locked_balance    -= deposit_amount  (releasing the deposit lock)
    Update deposit_lock status = RELEASED

  Credit seller:
    SELECT FOR UPDATE seller's wallet
    total_balance     += winningBidAmount
    available_balance += winningBidAmount
    Create PAYMENT transaction for seller (amount=winningBidAmount)

  Update HOLD status = COMPLETED

Publish PAYMENT_COMPLETED → Media Service notifies winner + seller
Notify Auction Service: update auction status = COMPLETED
```

### Flow 6: Forfeit — Non-Payment

**Trigger:** Scheduled job runs every 5 minutes, finds expired HOLD transactions.

```
SELECT HOLD transactions WHERE status=PENDING AND payment_deadline < now

For each expired HOLD:
  Atomically (single DB transaction):
    Cancel HOLD:
      available_balance += remaining_amount  (restore what was held)
      locked_balance    -= remaining_amount
      Create HOLD_CANCEL transaction

    Forfeit deposit:
      total_balance     -= deposit_amount    (money leaves wallet)
      locked_balance    -= deposit_amount    (release the deposit lock)
      -- available_balance is NOT changed here: the deposit was in locked, not available
      Create FORFEIT transaction (ref=auctionId)
      Update deposit_lock status = FORFEITED

    Transfer deposit_amount to platform wallet:
      SELECT FOR UPDATE platform wallet
      total_balance     += deposit_amount
      available_balance += deposit_amount
      Create DEPOSIT transaction on platform wallet (ref=auctionId, type=FORFEIT)

  Publish PAYMENT_FAILED → Media Service notifies winner + seller
  Notify Auction Service: update auction status = FAILED
```

---

## 4. API Endpoints

### User-Facing

```
GET  /api/v1/wallet
     Response: { totalBalance, availableBalance, lockedBalance, currency, status }

GET  /api/v1/wallet/transactions?type=&startDate=&endDate=&page=&size=
     Response: paginated transaction list

POST /api/v1/wallet/deposit
     Body: { amount }
     Response: { transactionId, status }

GET  /api/v1/wallet/payment/pending
     Response: list of HOLDs with deadlines and remaining amounts

POST /api/v1/wallet/payment/confirm
     Body: { auctionId }
     Response: { status }
```

### Internal — Feign (Bidding Service → Wallet Service)

Not exposed through API Gateway. Secured by internal network only.

```
GET  /api/v1/internal/wallet/deposit-lock?userId=&auctionId=
     Response: { locked: true|false, amount }

POST /api/v1/internal/wallet/deposit-lock
     Body: { userId, auctionId, depositAmount }
     Response: 200 OK | 400 Insufficient funds

GET  /api/v1/internal/wallet/balance/{userId}
     Response: { availableBalance, totalBalance }
```

### Admin

```
GET  /api/v1/admin/wallet/{userId}           — full wallet + recent transactions
GET  /api/v1/admin/transactions              — platform-wide, paginated + filterable
GET  /api/v1/admin/wallet/stats              — platform wallet balance, active lock count
POST /api/v1/admin/wallet/{userId}/freeze    — freeze account
POST /api/v1/admin/transactions/{id}/refund  — manual refund with required audit reason
```

---

## 5. Key Components (Spring Boot Implementation)

Following existing service patterns in the codebase:

```
controller/
  WalletController.java         — user-facing endpoints
  WalletAdminController.java    — admin endpoints
  WalletInternalController.java — internal Feign endpoints

service/
  WalletService.java            — deposit, balance query
  DepositLockService.java       — lock/unlock/forfeit logic
  PaymentService.java           — winner payment confirmation
  ForfeitScheduler.java         — @Scheduled job, runs every 5 min

repository/
  WalletRepository.java
  TransactionRepository.java
  DepositLockRepository.java

kafka/
  consumer/
    UserRegisteredEventConsumer.java
    AuctionEndedEventConsumer.java
    AuctionEndedWithWinnerEventConsumer.java
  producer/
    WalletEventPublisher.java

feign/
  AuctionServiceClient.java     — notify auction of COMPLETED/FAILED status
```

> All wallet balance mutations go through a single `WalletBalanceUpdater` helper that enforces the invariant (`total = available + locked`) and creates the transaction record atomically.

---

## 6. Concurrency & Safety

- **Pessimistic locking:** Every wallet balance mutation uses `SELECT FOR UPDATE` on the wallet row.
- **Invariant enforcement:** `WalletBalanceUpdater` always updates all three balance fields together and validates `total >= 0`, `available >= 0`, `locked >= 0` before committing.
- **DB constraint:** `CHECK (total_balance >= 0 AND available_balance >= 0 AND locked_balance >= 0)` prevents negative balances at DB level.
- **Idempotency:** Before any refund or forfeit, check `deposit_locks.status`. If already `RELEASED` or `FORFEITED`, skip silently.
- **Scheduled job:** The forfeit job should use a distributed lock (e.g., via the DB or Redis) if multiple wallet-service instances are deployed, to prevent double-forfeit on the same HOLD.

---

## 7. Resolved Decisions

| Decision | Resolution |
|---|---|
| Currency | USD only (Phase 2: multi-currency) |
| Deposit amount | Seller sets fixed USD amount at auction creation |
| Platform fee | None for MVP (Phase 2: % of winning bid, seller bears it) |
| Auction registration | Removed — first bid locks deposit and registers implicitly |
| Deposit → payment | Deposit counts toward payment; winner tops up `winningBid - deposit` |
| Refund timing | Near-immediate, per-bidder event-driven on `AUCTION_ENDED` |
| Forfeit | Cancel HOLD → restore balance → deduct deposit only as penalty |
| Forfeit destination | Platform system wallet (fixed UUID via `wallet.platform-user-id` config) |
| Idempotency | Guard via `deposit_locks.status` before any refund/forfeit |
| Locking strategy | Pessimistic (`SELECT FOR UPDATE`) on wallet row |
| Withdrawal | Phase 2 — `withdrawal_requests` table in schema, not implemented |
| `locked_balance` | Stored column, updated atomically with all balance changes |
| FEE tx type | In schema ENUM, unused in Phase 1 |
| `payment_gateway_tx_id` | In schema, nullable, unused in Phase 1 |

---

## 8. Out of Scope (Phase 2)

- Real payment gateway integration (VNPay, Stripe)
- Withdrawal / cashout functionality
- Platform fee deduction and FEE transactions
- Multi-currency support and FX conversion
- KYC verification
- Fraud detection and risk scoring
- Chargeback handling
- PCI compliance

---

## 9. Open Items Before Implementation

- [ ] Confirm `deposit_amount` field name on the Auction Service entity so Bidding Service can pass it to Wallet Service
- [ ] Confirm `AUCTION_ENDED` Kafka event payload includes `loserIds[]` (or equivalent list of non-winner participants)
- [ ] Confirm `AUCTION_ENDED_WITH_WINNER` payload includes `winningBidAmount`
- [ ] Seed platform system wallet and define `wallet.platform-user-id` in `application.yml`
- [ ] Add `CHECK` constraints to wallet table migration
- [ ] Confirm distributed lock strategy for forfeit scheduler if running multiple instances

---

## References

- Original Epic: [docs/epics/wallet/epic.md](../epics/wallet/epic.md)
- Business Clarifications: [docs/business-clarifications.md](../../docs/business-clarifications.md)
- Winner Payment Flow Diagram: [docs/diagrams/05-winner-payment-flow.md](../diagrams/05-winner-payment-flow.md)
- Deposit Flow Diagram: [docs/diagrams/06-deposit-withdrawal-flow.md](../diagrams/06-deposit-withdrawal-flow.md)
- Architecture: [docs/architecture.md](../../docs/architecture.md)
- Backend CLAUDE.md: [backend/CLAUDE.md](../../backend/CLAUDE.md)
