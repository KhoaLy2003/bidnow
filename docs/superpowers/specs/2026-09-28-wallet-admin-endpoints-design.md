# WALLET-307 — Wallet Admin Endpoints

**Issue:** [KhoaLy2003/bidnow#105](https://github.com/KhoaLy2003/bidnow/issues/105)
**Epic:** #49 — Wallet & Financial Management
**Builds on:** WALLET-301..306 (wallets, transactions, deposit locks, payment holds, platform wallet)
**Date:** 2026-09-28

---

## Scope

**In scope:**
- api-gateway: route `/api/v1/admin/wallets/**` to wallet-service
- wallet-service: `WalletAdminController` + `WalletAdminService` — stats, platform-wide transaction list, user wallet detail, freeze/unfreeze, manual refund
- wallet-service: `WalletAuditEventListener` forwarding `AuditApplicationEvent` to Kafka `audit-events`
- Repository queries, error codes, unit tests, docs

**Out of scope:**
- Seller claw-backs / counterparty reversals (refunds are funded by the platform wallet)
- Partial refunds
- Frontend admin UI
- `common` module changes

---

## Corrections to the Issue

| Issue says | Reality | Decision |
|---|---|---|
| `/api/v1/admin/wallet/...` and `/api/v1/admin/transactions` | Gateway routes admin APIs per service prefix; nothing routes these to wallet-service | All endpoints under `/api/v1/admin/wallets/...`; gateway route added |
| Status `FROZEN` | `WalletStatus` has `ACTIVE`, `SUSPENDED`; SUSPENDED already blocks deposits and deposit locks | Freeze = `SUSPENDED`; add unfreeze → `ACTIVE` |
| Manual refund "availableBalance credited" | Crediting alone creates money | Platform wallet debited by the same amount |
| `page=1&pageSize=50` | Existing wallet listing uses 0-based `page` and `size` | `page` (default 0) and `size` (default 50) |
| No double-refund rule | — | Each transaction refundable once (409 on repeat) |

---

## Decisions

| Topic | Decision |
|---|---|
| Refund funding | Debit platform wallet (`wallet.platform-user-id`), credit user; 400 if platform can't cover |
| Refundable transactions | `type ∈ {PAYMENT, FORFEIT}`, wallet ≠ platform wallet, `availableBalanceAfter ≤ availableBalanceBefore` (money left the user) |
| Refund amount | Full original amount, once per transaction |
| Refund idempotency | `REFUND` row with `referenceId = original transaction id` exists → 409; checked after wallet row locks |
| Refund audit | `@Audit(ADMIN_ACTION)` + ledger `metadata` JSON `{adminId, reason, originalTransactionId, direction}` |
| Freeze / unfreeze | `SUSPENDED` / `ACTIVE`; idempotent 200; platform wallet cannot be frozen (400) |
| Frozen wallet effects | Unchanged existing behaviour: mock deposit and new deposit lock rejected; refunds, sale proceeds, confirm, releases allowed |
| Authorization | Class-level `@PreAuthorize("hasRole('ADMIN')")` → 403 via existing `AccessDeniedException` handler |
| Lock order (refund) | User wallet + platform wallet via `findByIdForUpdate`, lower `UUID.compareTo` first |

---

## API

Base path `/api/v1/admin/wallets`; all responses `ResponseEntity<BaseResponse<T>>`; Swagger `@Operation` on each.

| Method | Path | Body / params | Success `data` |
|---|---|---|---|
| GET | `/stats` | — | `WalletStatsResponse { platformWalletBalance, totalActiveWallets, totalLockedBalance, totalActiveDepositLocks }` |
| GET | `/transactions` | `type?` (TransactionType), `page=0`, `size=50` | `PageResponse<AdminTransactionResponse>` sorted `createdAt DESC` |
| GET | `/{userId}` | — | `AdminWalletDetailResponse { wallet: AdminWalletResponse, recentTransactions: List<AdminTransactionResponse> (≤20, newest first), activeDepositLocks: List<AdminDepositLockResponse> }` |
| POST | `/{userId}/freeze` | — | `AdminWalletResponse` |
| POST | `/{userId}/unfreeze` | — | `AdminWalletResponse` |
| POST | `/transactions/{transactionId}/refund` | `ManualRefundRequest { @NotBlank reason }` | `ManualRefundResponse { refundTransactionId, amount, userId, availableBalance }` |

DTOs:
- `AdminWalletResponse { walletId, userId, totalBalance, availableBalance, lockedBalance, currency, status }`
- `AdminTransactionResponse { id, walletId, userId, type, amount, availableBalanceBefore, availableBalanceAfter, referenceId, description, status, metadata, createdAt }` (`userId` resolved from the wallet)
- `AdminDepositLockResponse { id, auctionId, amount, status, lockedAt }`

Errors:

| Status | Code | When |
|---|---|---|
| 403 | (existing `ACCESS_DENIED`) | caller lacks ADMIN |
| 404 | `WALLET_NOT_FOUND` | no wallet for `userId` |
| 404 | `TRANSACTION_NOT_FOUND` | no transaction with id |
| 400 | `TRANSACTION_NOT_REFUNDABLE` | fails the refundable rule |
| 409 | `TRANSACTION_ALREADY_REFUNDED` | a REFUND referencing it exists |
| 400 | `INSUFFICIENT_BALANCE` | platform available < amount (`errors{availableBalance, required}`) |
| 400 | `INVALID_INPUT` | blank reason; freezing the platform wallet |

Gateway (`api-gateway/src/main/resources/application.yml`): wallet-service route `Path=/api/v1/wallets/**, /api/v1/admin/wallets/**`.

---

## Flows

### Manual refund — `WalletAdminServiceImpl.refundTransaction(adminId, transactionId, reason)` — `@Transactional`, `@Audit(action = ADMIN_ACTION, entityType = "Transaction", reason = "Admin manual refund")`

```
1. tx = transactionRepository.findById(transactionId) → 404 TRANSACTION_NOT_FOUND
2. platformWalletId = walletRepository.findIdByUserId(platformUserId) → 404 WALLET_NOT_FOUND
   refundable: tx.type ∈ {PAYMENT, FORFEIT} and tx.walletId != platformWalletId
               and tx.availableBalanceAfter ≤ tx.availableBalanceBefore   → else 400 TRANSACTION_NOT_REFUNDABLE
3. lock tx.walletId and platformWalletId via findByIdForUpdate, lower UUID first
4. transactionRepository.existsByTypeAndReferenceId(REFUND, tx.id) → 409 TRANSACTION_ALREADY_REFUNDED
5. platform.available < tx.amount → 400 INSUFFICIENT_BALANCE
6. platform: available −= amount; total −= amount; save
   ledger REFUND {platform wallet, amount, before/after, referenceId = tx.id, COMPLETED,
                  description "Manual refund of transaction <id>",
                  metadata {"adminId","reason","originalTransactionId","direction":"DEBIT"}}
7. user: available += amount; total += amount; save
   ledger REFUND {user wallet, ..., metadata {..., "direction":"CREDIT"}}
8. return ManualRefundResponse { refundTransactionId = user row id, amount, userId = user wallet's userId,
                                 availableBalance = user.available }
```

`metadata` is serialized with Jackson into the existing `JSONB` column (`Transaction.metadata` is a `String`).

### Freeze / unfreeze — `@Transactional`, `@Audit(action = ADMIN_ACTION, entityType = "Wallet", reason = "Admin froze wallet" / "Admin unfroze wallet")`

```
wallet = walletRepository.findByUserIdForUpdate(userId) → 404 WALLET_NOT_FOUND
freeze: wallet.userId == platformUserId → 400 INVALID_INPUT
target = SUSPENDED (freeze) / ACTIVE (unfreeze); if wallet.status != target → set, save
return AdminWalletResponse
```

### Stats — read-only

```
platformWalletBalance   = platform wallet totalBalance (0 if missing)
totalActiveWallets      = walletRepository.countByStatusAndUserIdNot(ACTIVE, platformUserId)
totalLockedBalance      = walletRepository.sumLockedBalance()          (COALESCE(SUM(locked_balance), 0))
totalActiveDepositLocks = depositLockRepository.countByStatus(LOCKED)
```

### Wallet detail — read-only

```
wallet = findByUserId(userId) → 404
recentTransactions = transactionRepository.findTop20ByWalletIdOrderByCreatedAtDesc(wallet.id)
activeDepositLocks = depositLockRepository.findByWalletIdAndStatus(wallet.id, LOCKED)
```

### Transaction list — read-only

`transactionRepository.findAll(spec(type?), PageRequest.of(page, size, Sort.by("createdAt").descending()))` using the existing `SpecificationBuilder`; `userId` per row resolved by one `walletRepository.findAllById(walletIds)` lookup per page.

### Audit forwarding

`WalletAuditEventListener` (`@Component`): `@TransactionalEventListener(phase = AFTER_COMMIT) onAudit(AuditApplicationEvent e)` → `kafkaTemplate.send("audit-events", e.getAuditLogEvent().getCorrelationId().toString(), e.getAuditLogEvent())` with error logging (mirrors identity-service). Media-service's existing `AuditKafkaConsumer` stores it; visible via `/api/v1/admin/audit-logs`.

---

## Testing

JUnit 5 + Mockito; controllers via standalone MockMvc.

**`WalletAdminServiceImplTest`**
- refund happy path: platform 1000 → 950, user +50 (available and total), two REFUND rows with `referenceId = tx.id`, metadata contains adminId/reason/originalTransactionId/direction, response fields
- refund not found (404), each non-refundable case: DEPOSIT, HOLD, REFUND, seller PAYMENT (after > before), platform-wallet FORFEIT (400)
- refund already refunded (409, nothing saved); platform insufficient (400, nothing saved)
- refund wallet lock order (InOrder, both orderings)
- freeze ACTIVE → SUSPENDED (saved); freeze already SUSPENDED (no save, 200 result); freeze platform wallet (400); unfreeze SUSPENDED → ACTIVE; wallet missing (404)
- stats mapping (including missing platform wallet → 0)
- wallet detail mapping; missing wallet 404
- transaction list: type filter passed, paging/sort, userId resolved

**`WalletAdminControllerTest`** — each endpoint 200 shape; paging defaults `page=0,size=50`; refund blank reason → 400 INVALID_INPUT; 404/409/400 mappings; `X-User-Id` resolved as adminId for refund.

**`WalletAuditEventListenerTest`** — sends to `audit-events` keyed by correlationId.

**Known gaps:** `@PreAuthorize` 403, `@Audit` aspect wiring, the gateway route, and the aggregate JPQL are not exercised without a Spring context / DB — manual smoke with a non-admin and an admin token.

---

## Documentation Updates

- `docs/architecture.md` — admin wallet route and endpoint table
- `docs/epics/wallet/epic.md` — Admin Endpoints section as built (freeze = SUSPENDED + unfreeze; platform-funded manual refunds; once per transaction)

---

## Follow-ups

- Seller claw-back / counterparty reversal for disputed payments
- Partial refunds
- Admin UI in the frontend
