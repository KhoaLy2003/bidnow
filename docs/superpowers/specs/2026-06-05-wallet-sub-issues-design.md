# Wallet Epic — Sub-Issue Breakdown Design

**Epic:** [#49 EPIC-003 Wallet & Financial Management](https://github.com/KhoaLy2003/bidnow/issues/49)
**Date:** 2026-06-05
**Author:** KhoaLy2003
**Milestone:** v0.2-auction-wallet
**Primary Developer:** hiepnguyen06

---

## Overview

This document defines the breakdown of Epic #49 into 7 GitHub sub-issues. Each issue is a self-contained unit of work assignable to the primary developer. Issues are ordered by dependency; WALLET-301 must be completed first, WALLET-307 can run in parallel with 302–306.

**Total estimate:** 44 story points (~15 days development)

---

## Issue Dependency Order

```
WALLET-301 (DB Schema + Bootstrap + Wallet Init)
    ├── WALLET-302 (Mock Deposit + Transaction History)
    ├── WALLET-303 (Deposit Lock — Feign Integration)
    │       ├── WALLET-304 (Refund Logic)
    │       └── WALLET-305 (Winner Payment Flow)
    │               └── WALLET-306 (Forfeit Scheduler)
    └── WALLET-307 (Admin Endpoints)  ← parallel with 302–306
```

---

## Issues

---

### WALLET-301 — DB Schema, Service Bootstrap & Wallet Initialization

**Story ID:** `WALLET-301`
**Issue Type:** Feature
**Priority:** P0 — Critical
**Assignee:** hiepnguyen06
**Story Points:** 5 (~2 days)
**Depends On:** —

**As a** platform user, **I want** my wallet to be automatically created when I register, **so that** I can immediately add funds and participate in auctions.

#### Acceptance Criteria

**Scenario 1: Wallet auto-created on user registration**
- Given the Identity Service publishes `USER_REGISTERED { userId }`
- When Wallet Service consumes the event
- Expected: wallet created with `totalBalance = 0`, `availableBalance = 0`, `lockedBalance = 0`, `currency = USD`, `status = ACTIVE`. Duplicate events do not create a second wallet.

**Scenario 2: Get wallet balance**
- Given I am authenticated
- When I call `GET /api/v1/wallet`
- Expected: returns `{ totalBalance, availableBalance, lockedBalance, currency, status }`. Returns `404` if no wallet found.

**Scenario 3: Platform system wallet seeded at startup**
- Given the service starts and `wallet.platform-user-id` is set in `application.yml`
- When no platform wallet exists yet
- Expected: one platform wallet is auto-created with `status = ACTIVE`, `totalBalance = 0`. Restart is idempotent.

**Scenario 4: Balance invariant always holds**
- Given any write operation on a wallet
- When the operation completes
- Expected: `totalBalance = availableBalance + lockedBalance` is enforced in code and by DB CHECK constraint.

#### Technical Notes

**Liquibase migrations:**
- `wallets` — CHECK `(total_balance >= 0 AND available_balance >= 0 AND locked_balance >= 0)`, UNIQUE on `user_id`
- `transactions` — append-only; no DELETE/UPDATE after insert
- `deposit_locks` — UNIQUE `(wallet_id, auction_id)`
- `withdrawal_requests` — schema only (Phase 2, not implemented)

**Key classes:** `UserRegisteredEventConsumer`, `WalletController`, `WalletService`, `WalletInitializer`
**Kafka topic consumed:** `user-registered`
**API:** `GET /api/v1/wallet` → `{ totalBalance, availableBalance, lockedBalance, currency, status }`

#### Dependencies
- ✅ Identity Service publishes `USER_REGISTERED`
- ✅ Kafka and PostgreSQL configured

#### Definition of Done
- [ ] Liquibase migrations for all 4 tables with constraints
- [ ] `USER_REGISTERED` consumer creates wallet idempotently
- [ ] Platform system wallet seeded at startup
- [ ] `GET /api/v1/wallet` returns balance and status
- [ ] Balance invariant enforced in code
- [ ] Swagger docs generated
- [ ] Code reviewed and approved

---

### WALLET-302 — Mock Deposit & Transaction History

**Story ID:** `WALLET-302`
**Issue Type:** Feature
**Priority:** P0 — Critical
**Assignee:** hiepnguyen06
**Story Points:** 5 (~2 days)
**Depends On:** WALLET-301

**As a** registered user, **I want** to add funds to my wallet and view my transaction history, **so that** I have balance available for auction participation.

#### Acceptance Criteria

**Scenario 1: Successful mock deposit**
- Given I am authenticated with an active wallet
- When I call `POST /api/v1/wallet/deposit` with `{ "amount": 500.00 }`
- Expected: `totalBalance` and `availableBalance` each increase by 500.00. A `DEPOSIT` transaction is recorded (status = COMPLETED) with correct `balanceBefore`/`balanceAfter`. `DEPOSIT_RECEIVED` Kafka event emitted.

**Scenario 2: Invalid deposit amount**
- Given I call `POST /api/v1/wallet/deposit`
- When `amount <= 0` or missing
- Expected: `400 Bad Request` with validation error.

**Scenario 3: View transaction history — default**
- Given I am authenticated
- When I call `GET /api/v1/wallet/transactions`
- Expected: paginated list sorted by `createdAt DESC`, default page size 10. Each entry: `id`, `type`, `amount`, `balanceBefore`, `balanceAfter`, `description`, `createdAt`.

**Scenario 4: Filter by type**
- Given `GET /api/v1/wallet/transactions?type=DEPOSIT`
- Expected: only DEPOSIT transactions returned.

**Scenario 5: Filter by date range**
- Given `GET /api/v1/wallet/transactions?startDate=2026-01-01&endDate=2026-06-01`
- Expected: only transactions with `createdAt` within that range.

**Scenario 6: Empty history**
- Given no transactions exist
- Expected: `200 OK`, empty `data[]`, `totalItems: 0`.

#### Technical Notes

```
POST /api/v1/wallet/deposit         Body: { amount }
                                    Response: { transactionId, newBalance, status }
GET  /api/v1/wallet/transactions    Query: type?, startDate?, endDate?, page?, pageSize?
                                    Response: { data: [...], pagination: { currentPage, pageSize, totalPages, totalItems } }
```

**Key classes:** `WalletController`, `WalletService.deposit()`, `TransactionRepository`, `WalletEventPublisher`
**Kafka event emitted:** `DEPOSIT_RECEIVED { userId, walletId, amount, newBalance }`

#### Dependencies
- ✅ WALLET-301

#### Definition of Done
- [ ] `POST /api/v1/wallet/deposit` auto-succeeds and credits balance
- [ ] DEPOSIT transaction created with correct before/after balances
- [ ] `DEPOSIT_RECEIVED` event emitted
- [ ] `GET /api/v1/wallet/transactions` with pagination, type filter, date filter
- [ ] `amount > 0` validation enforced
- [ ] Swagger docs generated
- [ ] Code reviewed and approved

---

### WALLET-303 — Deposit Lock (Bidding Service Feign Integration)

**Story ID:** `WALLET-303`
**Issue Type:** Feature
**Priority:** P0 — Critical
**Assignee:** hiepnguyen06
**Story Points:** 8 (~3 days)
**Depends On:** WALLET-301

**As the** Bidding Service, **I want** to check and lock a user's deposit on their first bid, **so that** only financially committed users can participate in auctions.

#### Acceptance Criteria

**Scenario 1: Check lock — not locked yet**
- Given user has never bid on auction 123
- When Bidding Service calls `GET /api/v1/internal/wallet/deposit-lock?userId=X&auctionId=123`
- Expected: `{ locked: false }`

**Scenario 2: Check lock — already locked**
- Given user already has a LOCKED deposit_lock for auction 123
- Expected: `{ locked: true, amount: 50.00 }`. Bidding Service skips locking.

**Scenario 3: Successful deposit lock (first bid)**
- Given `availableBalance = 200.00`, auction deposit = `50.00`
- When Bidding Service calls `POST /api/v1/internal/wallet/deposit-lock { userId, auctionId, depositAmount: 50.00 }`
- Expected: `availableBalance → 150.00`, `lockedBalance += 50.00`, `totalBalance` unchanged. `deposit_locks` row inserted (status = LOCKED). HOLD transaction created. Returns `200 OK`.

**Scenario 4: Insufficient balance**
- Given `availableBalance = 30.00`, `depositAmount = 50.00`
- Expected: `400 Bad Request { error: "INSUFFICIENT_BALANCE", availableBalance: 30.00, required: 50.00 }`

**Scenario 5: Wallet not found**
- Expected: `404 Not Found`

**Scenario 6: Concurrent lock requests (race condition)**
- Given two simultaneous first-bid requests for the same user + auction
- Expected: exactly one balance deduction occurs. Second call returns `200 OK` (already locked, idempotent). Pessimistic `SELECT FOR UPDATE` prevents double-deduction.

**Scenario 7: Balance check endpoint**
- When Bidding Service calls `GET /api/v1/internal/wallet/balance/{userId}`
- Expected: `{ availableBalance, totalBalance, lockedBalance }`

#### Technical Notes

```
GET  /api/v1/internal/wallet/deposit-lock?userId=&auctionId=
POST /api/v1/internal/wallet/deposit-lock    Body: { userId, auctionId, depositAmount }
GET  /api/v1/internal/wallet/balance/{userId}
```

These endpoints are **not routed through the API Gateway**.

**Idempotency flow:**
```
SELECT * FROM deposit_locks WHERE wallet_id=? AND auction_id=?
  → LOCKED: return OK (skip)
  → null:   SELECT FOR UPDATE wallet → insert lock + deduct balance
```

**Key classes:** `WalletInternalController`, `DepositLockService`, `@Lock(PESSIMISTIC_WRITE)`

#### Dependencies
- ✅ WALLET-301
- ⚠️ Bidding Service must call these endpoints before accepting first bid per auction

#### Definition of Done
- [ ] `GET` deposit-lock returns status correctly
- [ ] `POST` deposit-lock locks atomically with `SELECT FOR UPDATE`
- [ ] Idempotent: duplicate lock on same (userId, auctionId) is safe
- [ ] `400` with clear body for insufficient balance
- [ ] `GET /api/v1/internal/wallet/balance/{userId}` implemented
- [ ] Endpoints excluded from API Gateway routing
- [ ] Code reviewed and approved

---

### WALLET-304 — Refund Logic (Losing Bidders)

**Story ID:** `WALLET-304`
**Issue Type:** Feature
**Priority:** P0 — Critical
**Assignee:** hiepnguyen06
**Story Points:** 8 (~3 days)
**Depends On:** WALLET-303

**As a** losing bidder, **I want** my locked deposit automatically refunded when the auction ends, **so that** my funds are freed up immediately.

#### Acceptance Criteria

**Scenario 1: Refund all losers on AUCTION_ENDED**
- Given auction 123 ended with winner W, losers = [A, B, C], depositAmount = 50.00
- When Wallet Service consumes `AUCTION_ENDED { auctionId, winnerId, loserIds[], depositAmount }`
- Expected: for each loser independently — `availableBalance += 50.00`, `lockedBalance -= 50.00`, `totalBalance` unchanged. REFUND transaction appended. `deposit_locks.status = RELEASED`, `releasedAt = now()`. `DEPOSIT_REFUNDED` event emitted per loser.

**Scenario 2: Idempotent — already refunded**
- Given the same `AUCTION_ENDED` event is consumed twice (Kafka duplicate)
- When second processing finds `deposit_locks.status = RELEASED`
- Expected: no balance change, no duplicate transaction, silent skip.

**Scenario 3: Loser with no deposit lock**
- Given a `loserId` who never placed a bid (no deposit_lock row)
- Expected: skip silently. No error.

**Scenario 4: Per-loser failure isolation**
- Given loser A's refund fails (DB error)
- Expected: losers B and C are still refunded in their own transactions. A's failure is logged and retried via Kafka retry / DLQ.

#### Technical Notes

**Kafka topic consumed:** `auction-ended`
**Payload:** `{ auctionId, winnerId, loserIds[], depositAmount }`

**Processing per loser (independent DB transaction):**
```
1. Find deposit_lock WHERE (walletId, auctionId) AND status = LOCKED
2. If not found or status != LOCKED → skip
3. SELECT FOR UPDATE wallet
4. available += amount, locked -= amount
5. INSERT REFUND transaction
6. UPDATE deposit_locks SET status = RELEASED, released_at = now()
7. Emit DEPOSIT_REFUNDED { userId, auctionId, amount }
```

**Key classes:** `AuctionEndedEventConsumer`, `DepositLockService.releaseDeposit()`
**Kafka event emitted:** `DEPOSIT_REFUNDED { userId, auctionId, amount }`

#### Dependencies
- ✅ WALLET-303 — deposit_locks rows exist
- ⚠️ Auction Service must publish `AUCTION_ENDED` with `loserIds[]` and `depositAmount`

#### Definition of Done
- [ ] `AUCTION_ENDED` consumer processes each loser in an independent DB transaction
- [ ] Balance updated atomically with `SELECT FOR UPDATE`
- [ ] REFUND transaction appended with correct before/after balances
- [ ] `deposit_locks.status` set to RELEASED
- [ ] Idempotency guard: skip if already RELEASED
- [ ] `DEPOSIT_REFUNDED` event emitted per loser
- [ ] Code reviewed and approved

---

### WALLET-305 — Winner Payment Flow

**Story ID:** `WALLET-305`
**Issue Type:** Feature
**Priority:** P0 — Critical
**Assignee:** hiepnguyen06
**Story Points:** 8 (~3 days)
**Depends On:** WALLET-303

**As an** auction winner, **I want** a 48-hour window to confirm payment (with my deposit counting toward the total), **so that** I can complete the purchase without full-amount pressure at bid time.

#### Acceptance Criteria

**Scenario 1: Payment hold created on AUCTION_ENDED_WITH_WINNER — sufficient balance**
- Given auction 123 ends: winner W, `winningBidAmount = 500.00`, `depositAmount = 50.00`, winner `availableBalance = 600.00`
- When Wallet Service consumes `AUCTION_ENDED_WITH_WINNER { auctionId, winnerId, winningBidAmount, depositAmount, sellerId }`
- Expected: `remaining = 450.00`. `availableBalance -= 450.00 → 150.00`, `lockedBalance += 450.00`. HOLD transaction created (status = PENDING_PAYMENT, deadline = now + 48h). `WINNER_PAYMENT_PENDING { insufficientFunds: false }` event emitted.

**Scenario 1b: Payment hold created — insufficient balance for remaining**
- Given winner `availableBalance = 100.00`, `remaining = 450.00`
- Expected: HOLD still created for remaining (status = PENDING_PAYMENT, deadline = now + 48h) but balance is NOT deducted yet. `WINNER_PAYMENT_PENDING { insufficientFunds: true }` event emitted. Winner must top up before confirming payment.

**Scenario 2: View pending payments**
- When winner calls `GET /api/v1/wallet/payment/pending`
- Expected: list of PENDING_PAYMENT HOLDs with `auctionId`, `totalAmount`, `depositAmount`, `remaining`, `deadline`, `hoursLeft`.

**Scenario 3: Confirm payment — success**
- Given valid PENDING_PAYMENT HOLD, deadline not expired
- When winner calls `POST /api/v1/wallet/payment/confirm { auctionId: 123 }`
- Expected (atomic):
  - Release HOLD: `lockedBalance -= remaining`, `totalBalance -= remaining`
  - Apply deposit: `lockedBalance -= depositAmount`, `totalBalance -= depositAmount`
  - `deposit_locks.status = RELEASED`
  - Seller credited: `totalBalance += 500.00`, `availableBalance += 500.00`
  - PAYMENT transaction created for winner and seller
  - HOLD status → COMPLETED
  - `PAYMENT_COMPLETED` event emitted
  - Auction Service notified: status → COMPLETED

**Scenario 4: Confirm payment — deadline expired**
- Expected: `400 Bad Request { error: "PAYMENT_DEADLINE_EXPIRED" }`

**Scenario 5: Confirm payment — no pending hold**
- Expected: `404 Not Found`

#### Technical Notes

```
GET  /api/v1/wallet/payment/pending
POST /api/v1/wallet/payment/confirm    Body: { auctionId }
```

**Key classes:** `AuctionEndedWithWinnerEventConsumer`, `PaymentService.createPaymentHold()`, `PaymentService.confirmPayment()`, `AuctionServiceClient` (Feign)

**Note on schema:** The `transactions` table needs a `deadline` column (TIMESTAMP, nullable) to support the 48-hour payment window on HOLD records. Add this in the WALLET-301 Liquibase migration.

**Kafka topics:**
- Consumed: `auction-ended-with-winner`
- Emitted: `winner-payment-pending`, `payment-completed`

#### Dependencies
- ✅ WALLET-303 — winner's deposit_lock exists
- ⚠️ Auction Service must publish `AUCTION_ENDED_WITH_WINNER` with `sellerId`, `depositAmount`, `winningBidAmount`; must expose status update endpoint

#### Definition of Done
- [ ] `AUCTION_ENDED_WITH_WINNER` consumer creates PENDING_PAYMENT HOLD
- [ ] `GET /api/v1/wallet/payment/pending` returns HOLDs with deadline info
- [ ] `POST /api/v1/wallet/payment/confirm` settles atomically (winner debited, seller credited)
- [ ] PAYMENT transactions created for both winner and seller
- [ ] `PAYMENT_COMPLETED` event emitted
- [ ] Auction Service notified: status → COMPLETED
- [ ] `400` on expired deadline; `404` if no pending hold
- [ ] Code reviewed and approved

---

### WALLET-306 — Forfeit Scheduler

**Story ID:** `WALLET-306`
**Issue Type:** Feature
**Priority:** P1 — High
**Assignee:** hiepnguyen06
**Story Points:** 5 (~2 days)
**Depends On:** WALLET-305

**As the** platform, **I want** deposits automatically forfeited when winners miss the 48-hour payment deadline, **so that** bad-faith bidding is penalised and auctions resolve cleanly.

#### Acceptance Criteria

**Scenario 1: Expired hold detected and forfeited**
- Given a PENDING_PAYMENT HOLD with `deadline < now()`
- When the scheduler runs (every 5 minutes)
- Expected (atomic per expired hold):
  - HOLD_CANCEL transaction: `availableBalance += remaining`, `lockedBalance -= remaining`
  - FORFEIT transaction: `totalBalance -= depositAmount`, `lockedBalance -= depositAmount`
  - `deposit_locks.status = FORFEITED`
  - Platform system wallet: `totalBalance += depositAmount`, `availableBalance += depositAmount`
  - `PAYMENT_FAILED` event emitted
  - Auction Service notified: status → FAILED

**Scenario 2: No expired holds**
- Expected: no operations, no errors.

**Scenario 3: Idempotent — hold already forfeited**
- Given a HOLD already with `status = FORFEITED`
- Expected: skipped silently.

**Scenario 4: Partial failure resilience**
- Given wallet forfeit succeeds but Auction Service Feign call fails
- Expected: wallet forfeit committed, `PAYMENT_FAILED` event emitted, Feign retry attempted separately.

#### Technical Notes

**Key classes:** `ForfeitScheduler` (`@Scheduled(fixedDelay = 300_000)`), `DepositLockService.forfeitDeposit()`, `AuctionServiceClient`

**Query for expired holds** (requires `deadline` column added in WALLET-301):
```sql
SELECT * FROM transactions
WHERE type = 'HOLD' AND status = 'PENDING_PAYMENT' AND deadline < NOW()
```

**Kafka event emitted:** `PAYMENT_FAILED { winnerId, auctionId, forfeitedAmount }`

#### Dependencies
- ✅ WALLET-305 — PENDING_PAYMENT HOLDs exist
- ⚠️ Auction Service status update endpoint (reused from WALLET-305)

#### Definition of Done
- [ ] Scheduler runs every 5 minutes detecting expired HOLDs
- [ ] HOLD_CANCEL + FORFEIT transactions created atomically
- [ ] `deposit_locks.status` → FORFEITED
- [ ] Platform system wallet credited with forfeited deposit amount
- [ ] `PAYMENT_FAILED` event emitted
- [ ] Auction Service notified: status → FAILED
- [ ] Already-forfeited HOLDs skipped (idempotent)
- [ ] Code reviewed and approved

---

### WALLET-307 — Admin Endpoints

**Story ID:** `WALLET-307`
**Issue Type:** Feature
**Priority:** P1 — High
**Assignee:** hiepnguyen06
**Story Points:** 5 (~2 days)
**Depends On:** WALLET-301 (parallel with 302–306)

**As an** admin, **I want** visibility into wallet balances, transactions, and manual controls, **so that** I can monitor platform financial health and resolve disputes.

#### Acceptance Criteria

**Scenario 1: View user wallet details**
- Given authenticated as ADMIN
- When `GET /api/v1/admin/wallet/{userId}`
- Expected: full wallet (`totalBalance`, `availableBalance`, `lockedBalance`, `status`), last 20 transactions, active deposit locks.

**Scenario 2: Platform-wide transaction list**
- When `GET /api/v1/admin/transactions?page=1&pageSize=50`
- Expected: paginated list of all transactions across all wallets, sorted by `createdAt DESC`.

**Scenario 3: Filter transactions by type**
- When `GET /api/v1/admin/transactions?type=FORFEIT`
- Expected: only FORFEIT transactions returned.

**Scenario 4: Platform wallet stats**
- When `GET /api/v1/admin/wallet/stats`
- Expected: `{ platformWalletBalance, totalActiveWallets, totalLockedBalance, totalActiveDepositLocks }`.

**Scenario 5: Freeze a user wallet**
- When `POST /api/v1/admin/wallet/{userId}/freeze`
- Expected: wallet `status → FROZEN`. Deposits and deposit locks blocked while FROZEN. Returns `200 OK`.

**Scenario 6: Manual refund**
- When `POST /api/v1/admin/transactions/{transactionId}/refund { reason: "Customer dispute" }`
- Expected: new REFUND transaction created (original unchanged — append-only). `availableBalance` credited. Audit includes admin userId + reason. Returns new transaction ID.

**Scenario 7: Unauthorized access**
- Given non-admin caller
- Expected: `403 Forbidden`.

#### Technical Notes

```
GET  /api/v1/admin/wallet/{userId}
GET  /api/v1/admin/transactions?type?&page?&pageSize?
GET  /api/v1/admin/wallet/stats
POST /api/v1/admin/wallet/{userId}/freeze
POST /api/v1/admin/transactions/{transactionId}/refund    Body: { reason }
```

**Key classes:** `WalletAdminController`, `WalletAdminService`
**Authorization:** `@PreAuthorize("hasRole('ADMIN')")` on all endpoints

#### Dependencies
- ✅ WALLET-301
- ✅ Identity Service provides `ADMIN` role in JWT

#### Definition of Done
- [ ] All 5 endpoints implemented with `ADMIN` role guard
- [ ] `GET /stats` returns correct platform-wide aggregates
- [ ] Freeze sets wallet status to FROZEN; deposit lock blocked on FROZEN wallets
- [ ] Manual refund creates new REFUND transaction without modifying original
- [ ] `403` for non-admin callers
- [ ] Swagger docs generated
- [ ] Code reviewed and approved

---

## Summary Table

| Story ID    | Title                                      | Points | Depends On           |
|-------------|---------------------------------------------|--------|----------------------|
| WALLET-301  | DB Schema, Bootstrap & Wallet Init          | 5      | —                    |
| WALLET-302  | Mock Deposit & Transaction History          | 5      | WALLET-301           |
| WALLET-303  | Deposit Lock (Feign Integration)            | 8      | WALLET-301           |
| WALLET-304  | Refund Logic (Losing Bidders)               | 8      | WALLET-303           |
| WALLET-305  | Winner Payment Flow                         | 8      | WALLET-303           |
| WALLET-306  | Forfeit Scheduler                           | 5      | WALLET-305           |
| WALLET-307  | Admin Endpoints                             | 5      | WALLET-301           |
| **Total**   |                                             | **44** |                      |
