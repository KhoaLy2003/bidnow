# Wallet Deposit & Transaction History — Design Spec

**Issue:** [WALLET-302](https://github.com/KhoaLy2003/bidnow/issues/100)
**Date:** 2026-07-03
**Author:** Hiep Nguyen
**Depends on:** WALLET-301 (wallets table + wallet initialization)

---

## Overview

Add mock deposit and transaction history to the wallet service. A user can credit their wallet via `POST /api/v1/wallet/deposit` (auto-succeeds, no payment gateway) and view a paginated, filterable transaction log via `GET /api/v1/wallet/transactions`. The schema is designed to be gateway-ready for a future VNPay/Stripe integration with minimal migration cost.

---

## Data Model

### `transactions` table — migration `02-init-transactions.sql`

```sql
id                        UUID          PK DEFAULT gen_random_uuid()
wallet_id                 UUID          NOT NULL REFERENCES wallets(id)
type                      VARCHAR(20)   NOT NULL
amount                    DECIMAL(19,4) NOT NULL
available_balance_before  DECIMAL(19,4) NOT NULL
available_balance_after   DECIMAL(19,4) NOT NULL
reference_id              UUID          NULL      -- cross-service ref: auction_id, bid_id, etc.
description               TEXT          NULL
status                    VARCHAR(20)   NOT NULL
payment_gateway_tx_id     VARCHAR(100)  NULL      -- [Phase 2] VNPay / Stripe reference
metadata                  JSONB         NULL      -- [Phase 2] extra context
created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
```

Indexes: `wallet_id`, `type`, `created_at`.

**No `updated_at`** — transaction records are immutable. Corrections are new transactions, not updates.

### `available_balance_before` / `available_balance_after`

These columns always track `availableBalance` (the user's spendable funds), not `totalBalance`.

Rationale: for `HOLD` transactions, `totalBalance` is unchanged (funds move from available to locked), so recording `totalBalance` would appear as a no-op. `availableBalance` reflects what actually changes from the user's perspective for every transaction type.

### `TransactionType` enum

| Value | Phase | Trigger |
|---|---|---|
| `DEPOSIT` | 1 | User adds funds |
| `HOLD` | 1 | Bid placed — locks `availableBalance` |
| `HOLD_CANCEL` | 1 | Outbid or auction cancelled — releases locked funds |
| `PAYMENT` | 1 | Won auction — held funds transferred |
| `FORFEIT` | 1 | Winner fails to complete payment |
| `REFUND` | 1 | Platform returns funds (dispute, cancellation) |
| `FEE` | 2 | Platform commission |
| `WITHDRAWAL` | 2 | User pulls funds out |

`PAYMENT` and `FORFEIT` implicitly settle a `HOLD` (single transaction, not two).

### `TransactionStatus` enum

`PENDING` | `COMPLETED` | `FAILED`

Mock deposits always write `COMPLETED`. `PENDING` and `FAILED` are reserved for Phase 2 gateway integration.

### `Transaction` entity

Does **not** extend `BaseEntity` (which adds `updatedAt`). Uses `@CreationTimestamp` on `created_at` directly to enforce immutability.

---

## Gateway-Readiness

The schema supports future payment gateway integration (VNPay / Stripe) without a destructive migration:

- `status` already has `PENDING` / `FAILED` values for async gateway flows
- `payment_gateway_tx_id` stores the gateway's transaction reference (null for mock)
- `reference_id` stores internal cross-service references (auction_id, bid_id), separate from the gateway reference
- `metadata JSONB` absorbs gateway-specific fields that don't warrant dedicated columns

When a real gateway is added, the deposit endpoint will return a payment URL and set `status = PENDING`. The balance credit and Kafka event move to a new webhook handler. No schema migration required.

---

## Deposit Flow — `POST /api/v1/wallet/deposit`

### Request / Response

```
POST /api/v1/wallet/deposit
Body:     { "amount": 500.00 }           -- amount must be > 0
Response: { "transactionId": "...", "newBalance": 600.00, "status": "COMPLETED" }
```

### Service — `WalletServiceImpl.deposit()` (`@Transactional`)

1. Load wallet by `userId` → `NotFoundException` if absent
2. Reject if `wallet.status != ACTIVE` → `BadRequestException`
3. Snapshot `availableBalanceBefore = wallet.availableBalance`
4. Increment `wallet.availableBalance += amount`, `wallet.totalBalance += amount`, save
5. Create `Transaction` (`type=DEPOSIT`, `status=COMPLETED`, before/after balances), save
6. Publish `DepositCompletedApplicationEvent` (Spring internal event)
7. Return `DepositResponse`

### Kafka Event — `WalletEventPublisher`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onDepositCompleted(DepositCompletedApplicationEvent event) { ... }
```

Publishes `DepositReceivedEvent` to `deposit-received-topic` only after the DB commit succeeds. If the commit fails, the event is never emitted — no orphaned events.

`DepositCompletedApplicationEvent` is internal to `wallet-service`. `DepositReceivedEvent` (fields: `userId`, `walletId`, `amount`, `newBalance`) lives in `common/dto/event`.

---

## Transaction History — `GET /api/v1/wallet/transactions`

### Request / Response

```
GET /api/v1/wallet/transactions
  ?type=DEPOSIT           (optional — TransactionType enum)
  &startDate=2026-01-01   (optional — ISO date, inclusive)
  &endDate=2026-06-01     (optional — ISO date, inclusive)
  &page=0                 (default 0)
  &size=10                (default 10)

Response: PageResponse<TransactionResponse>
  data[]: { id, type, amount, availableBalanceBefore, availableBalanceAfter,
            description, status, createdAt }
  pagination: { page, limit, total, totalPages, hasNext, hasPrev }
```

### Service — `WalletServiceImpl.getTransactions()` (`@Transactional(readOnly = true)`)

1. Load wallet by `userId` → `NotFoundException` if absent
2. Build `Specification<Transaction>` via `SpecificationBuilder`:
   - `walletId = wallet.id` (always applied)
   - `type = type` (skipped if null)
   - `createdAt BETWEEN startDate.atStartOfDay() AND endDate.plusDays(1).atStartOfDay()` (skipped if either is null; end is exclusive start-of-next-day so the full endDate is included)
3. Query `TransactionRepository` (extends `JpaSpecificationExecutor`) with `Sort.by("createdAt").descending()`
4. Return `PageResponse.of(page.map(this::toTransactionResponse))`

---

## New Files

### `wallet-service`

```
domain/entity/Transaction.java
domain/enums/TransactionType.java
domain/enums/TransactionStatus.java
dto/request/DepositRequest.java
dto/response/DepositResponse.java
dto/response/TransactionResponse.java
repository/TransactionRepository.java
kafka/WalletEventPublisher.java
kafka/DepositCompletedApplicationEvent.java
db/changelog/migrations/02-init-transactions.sql
```

### Modified in `wallet-service`

```
WalletService.java                          — add deposit(), getTransactions()
WalletServiceImpl.java                      — implement both
WalletController.java                       — POST /deposit, GET /transactions
db/changelog/db.changelog-master.xml        — include 02-init-transactions.sql
```

### `common` module

```
dto/event/DepositReceivedEvent.java
```

---

## Tests

### `WalletServiceImplTest` (Mockito unit tests)

| Test | Verifies |
|---|---|
| `deposit_happyPath` | `availableBalance` and `totalBalance` each increase by amount; transaction saved with correct before/after; Spring event published |
| `deposit_inactiveWallet` | throws `BadRequestException` |
| `deposit_walletNotFound` | throws `NotFoundException` |
| `getTransactions_noFilters` | returns paged results scoped to wallet |
| `getTransactions_typeFilter` | spec includes type equality condition |
| `getTransactions_dateRange` | spec includes between condition on `createdAt` |

### `WalletControllerTest` (`@WebMvcTest` slice)

| Test | Verifies |
|---|---|
| `deposit_amountZero` | `400 Bad Request` |
| `deposit_amountNegative` | `400 Bad Request` |
| `deposit_missingAmount` | `400 Bad Request` |
| `getTransactions_invalidTypeEnum` | `400 Bad Request` |

---

## Definition of Done

- [ ] `POST /api/v1/wallet/deposit` auto-succeeds and credits `availableBalance` + `totalBalance`
- [ ] `DEPOSIT` transaction recorded with correct `available_balance_before` / `available_balance_after`
- [ ] `DEPOSIT_RECEIVED` Kafka event emitted via `@TransactionalEventListener` after DB commit
- [ ] `GET /api/v1/wallet/transactions` returns paginated results sorted by `createdAt DESC`
- [ ] Type filter and date range filter work independently and together
- [ ] `amount > 0` validation enforced (`400` otherwise)
- [ ] Swagger docs generated (no `springdoc` UI per service — only `/v3/api-docs`)
- [ ] All unit tests pass
