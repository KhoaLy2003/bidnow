# Wallet Frontend Integration — Design

**Date:** 2026-07-04
**Status:** Approved

## Problem

The wallet UI (`WalletBadge`, `WalletPanel`, `/wallet` page) is fully mocked: `walletStore` starts at zero balance with no transactions, and "Add Funds" adds a hardcoded fake +$100 via a local mutation. Meanwhile the backend `wallet-service` already exposes three working endpoints (WALLET-302):

- `GET /api/v1/wallets` — wallet balance
- `POST /api/v1/wallets/deposit` — mock deposit
- `GET /api/v1/wallets/transactions` — paginated transaction history

This integrates those three endpoints into the existing frontend, replacing local mock state with real data.

## Scope

In scope: the 3 endpoints above. Recent-transactions list only (first page, no filter/pagination UI — a documented follow-up). Out of scope: deposit locks, refunds, payment holds, forfeits, platform fees — none of those flows exist in the backend yet.

## Naming note

`frontend/CLAUDE.md` documents amounts as being in cents. In practice, every existing usage (`formatCurrency`, `CurrencyInput`, auction prices) treats numbers as plain dollars, and the backend sends `BigDecimal` dollar amounts directly with no `*100`/`/100` conversion anywhere in the codebase. This design follows the actual code convention (dollars), not the stale doc comment.

## Types & mapping layer

New files, following the existing `types/api/auction.api.ts` + `types/mappers/auction.mapper.ts` pattern:

- **`types/api/wallet.api.ts`** — raw DTOs mirroring the Java response classes exactly:
  - `WalletResponse { totalBalance, availableBalance, lockedBalance, currency, status }`
  - `DepositRequest { amount }`, `DepositResponse { transactionId, newBalance, status }`
  - `TransactionResponse { id, type, amount, availableBalanceBefore, availableBalanceAfter, description, status, createdAt }`

- **`types/mappers/wallet.mapper.ts`**:
  - `mapWalletResponse(dto): WalletBalance` → `{ available: dto.availableBalance, held: dto.lockedBalance, total: dto.totalBalance }` — direct rename, no math.
  - `mapTransactionResponse(dto): Transaction` — maps the backend's `TransactionType` (`DEPOSIT, HOLD, HOLD_CANCEL, PAYMENT, FORFEIT, REFUND, FEE, WITHDRAWAL`) onto the UI's `TransactionType` union. `DEPOSIT→deposit`, `WITHDRAWAL→withdrawal`, `HOLD→bid_hold`, `HOLD_CANCEL→bid_release`, `PAYMENT→won_payment`, `REFUND→refund`. Two new UI variants are added for the remaining backend values with no current equivalent: `FEE→fee`, `FORFEIT→forfeit`.
  - Signed amount: `amount = dto.availableBalanceAfter >= dto.availableBalanceBefore ? dto.amount : -dto.amount` (sign computed from the balance delta, not hardcoded per type) — matches the existing UI `Transaction.amount` doc comment ("positive = credit, negative = debit") and stays correct as new transaction types start appearing from unbuilt backend flows.

## Service layer

`services/wallet.service.ts`, matching `auction.service.ts`'s conventions (uses `apiFetch`, throws parsed error body on non-ok response):

```ts
getWallet(): Promise<ApiResponse<WalletResponse>>
deposit(amount: number): Promise<ApiResponse<DepositResponse>>
getTransactions(params: { page?: number; size?: number }): Promise<ApiResponse<PageResponse<TransactionResponse>>>
```

## Store & hook

`store/walletStore.ts` — keep existing shape (`available/held/total/isLow/transactions/isLoading`), changes:
- Remove the fake `deposit(amount)` local mutator.
- Add `hasFetched: boolean` (default `false`) so parallel mounts of `WalletBadge` + `WalletPanel` don't double-fetch.
- Add `setTransactions(list: Transaction[])` (replaces the list, unlike the existing `addTransaction` prepend-er which is dropped — no more optimistic inserts, see below).

`hooks/useWallet.ts` becomes a real fetch hook (pattern: `hooks/useProfile.ts`):
- On mount, if `isAuthenticated` and `!hasFetched`: fetch wallet + first page of transactions (`page=0, size=10`) in parallel, populate the store, set `hasFetched=true`.
- Exposes `{ ...storeState, isLoading, error, refetch, depositFunds(amount) }`.
- `depositFunds(amount)`: calls `walletService.deposit(amount)`; on success, calls `refetch()` (re-fetches wallet + transactions from the server) rather than optimistically constructing a fake transaction row — guarantees the displayed transaction has the real server-issued id/timestamp/description. Returns a promise so the caller (deposit dialog) can await it for its own success/error UI.

## UI changes

- **`WalletPanel`**: "Add Funds" opens a `Dialog` (shadcn/Base UI, matching admin-page usage) containing a `CurrencyInput` and a confirm button. On confirm: call `depositFunds`, `sonner` toast on success (`"Deposit successful"`) or failure (error message from thrown response), close dialog on success. Balance card and transaction list show a skeleton (`components/ui/skeleton.tsx`) while `isLoading` is true and no data has loaded yet.
- **`TransactionRow`**: add `fee` and `forfeit` entries to `TYPE_CONFIG` (icon + label only). Remove the `positive` field from `TYPE_CONFIG` entries; derive the +/- sign and color directly from `Math.sign(transaction.amount)` instead.
- **`/wallet` page**: unchanged layout/composition — same `WalletPanel`, now backed by real data via the updated hook.

## Error handling

Fetch failures (`useWallet`) surface via a `sonner` toast and the hook's `error` field; the store keeps its last-known-good values rather than clearing to zero. Deposit failures surface via a toast inside the dialog without closing it, so the user can retry.

## Testing

- Manual verification via the running dev server: load `/wallet`, confirm real balance renders (or zero-state for a fresh test user), open the deposit dialog, submit an amount, confirm balance and transaction list update from the server response.
- No new automated test suite exists for the frontend (per `frontend/CLAUDE.md`: "No test suite is configured").
