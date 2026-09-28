# WALLET-303 — Deposit Lock Internal API (Wallet Side)

**Issue:** [KhoaLy2003/bidnow#101](https://github.com/KhoaLy2003/bidnow/issues/101)
**Epic:** #49 — Wallet & Financial Management
**Parent design:** `2026-05-21-wallet-financial-management-design.md` (Flow 3: First Bid = Implicit Registration + Deposit Lock)
**Date:** 2026-09-28

---

## Scope

**In scope (wallet-service only):**
- Liquibase migration `03-init-deposit-locks.sql` + `DepositLock` entity, enum, repository
- `WalletInternalController` with three internal endpoints
- `DepositLockService` with pessimistic-lock-based, idempotent lock flow
- `SecurityConfig` permitAll for `/api/v1/internal/**`
- Hardening: existing mock `deposit()` switched to `SELECT … FOR UPDATE`
- Unit tests (JUnit/Mockito) and doc updates

**Out of scope:**
- Bidding-service Feign client, DTOs, error decoder, bid-placement hook (belongs to the bid-placement story)
- Refund / forfeit / release flows (future WALLET stories)
- Testcontainers-based concurrency integration test

**Correction to issue:** the issue lists "✅ WALLET-301 — deposit_locks table exists". It does not; WALLET-301 deferred it. This story creates it.

---

## Decisions

| Topic | Decision |
|---|---|
| Deposit amount source | Trust caller (`depositAmount` in body). Wallet validates `>= 0` only; bidding-service is responsible for sourcing it from auction-service. |
| Zero deposit | Insert `LOCKED` row with `amount = 0` (still the implicit registration record). No balance change, no HOLD transaction. |
| Existing RELEASED/FORFEITED row on POST | `409 DEPOSIT_LOCK_CLOSED`. No re-locking. |
| Wallet not ACTIVE | `403 WALLET_NOT_ACTIVE` for new locks. An already-LOCKED row still returns 200 (idempotency checked first). |
| Response envelope | `BaseResponse<T>`, consistent with existing internal APIs. |
| Insufficient-balance details | `ErrorResponse.errors` map: `{ availableBalance, required }` (string values). No change to `common`. |
| Concurrency | `SELECT wallet FOR UPDATE` first → check lock → mutate. GET is the caller's fast path. `UNIQUE(wallet_id, auction_id)` as backstop. |

---

## Data Model

### Migration `03-init-deposit-locks.sql`

```sql
-- liquibase formatted sql

-- changeset bidnow:wallet_003
CREATE TABLE deposit_locks
(
    id          UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    wallet_id   UUID           NOT NULL REFERENCES wallets (id),
    auction_id  UUID           NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    locked_at   TIMESTAMP      NOT NULL,
    released_at TIMESTAMP,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_deposit_locks_wallet_auction UNIQUE (wallet_id, auction_id),
    CONSTRAINT chk_deposit_locks_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX idx_deposit_locks_auction_status ON deposit_locks (auction_id, status);
```

- The unique constraint's index covers `wallet_id` lookups.
- `(auction_id, status)` supports future refund/forfeit sweeps.
- Registered in `db.changelog-master.xml` after migration 02.

### Java

- `domain/enums/DepositLockStatus` — `LOCKED, RELEASED, FORFEITED`
- `domain/entity/DepositLock` — extends `BaseEntity`; fields mirror the table; `status` as `@Enumerated(STRING)`
- `repository/DepositLockRepository` — `Optional<DepositLock> findByWalletIdAndAuctionId(UUID walletId, UUID auctionId)`
- `WalletRepository` — add:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
  Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
  ```

---

## API Contract

Base path: `/api/v1/internal/wallet`. Not routed by the API Gateway (no route for `/api/v1/internal/**`; `AuthenticationFilter` already blocks `/api/v1/**/internal/**`). Wallet `SecurityConfig` adds `.requestMatchers("/api/v1/internal/**").permitAll()`, matching identity-service / user-service.

All success responses are `BaseResponse<T>`; errors are the common `ErrorResponse`.

### `GET /deposit-lock?userId={uuid}&auctionId={uuid}`

`data: DepositLockStatusResponse`

| Case | `data` |
|---|---|
| No row | `{ "locked": false }` |
| LOCKED | `{ "locked": true, "amount": 50.00, "status": "LOCKED", "lockedAt": "…" }` |
| RELEASED / FORFEITED | `{ "locked": false, "amount": 50.00, "status": "RELEASED", "lockedAt": "…" }` |

Errors: `404 WALLET_NOT_FOUND`.

### `POST /deposit-lock`

Request `DepositLockRequest`:
```json
{ "userId": "uuid", "auctionId": "uuid", "depositAmount": 50.00 }
```
Validation: all `@NotNull`; `depositAmount` `@DecimalMin("0.00")`.

`200 OK`, `data: DepositLockResponse`:
```json
{
  "lockId": "uuid",
  "amount": 50.00,
  "status": "LOCKED",
  "alreadyLocked": false,
  "availableBalance": 150.00,
  "lockedBalance": 50.00
}
```
`alreadyLocked = true` when an existing LOCKED row was found (idempotent replay). The balances returned are the wallet's current values.

Errors:

| Status | errorCode | When |
|---|---|---|
| 400 | `INVALID_INPUT` (existing handler) | Missing field or negative amount |
| 400 | `INSUFFICIENT_BALANCE` | `availableBalance < depositAmount`; `errors: { "availableBalance": "30.00", "required": "50.00" }` |
| 403 | `WALLET_NOT_ACTIVE` | Wallet status ≠ ACTIVE and no LOCKED row exists |
| 404 | `WALLET_NOT_FOUND` | No wallet for `userId` |
| 409 | `DEPOSIT_LOCK_CLOSED` | Row exists with status RELEASED or FORFEITED |

### `GET /balance/{userId}`

`data: WalletBalanceResponse`:
```json
{ "totalBalance": 200.00, "availableBalance": 150.00, "lockedBalance": 50.00, "currency": "USD" }
```
Errors: `404 WALLET_NOT_FOUND`.

---

## Lock Flow

`DepositLockServiceImpl.lockDeposit(userId, auctionId, amount)`, single `@Transactional`:

```
1. wallet = walletRepository.findByUserIdForUpdate(userId)     -- SELECT … FOR UPDATE; 404 if absent
2. existing = depositLockRepository.findByWalletIdAndAuctionId(wallet.id, auctionId)
     LOCKED             → return alreadyLocked=true
     RELEASED/FORFEITED → throw 409 DEPOSIT_LOCK_CLOSED
3. wallet.status != ACTIVE         → throw 403 WALLET_NOT_ACTIVE
4. wallet.available < amount       → throw 400 INSUFFICIENT_BALANCE
5. if amount > 0:
     before = wallet.available
     wallet.available -= amount
     wallet.locked    += amount     (total unchanged; DB CHECK enforces invariant)
     save Transaction { type=HOLD, status=COMPLETED, amount,
                        availableBalanceBefore=before, availableBalanceAfter=wallet.available,
                        referenceId=auctionId, description="Deposit lock for auction <id>" }
6. saveAndFlush DepositLock { walletId, auctionId, amount, status=LOCKED, lockedAt=now }
7. return alreadyLocked=false with new balances
```

**Why the row lock comes first (no unlocked pre-read):** if the wallet were first loaded without a lock, Hibernate would keep that instance in the persistence context, and the later `FOR UPDATE` query would acquire the DB lock but return the *cached, possibly stale* entity — a lost-update bug on the balance. The GET endpoint is the caller's fast path; POST is only called when GET reported `locked=false`, so always locking on POST costs nothing meaningful.

**Race safety (Scenario 6):** concurrent first-bid calls for the same user serialize on the wallet row at step 1. The second caller's step-2 query runs after the first commits (READ COMMITTED), sees the LOCKED row, and returns `alreadyLocked=true` — exactly one deduction. Locks for *different* auctions by the same user also serialize, which is required to keep `available_balance` correct.

**Backstop:** `saveAndFlush` surfaces a `uq_deposit_locks_wallet_auction` violation inside the service as `DataIntegrityViolationException`; the transaction rolls back. The controller catches it and retries `lockDeposit` once — the retry sees the existing row and returns `alreadyLocked=true` (or 409 if closed).

`getDepositLock` and `getBalance` are `@Transactional(readOnly = true)`, no row locks.

---

## Error Handling

- `InsufficientBalanceException extends BadRequestException` — errorCode `INSUFFICIENT_BALANCE`, carries `available` and `required`.
- `WalletExceptionHandler` (`@RestControllerAdvice`, `@Order(HIGHEST_PRECEDENCE)`) in wallet-service handles only `InsufficientBalanceException`, building `ErrorResponse` with the `errors` map. Everything else falls through to the common `GlobalExceptionHandler`.
- `WALLET_NOT_FOUND` → existing `NotFoundException`; `WALLET_NOT_ACTIVE` → existing `ForbiddenException`; `DEPOSIT_LOCK_CLOSED` → new `ConflictException` (409) local to wallet-service, extending `BaseException`.

---

## Hardening: Mock Deposit

`WalletServiceImpl.deposit()` currently loads the wallet via `findByUserId` (no row lock) and could race with a deposit lock. Switch it to `findByUserIdForUpdate`. Update `WalletServiceImplTest` stubs accordingly.

---

## Testing

JUnit 5 + Mockito, following existing wallet-service tests.

**`DepositLockServiceImplTest`**
- Scenario 1: GET, no row → `locked=false`
- Scenario 2: GET, LOCKED row → `locked=true, amount`
- GET, RELEASED row → `locked=false, status=RELEASED`
- Scenario 3: POST success → balances 200/0 → 150/50, total unchanged, HOLD saved with correct before/after/reference, lock saved LOCKED
- Scenario 4: insufficient → `InsufficientBalanceException` with available/required; nothing saved
- Scenario 5: wallet missing → `NotFoundException`
- Scenario 6 / idempotent replay: existing LOCKED row after `findByUserIdForUpdate` → `alreadyLocked=true`, no balance change, no saves; `findByUserId` (unlocked) never called
- Zero deposit: lock row saved, no HOLD, balances unchanged
- RELEASED/FORFEITED existing → `ConflictException`
- SUSPENDED wallet, no lock → `ForbiddenException`
- SUSPENDED wallet, existing LOCKED → 200 `alreadyLocked=true`
- Scenario 7: balance → correct three balances + currency

**`WalletInternalControllerTest`** (standalone `MockMvc` with `WalletExceptionHandler` + `GlobalExceptionHandler`, matching `UserProfileControllerTest`)
- 200 bodies wrapped in `BaseResponse` for all three endpoints
- 400 `INSUFFICIENT_BALANCE` with `errors.availableBalance` / `errors.required`
- 400 validation on missing fields / negative amount
- 403 / 404 / 409 mapping
- Unique-violation fallback returns `alreadyLocked=true`

**Known gaps:** the `permitAll` rule is verified by a manual smoke call, not an automated test. True DB-level serialization of `SELECT FOR UPDATE` is not exercised (no Testcontainers in wallet-service). Race safety relies on the pessimistic lock + unique constraint and code review.

---

## Documentation Updates

- `docs/diagrams/08-auction-registration-flow.md` — mark superseded: explicit registration replaced by first-bid implicit registration (link to parent design Flow 3).
- `docs/architecture.md` — add wallet internal endpoints under service-to-service APIs.
- Wallet API docs / epic — list the three internal endpoints and error codes.

---

## Follow-ups (not this story)

- Bidding-service `WalletServiceClient` (Feign) + error decoder mapping `INSUFFICIENT_BALANCE` / `WALLET_NOT_ACTIVE` / `DEPOSIT_LOCK_CLOSED` to bid rejections.
- Testcontainers Postgres concurrency test for Scenario 6.
- Release / forfeit flows transitioning `deposit_locks.status`.
