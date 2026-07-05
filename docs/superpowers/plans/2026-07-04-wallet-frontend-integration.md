# Wallet Frontend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fully-mocked wallet UI (`WalletBadge`, `WalletPanel`, `/wallet` page) with real data from the three live `wallet-service` endpoints (`GET /api/v1/wallets`, `POST /api/v1/wallets/deposit`, `GET /api/v1/wallets/transactions`).

**Architecture:** Add a `types/api` → `types/mappers` → `services` → `store`/`hooks` → `components` layer for wallet, mirroring the existing `auction.*` pattern exactly. The Zustand `walletStore` stays the shared cache; `useWallet()` becomes a real fetch-on-mount hook (pattern: `hooks/useProfile.ts`) instead of a thin store passthrough.

**Tech Stack:** Next.js (App Router), TypeScript strict mode, Zustand, `next-themes`/Base UI/shadcn components, `sonner` for toasts, native `fetch` via `lib/apiClient.ts`.

## Global Constraints

- Amounts are plain dollar numbers everywhere in this codebase (`formatCurrency`, `CurrencyInput`, all auction prices) — despite `frontend/CLAUDE.md` calling them "cents". Do not apply any `*100`/`/100` conversion. Backend `BigDecimal` values map straight through.
- No cents math, ever, in this feature.
- No test runner exists in `frontend/` (`frontend/CLAUDE.md`: "No test suite is configured"). Every task's verification step is `npx tsc --noEmit -p tsconfig.json` (run from `frontend/`) plus `npx eslint <changed files>` instead of a unit test run. The final task adds a manual browser smoke test.
- **Do not run `git commit` for any step in this plan.** This user commits manually. Each task's last step is `git add <files>` (staging only) — leave the commit itself to the user.
- Follow existing import/type patterns exactly: `types/api/*.api.ts` for raw DTOs, `types/mappers/*.mapper.ts` for DTO→UI conversion functions, `services/*.service.ts` using `apiFetch` from `lib/apiClient.ts` with the existing throw-parsed-json-on-!ok convention (see `services/auction.service.ts`, `services/user.service.ts`).
- Page size for the transaction list is `10`, matching `DEFAULT_PAGE_SIZE` in `lib/utils.ts`.

---

### Task 1: Wallet API DTO types

**Files:**
- Create: `frontend/types/api/wallet.api.ts`

**Interfaces:**
- Produces: `WalletResponse`, `DepositRequest`, `DepositResponse`, `WalletTransactionType`, `TransactionResponse` — used by Task 3 (mapper) and Task 4 (service).

- [ ] **Step 1: Write the DTO types**

```ts
export interface WalletResponse {
  totalBalance: number;
  availableBalance: number;
  lockedBalance: number;
  currency: string;
  status: string;
}

export interface DepositRequest {
  amount: number;
}

export interface DepositResponse {
  transactionId: string;
  newBalance: number;
  status: string;
}

export type WalletTransactionType =
  | 'DEPOSIT'
  | 'HOLD'
  | 'HOLD_CANCEL'
  | 'PAYMENT'
  | 'FORFEIT'
  | 'REFUND'
  | 'FEE'
  | 'WITHDRAWAL';

export interface TransactionResponse {
  id: string;
  type: WalletTransactionType;
  amount: number;
  availableBalanceBefore: number;
  availableBalanceAfter: number;
  description: string;
  status: string;
  createdAt: string;
}
```

These mirror `backend/wallet-service/src/main/java/com/bidnow/wallet/dto/response/WalletResponse.java`, `DepositResponse.java`, `TransactionResponse.java`, and `domain/enums/TransactionType.java` field-for-field.

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors (this file has no consumers yet, so it can only fail on a syntax mistake).

- [ ] **Step 3: Stage**

```bash
git add frontend/types/api/wallet.api.ts
```

---

### Task 2: Extend wallet UI types

**Files:**
- Modify: `frontend/types/ui/wallet.ui.ts`

**Interfaces:**
- Consumes: nothing new.
- Produces: `TransactionType` now includes `'fee' | 'forfeit'`; `Transaction.userId` becomes optional (backend transactions belong to the caller's own wallet — there is no `userId` field on `TransactionResponse` to populate it from, and no UI code reads it).

- [ ] **Step 1: Update the file**

Replace the full contents of `frontend/types/ui/wallet.ui.ts` with:

```ts
export type TransactionType =
  | 'deposit'
  | 'withdrawal'
  | 'bid_hold'
  | 'bid_release'
  | 'won_payment'
  | 'refund'
  | 'fee'
  | 'forfeit'

export interface Transaction {
  id:          string
  userId?:     string
  type:        TransactionType
  amount:      number        // dollars — positive = credit, negative = debit
  description: string
  createdAt:   Date
  auctionId?:  string
}

export interface WalletBalance {
  available: number          // dollars — spendable
  held:      number          // dollars — reserved for active bids
  total:     number          // dollars — available + held
}
```

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors — `userId` becoming optional can only break a caller that assigns it a required non-string value, which none do.

- [ ] **Step 3: Stage**

```bash
git add frontend/types/ui/wallet.ui.ts
```

---

### Task 3: Wallet mapper

**Files:**
- Create: `frontend/types/mappers/wallet.mapper.ts`

**Interfaces:**
- Consumes: `WalletResponse`, `TransactionResponse` from Task 1; `WalletBalance`, `Transaction`, `TransactionType` from Task 2.
- Produces: `mapWalletResponse(dto: WalletResponse): WalletBalance`, `mapTransactionResponse(dto: TransactionResponse): Transaction` — used by Task 6 (`useWallet` hook).

- [ ] **Step 1: Write the mapper**

```ts
import type { WalletResponse, TransactionResponse, WalletTransactionType } from '@/types/api/wallet.api'
import type { WalletBalance, Transaction, TransactionType } from '@/types/ui/wallet.ui'

const TYPE_MAP: Record<WalletTransactionType, TransactionType> = {
  DEPOSIT:     'deposit',
  WITHDRAWAL:  'withdrawal',
  HOLD:        'bid_hold',
  HOLD_CANCEL: 'bid_release',
  PAYMENT:     'won_payment',
  REFUND:      'refund',
  FEE:         'fee',
  FORFEIT:     'forfeit',
}

export function mapWalletResponse(dto: WalletResponse): WalletBalance {
  return {
    available: dto.availableBalance,
    held:      dto.lockedBalance,
    total:     dto.totalBalance,
  }
}

export function mapTransactionResponse(dto: TransactionResponse): Transaction {
  const signedAmount = dto.availableBalanceAfter >= dto.availableBalanceBefore
    ? dto.amount
    : -dto.amount

  return {
    id:          dto.id,
    type:        TYPE_MAP[dto.type],
    amount:      signedAmount,
    description: dto.description,
    createdAt:   new Date(dto.createdAt),
  }
}
```

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Stage**

```bash
git add frontend/types/mappers/wallet.mapper.ts
```

---

### Task 4: Wallet service

**Files:**
- Create: `frontend/services/wallet.service.ts`

**Interfaces:**
- Consumes: `apiFetch` from `frontend/lib/apiClient.ts`; `ApiResponse<T>`, `PageResponse<T>` from `frontend/types/api/common.api.ts`; `WalletResponse`, `DepositResponse`, `TransactionResponse` from Task 1.
- Produces: `walletService.getWallet()`, `walletService.deposit(amount)`, `walletService.getTransactions(params)` — used by Task 6 (`useWallet` hook).

- [ ] **Step 1: Write the service**

```ts
import { apiFetch } from '@/lib/apiClient'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type { WalletResponse, DepositResponse, TransactionResponse } from '@/types/api/wallet.api'

export const walletService = {
  async getWallet(): Promise<ApiResponse<WalletResponse>> {
    const response = await apiFetch('/api/v1/wallets')
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async deposit(amount: number): Promise<ApiResponse<DepositResponse>> {
    const response = await apiFetch('/api/v1/wallets/deposit', {
      method: 'POST',
      body: JSON.stringify({ amount }),
    })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async getTransactions(
    params: { page?: number; size?: number } = {}
  ): Promise<ApiResponse<PageResponse<TransactionResponse>>> {
    const query = new URLSearchParams()
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 10))
    const response = await apiFetch(`/api/v1/wallets/transactions?${query}`)
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },
}
```

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Stage**

```bash
git add frontend/services/wallet.service.ts
```

---

### Task 5: Wallet store — replace local mutators with real cache semantics

**Files:**
- Modify: `frontend/store/walletStore.ts` (full-file rewrite — current file is 41 lines)

**Interfaces:**
- Consumes: `Transaction`, `WalletBalance` from Task 2.
- Produces: `useWalletStore` state now has `hasFetched: boolean` and `setTransactions(list)`; `deposit()` and `addTransaction()` are removed — used by Task 6 (`useWallet` hook), which is the only remaining writer.

**Before this task**, confirm the only callers of the store's `deposit`/`addTransaction` mutators are the ones this plan already accounts for:

```bash
grep -rn "deposit(10_000)\|addTransaction(" frontend/ --include=*.tsx --include=*.ts
```
Expected: one match — `frontend/components/wallet/WalletPanel.tsx` (`onClick={() => deposit(10_000)}`). That call site is rewritten by Task 8 to use `depositFunds` instead, so removing these store mutators now is safe (Task 8 just won't type-check until it lands).

- [ ] **Step 1: Replace the file contents**

```ts
import { create } from 'zustand'
import type { Transaction, WalletBalance } from '@/types/ui/wallet.ui'

const LOW_BALANCE_THRESHOLD = 10  // $10.00

interface WalletState extends WalletBalance {
  isLow:           boolean
  transactions:    Transaction[]
  isLoading:       boolean
  hasFetched:      boolean
  setBalance:      (balance: WalletBalance) => void
  setTransactions: (transactions: Transaction[]) => void
  setLoading:      (v: boolean) => void
  setHasFetched:   (v: boolean) => void
}

export const useWalletStore = create<WalletState>((set) => ({
  available:    0,
  held:         0,
  total:        0,
  isLow:        false,
  transactions: [],
  isLoading:    false,
  hasFetched:   false,

  setBalance: (balance) =>
    set({ ...balance, isLow: balance.available < LOW_BALANCE_THRESHOLD }),

  setTransactions: (transactions) => set({ transactions }),

  setLoading: (v) => set({ isLoading: v }),

  setHasFetched: (v) => set({ hasFetched: v }),
}))
```

- [ ] **Step 2: Type-check** (expect a transient error — fixed in Task 6)

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: errors in `frontend/components/wallet/WalletPanel.tsx` (`deposit` no longer exists) and `frontend/hooks/useWallet.ts` if it references removed fields — these are expected and resolved by Task 6 and Task 8. Confirm there are no errors *outside* those two files.

- [ ] **Step 3: Stage**

```bash
git add frontend/store/walletStore.ts
```

---

### Task 6: `useWallet` hook — fetch on mount, deposit, refetch

**Files:**
- Modify: `frontend/hooks/useWallet.ts` (full-file rewrite — current file is 7 lines)

**Interfaces:**
- Consumes: `useWalletStore` from Task 5; `walletService` from Task 4; `mapWalletResponse`, `mapTransactionResponse` from Task 3; `useAuthStore` (existing, `frontend/store/authStore.ts`).
- Produces: `useWallet(): { available, held, total, isLow, transactions, isLoading, error, refetch(): Promise<void>, depositFunds(amount: number): Promise<void> }` — used by Task 8 (`WalletPanel`) and Task 9 (`WalletBadge`).

- [ ] **Step 1: Write the hook**

```ts
'use client'

import { useCallback, useEffect, useState } from 'react'
import { useWalletStore } from '@/store/walletStore'
import { useAuthStore } from '@/store/authStore'
import { walletService } from '@/services/wallet.service'
import { mapWalletResponse, mapTransactionResponse } from '@/types/mappers/wallet.mapper'

export function useWallet() {
  const state = useWalletStore()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const [error, setError] = useState<string | null>(null)

  const fetchWallet = useCallback(async () => {
    if (!isAuthenticated) return

    state.setLoading(true)
    setError(null)

    try {
      const [walletRes, transactionsRes] = await Promise.all([
        walletService.getWallet(),
        walletService.getTransactions({ page: 0, size: 10 }),
      ])
      state.setBalance(mapWalletResponse(walletRes.data))
      state.setTransactions(transactionsRes.data.data.map(mapTransactionResponse))
      state.setHasFetched(true)
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'message' in err
          ? String((err as { message: unknown }).message)
          : 'Failed to load wallet'
      setError(message)
    } finally {
      state.setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  useEffect(() => {
    if (isAuthenticated && !state.hasFetched) {
      fetchWallet()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, state.hasFetched])

  const depositFunds = useCallback(
    async (amount: number) => {
      await walletService.deposit(amount)
      await fetchWallet()
    },
    [fetchWallet]
  )

  return {
    available:    state.available,
    held:         state.held,
    total:        state.total,
    isLow:        state.isLow,
    transactions: state.transactions,
    isLoading:    state.isLoading,
    error,
    refetch:      fetchWallet,
    depositFunds,
  }
}
```

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors outside `frontend/components/wallet/WalletPanel.tsx` (still destructures the removed `deposit`, fixed in Task 8).

- [ ] **Step 3: Lint**

Run (from `frontend/`): `npx eslint hooks/useWallet.ts`
Expected: no errors.

- [ ] **Step 4: Stage**

```bash
git add frontend/hooks/useWallet.ts
```

---

### Task 7: `TransactionRow` — fee/forfeit config + sign-derived color

**Files:**
- Modify: `frontend/components/wallet/TransactionRow.tsx`

**Interfaces:**
- Consumes: `Transaction` from Task 2 (now carries a signed `amount`).
- Produces: no change to the component's external props (`{ transaction: Transaction }`).

- [ ] **Step 1: Replace `TYPE_CONFIG` and the sign logic**

Replace lines 10–24 of `frontend/components/wallet/TransactionRow.tsx`:

```tsx
const TYPE_CONFIG: Record<
  Transaction['type'],
  { label: string; Icon: React.ComponentType<{ className?: string }> }
> = {
  deposit:      { label: 'Deposit',        Icon: ArrowDown },
  withdrawal:   { label: 'Withdrawal',     Icon: ArrowUp },
  bid_hold:     { label: 'Bid Hold',       Icon: CircleDollarSign },
  bid_release:  { label: 'Bid Released',   Icon: RefreshCw },
  won_payment:  { label: 'Won — Payment',  Icon: ArrowUp },
  refund:       { label: 'Refund',         Icon: ArrowDown },
  fee:          { label: 'Platform Fee',   Icon: ArrowUp },
  forfeit:      { label: 'Forfeit',        Icon: ArrowUp },
}

export function TransactionRow({ transaction }: TransactionRowProps) {
  const { Icon, label } = TYPE_CONFIG[transaction.type]
  const positive = transaction.amount >= 0
```

(This deletes the old `positive` field from each config entry and the old `const { Icon, positive, label } = config` line — the rest of the component body, which already reads `positive`, `Icon`, and `label`, is unchanged.)

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Lint**

Run (from `frontend/`): `npx eslint components/wallet/TransactionRow.tsx`
Expected: no errors.

- [ ] **Step 4: Stage**

```bash
git add frontend/components/wallet/TransactionRow.tsx
```

---

### Task 8: `WalletPanel` — deposit dialog + loading skeleton

**Files:**
- Modify: `frontend/components/wallet/WalletPanel.tsx` (full-file rewrite — current file is 62 lines)

**Interfaces:**
- Consumes: `useWallet()` from Task 6; `Dialog`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogDescription`/`DialogFooter` from `frontend/components/ui/dialog.tsx` (existing); `CurrencyInput` from `frontend/components/ui/currency-input.tsx` (existing); `Skeleton` from `frontend/components/ui/skeleton.tsx` (existing); `toast` from `sonner`; `getErrorMessage` from `frontend/lib/utils.ts` (existing).
- Produces: no change to the component's external shape (`WalletPanel` takes no props).

- [ ] **Step 1: Replace the file contents**

```tsx
'use client'

import { useState } from 'react'
import { toast } from 'sonner'
import { CircleDollarSign } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { CurrencyInput } from '@/components/ui/currency-input'
import { TransactionRow } from './TransactionRow'
import { useWallet } from '@/hooks/useWallet'
import { formatCurrency } from '@/lib/format'
import { getErrorMessage } from '@/lib/utils'

export function WalletPanel() {
  const { available, held, transactions, isLoading, depositFunds } = useWallet()
  const [isDialogOpen, setIsDialogOpen] = useState(false)
  const [amount, setAmount] = useState(0)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleDeposit = async () => {
    if (amount <= 0) return
    setIsSubmitting(true)
    try {
      await depositFunds(amount)
      toast.success('Deposit successful')
      setIsDialogOpen(false)
      setAmount(0)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to deposit funds'))
    } finally {
      setIsSubmitting(false)
    }
  }

  const showSkeleton = isLoading && transactions.length === 0 && available === 0

  return (
    <>
      {/* Balance card */}
      <div className="mx-4 rounded-xl bg-[var(--color-wallet-bg)] p-4 flex flex-col gap-1">
        <p className="text-xs text-muted-foreground uppercase font-medium">
          Available balance
        </p>
        {showSkeleton ? (
          <Skeleton className="h-8 w-32" />
        ) : (
          <p className="font-mono font-medium text-[length:var(--font-size-price-md)] text-[var(--color-wallet-text)]">
            {formatCurrency(available)}
          </p>
        )}
        {held > 0 && (
          <p className="text-xs text-muted-foreground">
            {formatCurrency(held)} held for active bids
          </p>
        )}
      </div>

      <div className="px-4">
        <Button
          variant="brand"
          className="w-full"
          onClick={() => setIsDialogOpen(true)}
        >
          <CircleDollarSign className="size-4" />
          Add Funds
        </Button>
      </div>

      <Separator />

      {/* Transaction history */}
      <div className="px-4 flex flex-col gap-2 flex-1 min-h-0">
        <p className="text-sm font-medium">Recent transactions</p>
        {showSkeleton ? (
          <div className="flex flex-col gap-3 py-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : transactions.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4">No transactions yet.</p>
        ) : (
          <ScrollArea className="flex-1">
            {transactions.map((tx, i) => (
              <div key={tx.id}>
                {i > 0 && <Separator />}
                <TransactionRow transaction={tx} />
              </div>
            ))}
          </ScrollArea>
        )}
      </div>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add funds</DialogTitle>
            <DialogDescription>
              Simulated deposit — no real payment is processed.
            </DialogDescription>
          </DialogHeader>

          <CurrencyInput value={amount} onChange={setAmount} placeholder="0.00" />

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsDialogOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              variant="brand"
              onClick={handleDeposit}
              disabled={isSubmitting || amount <= 0}
            >
              Deposit {amount > 0 ? formatCurrency(amount) : ''}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
```

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors anywhere in the project (this was the last file referencing the removed `deposit` store field).

- [ ] **Step 3: Lint**

Run (from `frontend/`): `npx eslint components/wallet/WalletPanel.tsx`
Expected: no errors.

- [ ] **Step 4: Stage**

```bash
git add frontend/components/wallet/WalletPanel.tsx
```

---

### Task 9: `WalletBadge` — loading skeleton

**Files:**
- Modify: `frontend/components/wallet/WalletBadge.tsx:11-13`

**Interfaces:**
- Consumes: `useWallet()` from Task 6 (now also returns `isLoading`, `transactions`).
- Produces: no change to the component's external shape.

- [ ] **Step 1: Show a skeleton before the first successful load**

Replace lines 11–13:

```tsx
export function WalletBadge() {
  const { available, isLow } = useWallet()
```

with:

```tsx
export function WalletBadge() {
  const { available, isLow, isLoading, transactions } = useWallet()
  const showSkeleton = isLoading && transactions.length === 0 && available === 0

  if (showSkeleton) {
    return <Skeleton className="h-7 w-20 rounded-full" />
  }
```

Add the import at the top of the file (alongside the existing `Button`/`Sheet`/etc. imports):

```tsx
import { Skeleton } from '@/components/ui/skeleton'
```

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Lint**

Run (from `frontend/`): `npx eslint components/wallet/WalletBadge.tsx`
Expected: no errors.

- [ ] **Step 4: Stage**

```bash
git add frontend/components/wallet/WalletBadge.tsx
```

---

### Task 10: Remove the fake $240,000 wallet seed from `BidPanelLive`

**Files:**
- Modify: `frontend/components/auction/BidPanelLive.tsx:18-25`

**Why:** `BidPanelLive` currently seeds the wallet store with a fake $240,000 balance whenever the store's `available` is `0`, purely to have a non-zero number for demoing the bid form. Now that `useWallet()` fetches a real balance, a genuinely-zero real balance would get silently overwritten by this fake seed the moment the user opens a live auction — that's a real-money-display bug, not a demo convenience anymore.

**Interfaces:**
- Consumes: nothing new.
- Produces: no change to `BidPanelLive`'s external props.

- [ ] **Step 1: Delete the seeding effect**

Current (lines 18–25):

```tsx
export function BidPanelLive({ auction, isCurrentUserWinning }: BidPanelLiveProps) {
  const walletAvailable = useWalletStore((s) => s.available)

  useEffect(() => {
    if (useWalletStore.getState().available === 0) {
      useWalletStore.getState().setBalance({ available: 240_000, held: 0, total: 240_000 })
    }
  }, [])

  return (
```

Replace with:

```tsx
export function BidPanelLive({ auction, isCurrentUserWinning }: BidPanelLiveProps) {
  const walletAvailable = useWalletStore((s) => s.available)

  return (
```

Remove the now-unused `useEffect` import at the top of the file if nothing else in the file uses it (check with `grep -n "useEffect" frontend/components/auction/BidPanelLive.tsx` — if the only remaining match is the import line, delete that line too).

- [ ] **Step 2: Type-check**

Run (from `frontend/`): `npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Lint**

Run (from `frontend/`): `npx eslint components/auction/BidPanelLive.tsx`
Expected: no errors (in particular, no unused-import warning for `useEffect`).

- [ ] **Step 4: Stage**

```bash
git add frontend/components/auction/BidPanelLive.tsx
```

---

### Task 11: Manual end-to-end verification

**Files:** none (verification only).

- [ ] **Step 1: Start the backend services**

Follow `backend/CLAUDE.md`: start `discovery-service`, then `api-gateway`, then `wallet-service` (and `identity-service` for login). Ensure `docker compose up -d` (Kafka) has been run first.

- [ ] **Step 2: Start the frontend dev server**

Run (from `frontend/`): `npm run dev`
Expected: server starts on `http://localhost:3000` with no compile errors.

- [ ] **Step 3: Log in and open the wallet**

In the browser: log in as a test user, click the wallet badge in the header. Expected: the Sheet opens showing the real balance (likely `$0.00` for a fresh user) and "No transactions yet." — not the old fake `$0.00`-forever-empty mock, and not the `$240,000` seed.

- [ ] **Step 4: Deposit funds**

Click "Add Funds", enter an amount (e.g. `100`), click "Deposit $100.00". Expected: a success toast, the dialog closes, the balance updates to `$100.00`, and a new "Deposit" transaction row appears with a real relative timestamp ("a few seconds ago").

- [ ] **Step 5: Confirm persistence across reload**

Reload the page, reopen the wallet badge. Expected: the balance and transaction persist (now coming from the server, not client-only state).

- [ ] **Step 6: Confirm the auction bid panel shows the real balance**

Open a live auction's detail page. Expected: the "Available" balance shown in the bid panel (`BidPanelLive`) matches the real wallet balance from Step 4 — not a fake `$240,000`.
