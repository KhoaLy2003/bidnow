# Winner Payment UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a winner see what they owe, top up the mock wallet if short, and pay, from the auction page and from `/wallet`.

**Architecture:** Frontend only. A `paymentStore` (Zustand) holds the winner's pending payment holds and is read by two surfaces: a payment status block in `BidPanelEnded` and a "Payments due" list on `/wallet`, both opening one shared `PayNowDialog`. Pure logic (panel state machine, shortfall, error classification) lives in `lib/` so it is unit-testable. Live notification pushes keep the store fresh.

**Tech Stack:** Next.js 16 (App Router), TypeScript strict, Zustand 5, Tailwind v4, shadcn/Base UI (`Dialog`, `Button`, `Skeleton`, `CurrencyInput`), Vitest (node environment, pure modules only), `sonner` toasts, `lucide-react`.

**Spec:** `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`

## Global Constraints

- Frontend only: no backend, schema, gateway or `docs/architecture.md` change. Real gateway (VNPay) is Phase 2.
- Amounts are **dollars** (`number`) end to end. Arithmetic and comparison go through `lib/money.ts`; display with `formatCurrency` (from `lib/format.ts`) in `font-mono`.
- Never parse the backend `deadline` (timezone-less `LocalDateTime`) in the browser. Display `hoursLeft` and read `expired` from the server.
- Use shadcn/Base UI primitives from `components/ui/` (`Dialog`, `Button`, `Skeleton`, `CurrencyInput`); do not hand-write primitives. Icons from `lucide-react` only.
- Colours via CSS variables / design tokens, no raw hex. No `any` (strict TypeScript); every API payload and prop gets a type.
- `frontend/AGENTS.md` warns this Next.js has breaking changes. Mirror the imports and patterns of `components/auction/BuyNowButton.tsx` (`useRouter` from `next/navigation`, `Link` from `next/link`, `'use client'`) and read `node_modules/next/dist/docs/` before using any Next API not already used in the repo.
- Vitest runs in the `node` environment and only picks up `**/*.test.ts`. Test pure modules (`lib/`, `services/`, `store/`, `types/mappers/`). Components and hooks are verified manually.
- **Do not run `git commit`** at any point. The user commits at the end of the feature. Leave all changes in the working tree. Create the branch `feature/winner-payment-ui` before starting if you are not already on a branch for this work (AGENTS.md branch naming).
- Run all commands from `frontend/` unless stated otherwise.

## Review Focus

Failure modes the spec implies that no happy-path test exercises. Each has a pinning test in the owning task.

1. **Revisiting an old, already-paid win** must not flash "Preparing your payment…" for 30 s. The "preparing" state only applies to a recent win (`isRecentWin`). Pinned in Task 2.
2. **Cent-level money**: a balance one cent short, and float drift such as `0.1 + 0.2` vs `0.3`, must give the right shortfall (or none). Pinned in Task 2 (`getShortfall`).
3. **Account switch**: logging out and in as another user must not show the previous user's holds or "paid" markers. Pinned in Task 3 (`reset`) and wired in the notifications bridge.
4. **Out-of-order responses**: a slow fetch that started before a payment must not resurrect the paid hold, and a superseded fetch must not overwrite a newer one. Pinned in Task 3.
5. **Unhelpful error bodies**: a gateway 502 with an HTML body, or `INSUFFICIENT_BALANCE` without an `errors` map, must classify to a sensible message and not crash. Pinned in Tasks 1 and 2.

Also verified manually in Task 6/7: double-clicking Pay sends one request (button disabled and dialog locked while in flight).

## Spec deltas found while planning

These refine the approved spec and are applied to it in Task 7:
- Panel gets an `error` state (initial load failed) with a Retry; the spec table had none.
- "Preparing" only for a recent win (`isRecentWin`), see Review Focus 1.
- Live pushes are applied in `useUserNotifications` (which already owns the push handler) by calling `paymentStore.refresh()`, instead of a store subscription that could not tell a push from a list load.
- Inline top-up uses the existing `CurrencyInput` (as `WalletPanel`), not `parseDollarInput`.
- The UI `PendingPayment` type omits `deadline` (never used).

---

## File Structure

**Create**
- `frontend/lib/payment-utils.ts`, `frontend/lib/payment-utils.test.ts`: pure helpers (shortfall, panel state, push-type check, hours text, recent-win, auction preview)
- `frontend/lib/payment-errors.ts`, `frontend/lib/payment-errors.test.ts`: classify/describe payment failures
- `frontend/store/paymentStore.ts`, `frontend/store/paymentStore.test.ts`: holds, paid ids, load/refresh/reset
- `frontend/services/wallet.service.test.ts`: payment service tests
- `frontend/types/mappers/wallet.mapper.test.ts`: pending-payment mapper test
- `frontend/hooks/usePendingPayments.ts`: loads the store once per mount, exposes it
- `frontend/hooks/useAuctionPreview.ts`: cached title/image fetch per auction id
- `frontend/components/payment/PayNowDialog.tsx`: price breakdown, inline top-up, Pay
- `frontend/components/payment/PaymentStatusBlock.tsx`: per-auction payment status for the Won panel
- `frontend/components/wallet/PaymentsDueList.tsx`: "Payments due" list for `/wallet`

**Modify**
- `frontend/types/api/wallet.api.ts`: payment DTO types
- `frontend/types/ui/wallet.ui.ts`: `PendingPayment`
- `frontend/types/mappers/wallet.mapper.ts`: `mapPendingPaymentResponse`
- `frontend/services/wallet.service.ts`: `PaymentRequestError`, `getPendingPayments`, `confirmPayment`
- `frontend/hooks/useUserNotifications.ts`: refresh/reset the payment store
- `frontend/components/auction/BidPanelEnded.tsx`: replace the stub button
- `frontend/app/(dashboard)/wallet/page.tsx`: render the list
- `docs/diagrams/05-winner-payment-flow.md`, `docs/epics/wallet/epic.md`, `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`: docs

---

### Task 1: Payment types, mapper and service methods

**Files:**
- Modify: `frontend/types/api/wallet.api.ts`
- Modify: `frontend/types/ui/wallet.ui.ts`
- Modify: `frontend/types/mappers/wallet.mapper.ts`
- Modify: `frontend/services/wallet.service.ts`
- Test: `frontend/types/mappers/wallet.mapper.test.ts` (create)
- Test: `frontend/services/wallet.service.test.ts` (create)

**Interfaces:**
- Consumes: `apiFetch` from `@/lib/apiClient`; `ApiResponse` from `@/types/api/common.api`; `ApiErrorResponse` from `@/types/api/bid.api`.
- Produces:
  - `PendingPaymentResponse`, `ConfirmPaymentRequest`, `ConfirmPaymentResponse` (API types)
  - `PendingPayment` (UI type): `{ auctionId: string; totalAmount: number; depositApplied: number; remaining: number; fundsHeld: boolean; hoursLeft: number; expired: boolean }`
  - `mapPendingPaymentResponse(dto: PendingPaymentResponse): PendingPayment`
  - `class PaymentRequestError extends Error { readonly status: number; readonly body: ApiErrorResponse | null }`
  - `walletService.getPendingPayments(): Promise<PendingPaymentResponse[]>`
  - `walletService.confirmPayment(auctionId: string): Promise<ConfirmPaymentResponse>`

- [ ] **Step 1: Write the failing mapper test**

Create `frontend/types/mappers/wallet.mapper.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mapPendingPaymentResponse } from './wallet.mapper'
import type { PendingPaymentResponse } from '@/types/api/wallet.api'

describe('mapPendingPaymentResponse', () => {
  it('maps the amounts and flags and drops the timezone-less deadline', () => {
    const dto: PendingPaymentResponse = {
      auctionId: 'a-1',
      totalAmount: 500,
      depositApplied: 50,
      remaining: 450,
      fundsHeld: true,
      deadline: '2026-10-17T10:00:00',
      hoursLeft: 47,
      expired: false,
    }

    expect(mapPendingPaymentResponse(dto)).toEqual({
      auctionId: 'a-1',
      totalAmount: 500,
      depositApplied: 50,
      remaining: 450,
      fundsHeld: true,
      hoursLeft: 47,
      expired: false,
    })
  })
})
```

- [ ] **Step 2: Write the failing service test**

Create `frontend/services/wallet.service.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { PaymentRequestError, walletService } from './wallet.service'

const mockFetch = vi.mocked(apiFetch)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('walletService.getPendingPayments', () => {
  beforeEach(() => mockFetch.mockReset())

  it('gets the pending payments and returns the data list', async () => {
    const data = [{
      auctionId: 'a-1', totalAmount: 500, depositApplied: 50, remaining: 450,
      fundsHeld: true, deadline: '2026-10-17T10:00:00', hoursLeft: 47, expired: false,
    }]
    mockFetch.mockResolvedValue(json({ status: 200, data }))

    const result = await walletService.getPendingPayments()

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/wallets/payments/pending')
    expect(result).toEqual(data)
  })

  it('throws a PaymentRequestError carrying the status and body', async () => {
    mockFetch.mockResolvedValue(json({ status: 500, errorCode: 'INTERNAL_ERROR', message: 'boom' }, 500))

    const err = await walletService.getPendingPayments().catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(500)
    expect((err as PaymentRequestError).body?.errorCode).toBe('INTERNAL_ERROR')
  })
})

describe('walletService.confirmPayment', () => {
  beforeEach(() => mockFetch.mockReset())

  it('posts the auction id to the confirm endpoint and returns the data', async () => {
    const data = {
      auctionId: 'a-1', amountPaid: 500, depositApplied: 50, remainingPaid: 450,
      availableBalance: 100, lockedBalance: 0,
    }
    mockFetch.mockResolvedValue(json({ status: 200, data }))

    const result = await walletService.confirmPayment('a-1')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/wallets/payments/confirm', {
      method: 'POST',
      body: JSON.stringify({ auctionId: 'a-1' }),
    })
    expect(result).toEqual(data)
  })

  it('throws a PaymentRequestError carrying the status and error body', async () => {
    mockFetch.mockResolvedValue(
      json({ status: 400, errorCode: 'INSUFFICIENT_BALANCE', message: 'low', errors: { required: '450', availableBalance: '100' } }, 400))

    const err = await walletService.confirmPayment('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(400)
    expect((err as PaymentRequestError).body?.errorCode).toBe('INSUFFICIENT_BALANCE')
    expect((err as PaymentRequestError).body?.errors).toEqual({ required: '450', availableBalance: '100' })
  })

  it('keeps a null body when the error response is not JSON (gateway 502)', async () => {
    mockFetch.mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 }))

    const err = await walletService.confirmPayment('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(502)
    expect((err as PaymentRequestError).body).toBeNull()
  })
})
```

- [ ] **Step 3: Run both tests to verify they fail**

Run: `npx vitest run types/mappers/wallet.mapper.test.ts services/wallet.service.test.ts`
Expected: FAIL (`mapPendingPaymentResponse` / `PaymentRequestError` / `getPendingPayments` are not defined).

- [ ] **Step 4: Add the API types**

Append to `frontend/types/api/wallet.api.ts`:

```ts
export interface PendingPaymentResponse {
  auctionId: string;
  totalAmount: number;
  depositApplied: number;
  remaining: number;
  fundsHeld: boolean;
  /** Server LocalDateTime without a timezone: display-only, never parse in the browser. */
  deadline: string;
  hoursLeft: number;
  expired: boolean;
}

export interface ConfirmPaymentRequest {
  auctionId: string;
}

export interface ConfirmPaymentResponse {
  auctionId: string;
  amountPaid: number;
  depositApplied: number;
  remainingPaid: number;
  availableBalance: number;
  lockedBalance: number;
}
```

- [ ] **Step 5: Add the UI type**

Append to `frontend/types/ui/wallet.ui.ts`:

```ts
/** A winner's unpaid payment hold. All amounts are dollars. */
export interface PendingPayment {
  auctionId:      string
  totalAmount:    number   // winning price
  depositApplied: number   // auction deposit counted toward the price
  remaining:      number   // totalAmount − depositApplied
  fundsHeld:      boolean  // remaining is already moved to locked in the wallet
  hoursLeft:      number   // server-computed, floored; 0 means under one hour
  expired:        boolean  // server-computed
}
```

- [ ] **Step 6: Add the mapper**

In `frontend/types/mappers/wallet.mapper.ts`, change the imports to:

```ts
import type {
  WalletResponse,
  TransactionResponse,
  WalletTransactionType,
  PendingPaymentResponse,
} from '@/types/api/wallet.api'
import type { WalletBalance, Transaction, TransactionType, PendingPayment } from '@/types/ui/wallet.ui'
```

and append:

```ts
export function mapPendingPaymentResponse(dto: PendingPaymentResponse): PendingPayment {
  return {
    auctionId:      dto.auctionId,
    totalAmount:    dto.totalAmount,
    depositApplied: dto.depositApplied,
    remaining:      dto.remaining,
    fundsHeld:      dto.fundsHeld,
    hoursLeft:      dto.hoursLeft,
    expired:        dto.expired,
  }
}
```

- [ ] **Step 7: Add the service methods**

Replace the contents of `frontend/services/wallet.service.ts` with (existing methods unchanged):

```ts
import { apiFetch } from '@/lib/apiClient'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type { ApiErrorResponse } from '@/types/api/bid.api'
import type {
  WalletResponse,
  DepositResponse,
  TransactionResponse,
  PendingPaymentResponse,
  ConfirmPaymentRequest,
  ConfirmPaymentResponse,
} from '@/types/api/wallet.api'

/** A non-2xx payment API response; `body` is the backend ErrorResponse when it was JSON. */
export class PaymentRequestError extends Error {
  constructor(readonly status: number, readonly body: ApiErrorResponse | null) {
    super(body?.message ?? `Request failed with status ${status}`)
    this.name = 'PaymentRequestError'
  }
}

async function toPaymentRequestError(response: Response): Promise<PaymentRequestError> {
  let body: ApiErrorResponse | null = null
  try {
    body = (await response.json()) as ApiErrorResponse
  } catch {
    body = null
  }
  return new PaymentRequestError(response.status, body)
}

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

  /** The caller's unpaid winner holds (PENDING_PAYMENT only). */
  async getPendingPayments(): Promise<PendingPaymentResponse[]> {
    const response = await apiFetch('/api/v1/wallets/payments/pending')
    if (!response.ok) throw await toPaymentRequestError(response)
    const body: ApiResponse<PendingPaymentResponse[]> = await response.json()
    return body.data
  },

  /** Settles the caller's hold for an auction. Throws PaymentRequestError on any non-2xx. */
  async confirmPayment(auctionId: string): Promise<ConfirmPaymentResponse> {
    const request: ConfirmPaymentRequest = { auctionId }
    const response = await apiFetch('/api/v1/wallets/payments/confirm', {
      method: 'POST',
      body: JSON.stringify(request),
    })
    if (!response.ok) throw await toPaymentRequestError(response)
    const body: ApiResponse<ConfirmPaymentResponse> = await response.json()
    return body.data
  },
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `npx vitest run types/mappers/wallet.mapper.test.ts services/wallet.service.test.ts`
Expected: PASS (all tests).

- [ ] **Step 9: Typecheck and lint the touched files**

Run: `npx tsc --noEmit && npx eslint types services`
Expected: no errors. (Do not commit.)

---

### Task 2: Pure payment logic and error classification

**Files:**
- Create: `frontend/lib/payment-utils.ts`
- Create: `frontend/lib/payment-errors.ts`
- Test: `frontend/lib/payment-utils.test.ts` (create)
- Test: `frontend/lib/payment-errors.test.ts` (create)

**Interfaces:**
- Consumes: `PendingPayment` (Task 1); `PaymentRequestError` (Task 1); `isLessThan`, `toCents`, `fromCents` from `@/lib/money`; `resolveImageUrl` from `@/lib/image-utils`; `formatCurrency` from `@/lib/format`; `AuctionImage` from `@/types/ui/auction.ui`.
- Produces (`lib/payment-utils.ts`):
  - `PREPARING_POLL_MS = 3_000`, `PREPARING_WINDOW_MS = 30_000`, `RECENT_WIN_MS = 120_000`
  - `isPaymentPush(serverType: string): boolean`
  - `getShortfall(hold: Pick<PendingPayment, 'fundsHeld' | 'remaining'>, availableBalance: number): number | null`
  - `formatHoursLeft(hours: number): string`
  - `isRecentWin(completedAt: Date | undefined, now?: number): boolean`
  - `type PaymentPanelState = 'loading' | 'error' | 'preparing' | 'due' | 'expired' | 'paid' | 'none'`
  - `derivePaymentPanelState(input: PaymentPanelInput): { state: PaymentPanelState; hold: PendingPayment | null }`
  - `interface AuctionPreview { title: string; imageUrl?: string }`, `toAuctionPreview(auction: { title: string; images: AuctionImage[] }): AuctionPreview`
- Produces (`lib/payment-errors.ts`):
  - `type PaymentFailure` (kinds: `unauthenticated | insufficient-balance | expired | no-longer-due | account-problem | invalid | unavailable | unknown`)
  - `interface PaymentFailureView { message: string; disablesForm: boolean }`
  - `classifyPaymentError(err: unknown): PaymentFailure`, `describePaymentFailure(failure: PaymentFailure): PaymentFailureView`

- [ ] **Step 1: Write the failing payment-utils test**

Create `frontend/lib/payment-utils.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import {
  RECENT_WIN_MS,
  derivePaymentPanelState,
  formatHoursLeft,
  getShortfall,
  isPaymentPush,
  isRecentWin,
  toAuctionPreview,
  type PaymentPanelInput,
} from './payment-utils'
import type { AuctionImage } from '@/types/ui/auction.ui'
import type { PendingPayment } from '@/types/ui/wallet.ui'

function hold(over: Partial<PendingPayment> = {}): PendingPayment {
  return {
    auctionId: 'a-1', totalAmount: 500, depositApplied: 100, remaining: 400,
    fundsHeld: false, hoursLeft: 47, expired: false, ...over,
  }
}

describe('isPaymentPush', () => {
  it('matches the payment notification types that change what is owed', () => {
    expect(isPaymentPush('PAYMENT_REQUIRED')).toBe(true)
    expect(isPaymentPush('PAYMENT_RECEIVED')).toBe(true)
    expect(isPaymentPush('PAYMENT_FAILED')).toBe(true)
  })

  it('ignores everything else, including reminders', () => {
    expect(isPaymentPush('PAYMENT_REMINDER')).toBe(false)
    expect(isPaymentPush('BID_OUTBID')).toBe(false)
  })
})

describe('getShortfall', () => {
  it('is null when the funds are already held', () => {
    expect(getShortfall(hold({ fundsHeld: true }), 0)).toBeNull()
  })

  it('is null when the balance covers the remainder, including exactly', () => {
    expect(getShortfall(hold(), 400)).toBeNull()
    expect(getShortfall(hold(), 1000)).toBeNull()
  })

  it('returns the missing amount', () => {
    expect(getShortfall(hold(), 150.5)).toBe(249.5)
  })

  it('detects a balance that is one cent short', () => {
    expect(getShortfall(hold(), 399.99)).toBe(0.01)
  })

  it('is not fooled by float drift', () => {
    expect(getShortfall(hold({ remaining: 0.3 }), 0.1 + 0.2)).toBeNull()
    expect(getShortfall(hold({ remaining: 0.3 }), 0.1)).toBe(0.2)
  })

  it('is null when nothing remains to pay', () => {
    expect(getShortfall(hold({ remaining: 0 }), 0)).toBeNull()
  })
})

describe('formatHoursLeft', () => {
  it('words zero, one and many hours', () => {
    expect(formatHoursLeft(0)).toBe('Less than 1 hour left')
    expect(formatHoursLeft(1)).toBe('1 hour left')
    expect(formatHoursLeft(47)).toBe('47 hours left')
  })

  it('treats a negative value as under an hour', () => {
    expect(formatHoursLeft(-3)).toBe('Less than 1 hour left')
  })
})

describe('isRecentWin', () => {
  const now = Date.parse('2026-10-15T12:00:00Z')

  it('treats an unknown completion time as recent (live state has no completedAt yet)', () => {
    expect(isRecentWin(undefined, now)).toBe(true)
  })

  it('is true within the window and false after it', () => {
    expect(isRecentWin(new Date(now - 60_000), now)).toBe(true)
    expect(isRecentWin(new Date(now - RECENT_WIN_MS - 1), now)).toBe(false)
  })

  it('is false for an auction won days ago', () => {
    expect(isRecentWin(new Date('2026-10-12T12:00:00Z'), now)).toBe(false)
  })

  it('is true when the client clock is behind the server', () => {
    expect(isRecentWin(new Date(now + 5_000), now)).toBe(true)
  })
})

describe('derivePaymentPanelState', () => {
  const base: PaymentPanelInput = {
    holds: [], auctionId: 'a-1', paidIds: new Set<string>(),
    hasFetched: true, loadFailed: false, pollWindowOpen: false,
  }

  it('is loading before the first fetch', () => {
    expect(derivePaymentPanelState({ ...base, hasFetched: false })).toEqual({ state: 'loading', hold: null })
  })

  it('is error when the first fetch failed', () => {
    expect(derivePaymentPanelState({ ...base, hasFetched: false, loadFailed: true }).state).toBe('error')
  })

  it('does not show error once a fetch has succeeded', () => {
    expect(derivePaymentPanelState({ ...base, loadFailed: true }).state).toBe('none')
  })

  it('is due when there is an unexpired hold for the auction', () => {
    const h = hold()
    expect(derivePaymentPanelState({ ...base, holds: [h] })).toEqual({ state: 'due', hold: h })
  })

  it('is expired when the hold is past its deadline', () => {
    const h = hold({ expired: true })
    expect(derivePaymentPanelState({ ...base, holds: [h] })).toEqual({ state: 'expired', hold: h })
  })

  it('ignores holds for other auctions', () => {
    expect(derivePaymentPanelState({ ...base, holds: [hold({ auctionId: 'other' })] }).state).toBe('none')
  })

  it('is preparing while the window is open and no hold exists yet', () => {
    expect(derivePaymentPanelState({ ...base, pollWindowOpen: true }).state).toBe('preparing')
  })

  it('is none once the window has closed with no hold', () => {
    expect(derivePaymentPanelState(base).state).toBe('none')
  })

  it('is paid when the auction was paid this session, even if a stale hold is present', () => {
    expect(derivePaymentPanelState({ ...base, paidIds: new Set(['a-1']), holds: [hold()] }))
      .toEqual({ state: 'paid', hold: null })
  })
})

describe('toAuctionPreview', () => {
  const img = (over: Partial<AuctionImage>): AuctionImage => ({
    id: 'i', imageUrl: 'https://cdn.test/full.jpg', displayOrder: 0, isPrimary: false, ...over,
  })

  it('prefers the primary image and its thumbnail', () => {
    const preview = toAuctionPreview({
      title: 'Watch',
      images: [
        img({ id: 'a', imageUrl: 'https://cdn.test/a.jpg' }),
        img({ id: 'b', isPrimary: true, imageUrl: 'https://cdn.test/b.jpg', thumbnailUrl: 'https://cdn.test/b-thumb.jpg' }),
      ],
    })
    expect(preview).toEqual({ title: 'Watch', imageUrl: 'https://cdn.test/b-thumb.jpg' })
  })

  it('falls back to the first image by display order', () => {
    const preview = toAuctionPreview({
      title: 'Watch',
      images: [
        img({ id: 'a', displayOrder: 2, imageUrl: 'https://cdn.test/a.jpg' }),
        img({ id: 'b', displayOrder: 1, imageUrl: 'https://cdn.test/b.jpg' }),
      ],
    })
    expect(preview.imageUrl).toBe('https://cdn.test/b.jpg')
  })

  it('has no image when the auction has none', () => {
    expect(toAuctionPreview({ title: 'Watch', images: [] })).toEqual({ title: 'Watch', imageUrl: undefined })
  })
})
```

- [ ] **Step 2: Write the failing payment-errors test**

Create `frontend/lib/payment-errors.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { PaymentRequestError } from '@/services/wallet.service'
import { classifyPaymentError, describePaymentFailure } from './payment-errors'

function failure(status: number, errorCode: string, errors?: Record<string, string>): PaymentRequestError {
  return new PaymentRequestError(status, { status, errorCode, message: 'server message', errors })
}

describe('classifyPaymentError', () => {
  it('maps INSUFFICIENT_BALANCE with parsed amounts', () => {
    expect(classifyPaymentError(failure(400, 'INSUFFICIENT_BALANCE', { required: '450.00', availableBalance: '100' })))
      .toEqual({ kind: 'insufficient-balance', required: 450, available: 100 })
  })

  it('maps INSUFFICIENT_BALANCE without an errors map to null amounts', () => {
    expect(classifyPaymentError(failure(400, 'INSUFFICIENT_BALANCE')))
      .toEqual({ kind: 'insufficient-balance', required: null, available: null })
  })

  it('accepts numeric amounts as well as strings', () => {
    const err = failure(400, 'INSUFFICIENT_BALANCE', { required: 20, availableBalance: 10 } as unknown as Record<string, string>)
    expect(classifyPaymentError(err)).toEqual({ kind: 'insufficient-balance', required: 20, available: 10 })
  })

  it('maps the deadline, not-pending and not-found codes', () => {
    expect(classifyPaymentError(failure(400, 'PAYMENT_DEADLINE_EXPIRED'))).toEqual({ kind: 'expired' })
    expect(classifyPaymentError(failure(409, 'PAYMENT_NOT_PENDING'))).toEqual({ kind: 'no-longer-due' })
    expect(classifyPaymentError(failure(404, 'PAYMENT_HOLD_NOT_FOUND'))).toEqual({ kind: 'no-longer-due' })
  })

  it('maps account problems', () => {
    expect(classifyPaymentError(failure(404, 'SELLER_WALLET_NOT_FOUND'))).toEqual({ kind: 'account-problem' })
    expect(classifyPaymentError(failure(409, 'DEPOSIT_LOCK_CLOSED'))).toEqual({ kind: 'account-problem' })
  })

  it('maps INVALID_INPUT to the first field error, else the server message', () => {
    expect(classifyPaymentError(failure(400, 'INVALID_INPUT', { auctionId: 'is required' })))
      .toEqual({ kind: 'invalid', message: 'is required' })
    expect(classifyPaymentError(failure(400, 'INVALID_INPUT')))
      .toEqual({ kind: 'invalid', message: 'server message' })
  })

  it('maps 401, 503, SERVICE_UNAVAILABLE and network failures', () => {
    expect(classifyPaymentError(new PaymentRequestError(401, null))).toEqual({ kind: 'unauthenticated' })
    expect(classifyPaymentError(new PaymentRequestError(503, null))).toEqual({ kind: 'unavailable' })
    expect(classifyPaymentError(failure(500, 'SERVICE_UNAVAILABLE'))).toEqual({ kind: 'unavailable' })
    expect(classifyPaymentError(new TypeError('Failed to fetch'))).toEqual({ kind: 'unavailable' })
  })

  it('maps an expired session to unauthenticated', () => {
    expect(classifyPaymentError(new Error('session_expired'))).toEqual({ kind: 'unauthenticated' })
  })

  it('does not crash on a gateway 502 with a non-JSON body', () => {
    expect(classifyPaymentError(new PaymentRequestError(502, null))).toEqual({ kind: 'unknown' })
  })

  it('falls back to unknown for anything else', () => {
    expect(classifyPaymentError(failure(500, 'SOMETHING_ELSE'))).toEqual({ kind: 'unknown' })
    expect(classifyPaymentError('weird')).toEqual({ kind: 'unknown' })
  })
})

describe('describePaymentFailure', () => {
  it('words an insufficient balance with the amounts and keeps the form enabled', () => {
    const view = describePaymentFailure({ kind: 'insufficient-balance', required: 450, available: 100 })
    expect(view.message).toBe('You need $450 available to pay (you have $100). Add funds, then try again.')
    expect(view.disablesForm).toBe(false)
  })

  it('words an insufficient balance without amounts', () => {
    const view = describePaymentFailure({ kind: 'insufficient-balance', required: null, available: null })
    expect(view.message).toBe('You need more funds available to pay. Add funds, then try again.')
  })

  it('disables the form for terminal failures', () => {
    expect(describePaymentFailure({ kind: 'expired' })).toEqual({
      message: 'The payment deadline has passed.', disablesForm: true,
    })
    expect(describePaymentFailure({ kind: 'no-longer-due' })).toEqual({
      message: 'This payment is no longer due.', disablesForm: true,
    })
    expect(describePaymentFailure({ kind: 'account-problem' })).toEqual({
      message: "We couldn't complete this payment. Please contact support.", disablesForm: true,
    })
  })

  it('keeps the form enabled for retryable failures', () => {
    expect(describePaymentFailure({ kind: 'unavailable' }).disablesForm).toBe(false)
    expect(describePaymentFailure({ kind: 'unknown' }).disablesForm).toBe(false)
    expect(describePaymentFailure({ kind: 'unauthenticated' }).disablesForm).toBe(false)
    expect(describePaymentFailure({ kind: 'invalid', message: 'bad' })).toEqual({ message: 'bad', disablesForm: false })
  })
})
```

- [ ] **Step 3: Run both tests to verify they fail**

Run: `npx vitest run lib/payment-utils.test.ts lib/payment-errors.test.ts`
Expected: FAIL (modules do not exist).

- [ ] **Step 4: Implement `lib/payment-utils.ts`**

Create `frontend/lib/payment-utils.ts`:

```ts
import { fromCents, isLessThan, toCents } from '@/lib/money'
import { resolveImageUrl } from '@/lib/image-utils'
import type { AuctionImage } from '@/types/ui/auction.ui'
import type { PendingPayment } from '@/types/ui/wallet.ui'

/** How often the Won panel refetches while the wallet-service hold has not appeared yet. */
export const PREPARING_POLL_MS = 3_000
/** How long the Won panel keeps waiting for the hold before it gives up. */
export const PREPARING_WINDOW_MS = 30_000
/** An auction completed longer ago than this is not "just won": never show "preparing". */
export const RECENT_WIN_MS = 120_000

const PAYMENT_PUSH_TYPES: ReadonlySet<string> = new Set([
  'PAYMENT_REQUIRED',
  'PAYMENT_RECEIVED',
  'PAYMENT_FAILED',
])

/** True for notification types after which the winner's pending payments may have changed. */
export function isPaymentPush(serverType: string): boolean {
  return PAYMENT_PUSH_TYPES.has(serverType)
}

/**
 * How much more the winner must add to pay, or null when no top-up is needed:
 * the remainder is already held, or the available balance covers it. Compared in integer cents.
 */
export function getShortfall(
  hold: Pick<PendingPayment, 'fundsHeld' | 'remaining'>,
  availableBalance: number,
): number | null {
  if (hold.fundsHeld) return null
  if (!isLessThan(availableBalance, hold.remaining)) return null
  return fromCents(toCents(hold.remaining) - toCents(availableBalance))
}

/** Server-computed floored hours: 0 means under one hour. */
export function formatHoursLeft(hours: number): string {
  if (hours <= 0) return 'Less than 1 hour left'
  return hours === 1 ? '1 hour left' : `${hours} hours left`
}

/** Whether the auction completed recently enough that its hold may still be on its way. */
export function isRecentWin(completedAt: Date | undefined, now: number = Date.now()): boolean {
  if (!completedAt) return true
  return now - completedAt.getTime() < RECENT_WIN_MS
}

export type PaymentPanelState = 'loading' | 'error' | 'preparing' | 'due' | 'expired' | 'paid' | 'none'

export interface PaymentPanelInput {
  holds:          readonly PendingPayment[]
  auctionId:      string
  paidIds:        ReadonlySet<string>
  hasFetched:     boolean
  loadFailed:     boolean
  pollWindowOpen: boolean
}

export function derivePaymentPanelState(
  input: PaymentPanelInput,
): { state: PaymentPanelState; hold: PendingPayment | null } {
  if (input.paidIds.has(input.auctionId)) return { state: 'paid', hold: null }

  const hold = input.holds.find((h) => h.auctionId === input.auctionId) ?? null
  if (hold) return { state: hold.expired ? 'expired' : 'due', hold }

  if (!input.hasFetched) return { state: input.loadFailed ? 'error' : 'loading', hold: null }
  if (input.pollWindowOpen) return { state: 'preparing', hold: null }
  return { state: 'none', hold: null }
}

export interface AuctionPreview {
  title:     string
  imageUrl?: string
}

/** Title plus the primary (else first by display order) image's thumbnail, for list rows. */
export function toAuctionPreview(auction: { title: string; images: AuctionImage[] }): AuctionPreview {
  const image =
    auction.images.find((i) => i.isPrimary) ??
    [...auction.images].sort((a, b) => a.displayOrder - b.displayOrder)[0]
  return {
    title:    auction.title,
    imageUrl: resolveImageUrl(image?.thumbnailUrl ?? image?.imageUrl),
  }
}
```

- [ ] **Step 5: Implement `lib/payment-errors.ts`**

Create `frontend/lib/payment-errors.ts`:

```ts
import { PaymentRequestError } from '@/services/wallet.service'
import { formatCurrency } from '@/lib/format'

export type PaymentFailure =
  | { kind: 'unauthenticated' }
  | { kind: 'insufficient-balance'; required: number | null; available: number | null }
  | { kind: 'expired' }
  | { kind: 'no-longer-due' }
  | { kind: 'account-problem' }
  | { kind: 'invalid'; message: string }
  | { kind: 'unavailable' }
  | { kind: 'unknown' }

export interface PaymentFailureView {
  message:      string
  disablesForm: boolean
}

function parseAmount(value: unknown): number | null {
  if (value === undefined || value === null || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

export function classifyPaymentError(err: unknown): PaymentFailure {
  if (err instanceof PaymentRequestError) {
    const code = err.body?.errorCode
    const errors = err.body?.errors ?? {}
    if (err.status === 401) return { kind: 'unauthenticated' }
    switch (code) {
      case 'INSUFFICIENT_BALANCE':
        return {
          kind: 'insufficient-balance',
          required: parseAmount(errors.required),
          available: parseAmount(errors.availableBalance),
        }
      case 'PAYMENT_DEADLINE_EXPIRED':
        return { kind: 'expired' }
      case 'PAYMENT_NOT_PENDING':
      case 'PAYMENT_HOLD_NOT_FOUND':
        return { kind: 'no-longer-due' }
      case 'SELLER_WALLET_NOT_FOUND':
      case 'DEPOSIT_LOCK_CLOSED':
        return { kind: 'account-problem' }
      case 'INVALID_INPUT': {
        const first = Object.values(errors)[0]
        return { kind: 'invalid', message: first ?? err.message }
      }
    }
    if (err.status === 503 || code === 'SERVICE_UNAVAILABLE') return { kind: 'unavailable' }
    return { kind: 'unknown' }
  }
  if (err instanceof Error && err.message === 'session_expired') return { kind: 'unauthenticated' }
  if (err instanceof TypeError) return { kind: 'unavailable' } // fetch network failure
  return { kind: 'unknown' }
}

export function describePaymentFailure(failure: PaymentFailure): PaymentFailureView {
  switch (failure.kind) {
    case 'insufficient-balance': {
      const needs = failure.required !== null ? formatCurrency(failure.required) : 'more funds'
      const has = failure.available !== null ? ` (you have ${formatCurrency(failure.available)})` : ''
      return {
        message: `You need ${needs} available to pay${has}. Add funds, then try again.`,
        disablesForm: false,
      }
    }
    case 'expired':
      return { message: 'The payment deadline has passed.', disablesForm: true }
    case 'no-longer-due':
      return { message: 'This payment is no longer due.', disablesForm: true }
    case 'account-problem':
      return { message: "We couldn't complete this payment. Please contact support.", disablesForm: true }
    case 'invalid':
      return { message: failure.message, disablesForm: false }
    case 'unauthenticated':
      return { message: 'Please sign in to continue.', disablesForm: false }
    case 'unavailable':
      return { message: "We couldn't reach the payment service. Please try again.", disablesForm: false }
    case 'unknown':
      return { message: 'Could not complete your payment. Please try again.', disablesForm: false }
  }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `npx vitest run lib/payment-utils.test.ts lib/payment-errors.test.ts`
Expected: PASS (all tests).

- [ ] **Step 7: Typecheck and lint**

Run: `npx tsc --noEmit && npx eslint lib`
Expected: no errors. (Do not commit.)

---

### Task 3: Payment store, hook and notification wiring

**Files:**
- Create: `frontend/store/paymentStore.ts`
- Create: `frontend/hooks/usePendingPayments.ts`
- Modify: `frontend/hooks/useUserNotifications.ts`
- Test: `frontend/store/paymentStore.test.ts` (create)

**Interfaces:**
- Consumes: `walletService.getPendingPayments` (Task 1), `mapPendingPaymentResponse` (Task 1), `PendingPayment` (Task 1), `isPaymentPush` (Task 2), `useAuthStore` (`isAuthenticated`).
- Produces:
  - `usePaymentStore` (Zustand): state `{ holds: PendingPayment[]; paidIds: ReadonlySet<string>; isLoading: boolean; hasFetched: boolean; loadFailed: boolean }`, actions `load(): Promise<void>`, `refresh(): Promise<void>` (loads only if `hasFetched`), `markPaid(auctionId: string): void`, `reset(): void`
  - `usePendingPayments(): { holds, paidIds, isLoading, hasFetched, loadFailed, refetch: () => Promise<void> }`

- [ ] **Step 1: Write the failing store test**

Create `frontend/store/paymentStore.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/wallet.service', () => ({
  walletService: { getPendingPayments: vi.fn() },
}))

import { walletService } from '@/services/wallet.service'
import type { PendingPaymentResponse } from '@/types/api/wallet.api'
import { usePaymentStore } from './paymentStore'

const service = vi.mocked(walletService)

function dto(auctionId: string, over: Partial<PendingPaymentResponse> = {}): PendingPaymentResponse {
  return {
    auctionId, totalAmount: 500, depositApplied: 100, remaining: 400, fundsHeld: false,
    deadline: '2026-10-17T10:00:00', hoursLeft: 47, expired: false, ...over,
  }
}

const store = () => usePaymentStore.getState()
const ids = () => store().holds.map((h) => h.auctionId)

describe('paymentStore', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    store().reset()
  })

  it('load fetches and maps the holds', async () => {
    service.getPendingPayments.mockResolvedValue([dto('a-1'), dto('a-2')])

    await store().load()

    expect(ids()).toEqual(['a-1', 'a-2'])
    expect(store().holds[0]).not.toHaveProperty('deadline')
    expect(store().hasFetched).toBe(true)
    expect(store().loadFailed).toBe(false)
    expect(store().isLoading).toBe(false)
  })

  it('a failed first load flags loadFailed and leaves hasFetched false', async () => {
    service.getPendingPayments.mockRejectedValue(new Error('down'))

    await store().load()

    expect(store().loadFailed).toBe(true)
    expect(store().hasFetched).toBe(false)
    expect(store().isLoading).toBe(false)
  })

  it('a failed refresh keeps the holds already loaded', async () => {
    service.getPendingPayments.mockResolvedValueOnce([dto('a-1')])
    await store().load()
    service.getPendingPayments.mockRejectedValueOnce(new Error('down'))

    await store().load()

    expect(ids()).toEqual(['a-1'])
    expect(store().hasFetched).toBe(true)
  })

  it('a later success clears loadFailed', async () => {
    service.getPendingPayments.mockRejectedValueOnce(new Error('down'))
    await store().load()
    service.getPendingPayments.mockResolvedValueOnce([])

    await store().load()

    expect(store().loadFailed).toBe(false)
    expect(store().hasFetched).toBe(true)
  })

  it('ignores a superseded load', async () => {
    let resolveFirst!: (v: PendingPaymentResponse[]) => void
    service.getPendingPayments
      .mockReturnValueOnce(new Promise<PendingPaymentResponse[]>((r) => { resolveFirst = r }))
      .mockResolvedValueOnce([dto('new')])

    const first = store().load()
    await store().load()
    resolveFirst([dto('old')])
    await first

    expect(ids()).toEqual(['new'])
  })

  it('markPaid removes the hold and records the auction as paid', async () => {
    service.getPendingPayments.mockResolvedValue([dto('a-1'), dto('a-2')])
    await store().load()

    store().markPaid('a-1')

    expect(ids()).toEqual(['a-2'])
    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('a stale response that still contains a paid hold does not resurrect it', async () => {
    let resolveSlow!: (v: PendingPaymentResponse[]) => void
    service.getPendingPayments
      .mockReturnValueOnce(new Promise<PendingPaymentResponse[]>((r) => { resolveSlow = r }))

    const slow = store().load()
    store().markPaid('a-1')
    resolveSlow([dto('a-1'), dto('a-2')])
    await slow

    expect(ids()).toEqual(['a-2'])
    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('load keeps the paid ids across refetches', async () => {
    service.getPendingPayments.mockResolvedValue([])
    await store().load()
    store().markPaid('a-1')

    await store().load()

    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('refresh does nothing before the first load', async () => {
    await store().refresh()

    expect(service.getPendingPayments).not.toHaveBeenCalled()
  })

  it('refresh reloads once the store has been loaded', async () => {
    service.getPendingPayments.mockResolvedValueOnce([dto('a-1')])
    await store().load()
    service.getPendingPayments.mockResolvedValueOnce([dto('a-1'), dto('a-2')])

    await store().refresh()

    expect(ids()).toEqual(['a-1', 'a-2'])
  })

  it('reset clears everything and ignores an in-flight load', async () => {
    let resolveSlow!: (v: PendingPaymentResponse[]) => void
    service.getPendingPayments
      .mockReturnValueOnce(new Promise<PendingPaymentResponse[]>((r) => { resolveSlow = r }))
    const slow = store().load()
    store().markPaid('a-9')

    store().reset()
    resolveSlow([dto('previous-user')])
    await slow

    expect(store().holds).toEqual([])
    expect(store().paidIds.size).toBe(0)
    expect(store().hasFetched).toBe(false)
    expect(store().isLoading).toBe(false)
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npx vitest run store/paymentStore.test.ts`
Expected: FAIL (`./paymentStore` does not exist).

- [ ] **Step 3: Implement the store**

Create `frontend/store/paymentStore.ts`:

```ts
import { create } from 'zustand'
import { walletService } from '@/services/wallet.service'
import { mapPendingPaymentResponse } from '@/types/mappers/wallet.mapper'
import type { PendingPayment } from '@/types/ui/wallet.ui'

interface PaymentState {
  /** The caller's unpaid winner holds. */
  holds:      PendingPayment[]
  /** Auctions paid in this browser session; lets the panel say "paid" once the hold has left the server list. */
  paidIds:    ReadonlySet<string>
  isLoading:  boolean
  hasFetched: boolean
  /** The latest load failed. The panel only surfaces it before the first successful load. */
  loadFailed: boolean
  load:       () => Promise<void>
  /** Reloads only if the store has been loaded before (nobody is looking otherwise). */
  refresh:    () => Promise<void>
  markPaid:   (auctionId: string) => void
  reset:      () => void
}

export const usePaymentStore = create<PaymentState>((set, get) => {
  /** Bumped by every load and by reset(); only the latest load may write its result. */
  let seq = 0

  return {
    holds:      [],
    paidIds:    new Set<string>(),
    isLoading:  false,
    hasFetched: false,
    loadFailed: false,

    load: async () => {
      const mine = ++seq
      set({ isLoading: true })
      try {
        const dtos = await walletService.getPendingPayments()
        if (mine !== seq) return
        const paid = get().paidIds
        set({
          holds:      dtos.map(mapPendingPaymentResponse).filter((h) => !paid.has(h.auctionId)),
          hasFetched: true,
          loadFailed: false,
          isLoading:  false,
        })
      } catch {
        if (mine !== seq) return
        set({ loadFailed: true, isLoading: false })
      }
    },

    refresh: async () => {
      if (get().hasFetched) await get().load()
    },

    markPaid: (auctionId) =>
      set((s) => ({
        holds:   s.holds.filter((h) => h.auctionId !== auctionId),
        paidIds: new Set(s.paidIds).add(auctionId),
      })),

    reset: () => {
      seq++
      set({ holds: [], paidIds: new Set<string>(), isLoading: false, hasFetched: false, loadFailed: false })
    },
  }
})
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npx vitest run store/paymentStore.test.ts`
Expected: PASS (all tests).

- [ ] **Step 5: Create the hook**

Create `frontend/hooks/usePendingPayments.ts`:

```ts
'use client'

import { useEffect } from 'react'
import { useAuthStore } from '@/store/authStore'
import { usePaymentStore } from '@/store/paymentStore'

/**
 * The winner's pending payments from the shared store. Loads once per mount when the store is empty;
 * live pushes and payments keep it fresh (see useUserNotifications and PayNowDialog).
 */
export function usePendingPayments() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const holds      = usePaymentStore((s) => s.holds)
  const paidIds    = usePaymentStore((s) => s.paidIds)
  const isLoading  = usePaymentStore((s) => s.isLoading)
  const hasFetched = usePaymentStore((s) => s.hasFetched)
  const loadFailed = usePaymentStore((s) => s.loadFailed)
  const load       = usePaymentStore((s) => s.load)

  useEffect(() => {
    // Read the live state, not the render snapshot: a failed load must not retry in a loop.
    if (isAuthenticated && !usePaymentStore.getState().hasFetched) void load()
  }, [isAuthenticated, load])

  return { holds, paidIds, isLoading, hasFetched, loadFailed, refetch: load }
}
```

- [ ] **Step 6: Wire the notifications bridge**

In `frontend/hooks/useUserNotifications.ts`:

Add imports after the existing `useNotificationStore` import:

```ts
import { usePaymentStore } from '@/store/paymentStore'
import { isPaymentPush } from '@/lib/payment-utils'
```

Replace the early-return block:

```ts
    if (!userId || isAdmin) {
      store.reset()
      return
    }
```

with:

```ts
    if (!userId || isAdmin) {
      store.reset()
      usePaymentStore.getState().reset()
      return
    }
```

Replace the `onMessage` handler body's last line:

```ts
      if (added && shouldToast(added)) showNotificationToast(added)
```

with:

```ts
      if (added && shouldToast(added)) showNotificationToast(added)
      if (added && isPaymentPush(added.serverType)) void usePaymentStore.getState().refresh()
```

In the effect cleanup, after `useNotificationStore.getState().reset() // drop this user's data; late responses are ignored (epoch)` add:

```ts
      usePaymentStore.getState().reset() // same for the previous user's payment holds
```

- [ ] **Step 7: Typecheck, lint, run all tests**

Run: `npx tsc --noEmit && npx eslint store hooks && npx vitest run`
Expected: no type or lint errors; all tests pass. (Do not commit.)

---

### Task 4: PayNowDialog

**Files:**
- Create: `frontend/components/payment/PayNowDialog.tsx`

**Interfaces:**
- Consumes: `PendingPayment` (Task 1); `walletService.confirmPayment`, `PaymentRequestError` (Task 1); `getShortfall`, `formatHoursLeft` (Task 2); `classifyPaymentError`, `describePaymentFailure`, `PaymentFailure` (Task 2); `usePaymentStore.markPaid/refresh` (Task 3); `useWallet()` (existing: `available`, `isLoading`, `refetch`, `depositFunds`).
- Produces: `PayNowDialog({ hold: PendingPayment; title: string; open: boolean; onOpenChange: (open: boolean) => void })`. On a successful payment it calls `markPaid`, refetches wallet and payments, toasts "Payment complete" and closes itself via `onOpenChange(false)`.

Components are verified manually (no component test harness in this repo), so there is no failing-test step; the gates are typecheck, lint, and the manual checks at the end.

- [ ] **Step 1: Create the component**

Create `frontend/components/payment/PayNowDialog.tsx`:

```tsx
'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { CurrencyInput } from '@/components/ui/currency-input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { useWallet } from '@/hooks/useWallet'
import { formatCurrency } from '@/lib/format'
import { fromCents, toCents } from '@/lib/money'
import { classifyPaymentError, describePaymentFailure, type PaymentFailure } from '@/lib/payment-errors'
import { formatHoursLeft, getShortfall } from '@/lib/payment-utils'
import { getErrorMessage } from '@/lib/utils'
import { walletService } from '@/services/wallet.service'
import { usePaymentStore } from '@/store/paymentStore'
import type { PendingPayment } from '@/types/ui/wallet.ui'

interface PayNowDialogProps {
  hold:         PendingPayment
  title:        string
  open:         boolean
  onOpenChange: (open: boolean) => void
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-mono">{value}</dd>
    </div>
  )
}

export function PayNowDialog({ hold, title, open, onOpenChange }: PayNowDialogProps) {
  const router = useRouter()
  const { available, isLoading: walletLoading, refetch: refetchWallet, depositFunds } = useWallet()

  // What the user typed in the top-up field; null means "use the shortfall".
  const [typedAmount, setTypedAmount] = useState<number | null>(null)
  const [isToppingUp, setIsToppingUp] = useState(false)
  const [isPaying, setIsPaying]       = useState(false)
  const [failure, setFailure]         = useState<PaymentFailure | null>(null)
  const [prevOpen, setPrevOpen]       = useState(open)

  // Opening the dialog starts clean (state adjusted during render, as CurrencyInput does, not in an effect).
  if (open !== prevOpen) {
    setPrevOpen(open)
    if (open) {
      setFailure(null)
      setTypedAmount(null)
    }
  }

  const failureView = failure ? describePaymentFailure(failure) : null
  const busy        = isPaying || isToppingUp
  // Until the wallet has loaded, `available` is a meaningless 0: do not claim a shortfall.
  const shortfall   = walletLoading ? null : getShortfall(hold, available)
  const blocked     = hold.expired || (failureView?.disablesForm ?? false)
  // Prefilled with the missing amount; follows the shortfall after a partial top-up until the user types.
  const topUpAmount = typedAmount ?? shortfall ?? 0

  async function handleTopUp() {
    const amount = fromCents(toCents(topUpAmount))
    if (amount <= 0) return
    setIsToppingUp(true)
    try {
      await depositFunds(amount) // credits the mock wallet, then refetches it
      setTypedAmount(null)
      toast.success('Funds added')
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Failed to add funds'))
    } finally {
      setIsToppingUp(false)
    }
  }

  async function handlePay() {
    setFailure(null)
    setIsPaying(true)
    try {
      await walletService.confirmPayment(hold.auctionId)
      usePaymentStore.getState().markPaid(hold.auctionId)
      toast.success('Payment complete')
      onOpenChange(false)
      void refetchWallet()
      void usePaymentStore.getState().refresh()
    } catch (err: unknown) {
      const f = classifyPaymentError(err)
      if (f.kind === 'unauthenticated') {
        router.push('/login')
        return
      }
      if (f.kind === 'insufficient-balance' || f.kind === 'expired' || f.kind === 'no-longer-due') {
        // Our view of the balance or of the hold is stale: resync both.
        void refetchWallet()
        void usePaymentStore.getState().refresh()
      }
      setFailure(f)
    } finally {
      setIsPaying(false)
    }
  }

  const payDisabled = busy || walletLoading || shortfall !== null || blocked

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent showCloseButton={!busy}>
        <DialogHeader>
          <DialogTitle>Complete payment</DialogTitle>
          <DialogDescription>{title}</DialogDescription>
        </DialogHeader>

        <dl className="flex flex-col gap-2 text-sm">
          <SummaryRow label="Winning price" value={formatCurrency(hold.totalAmount)} />
          <SummaryRow label="Deposit applied" value={`− ${formatCurrency(hold.depositApplied)}`} />
          <div className="flex items-center justify-between border-t pt-2">
            <dt className="font-medium">Remaining to pay</dt>
            <dd className="font-mono font-medium">{formatCurrency(hold.remaining)}</dd>
          </div>
          <SummaryRow label="Time left" value={hold.expired ? 'Deadline passed' : formatHoursLeft(hold.hoursLeft)} />
        </dl>

        {shortfall !== null && !blocked && (
          <div className="flex flex-col gap-2 rounded-lg bg-[var(--color-wallet-bg)] p-3">
            <p className="text-xs text-muted-foreground">
              You have {formatCurrency(available)} available and need {formatCurrency(hold.remaining)}.
              Add at least {formatCurrency(shortfall)} to continue.
            </p>
            <div className="flex gap-2">
              <div className="flex-1">
                <CurrencyInput value={topUpAmount} onChange={setTypedAmount} placeholder="0.00" disabled={busy} />
              </div>
              <Button
                variant="outline"
                disabled={busy || topUpAmount <= 0}
                onClick={() => void handleTopUp()}
              >
                {isToppingUp && <Loader2 className="size-4 animate-spin" />}
                Add funds
              </Button>
            </div>
          </div>
        )}

        {hold.expired && (
          <p role="alert" className="text-xs text-[var(--color-danger-text)]">
            The payment deadline has passed.
          </p>
        )}

        {failureView && (
          <p role="alert" className="text-xs text-[var(--color-danger-text)]">
            {failureView.message}
          </p>
        )}

        <DialogFooter>
          <Button variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant="brand" disabled={payDisabled} onClick={() => void handlePay()}>
            {isPaying && <Loader2 className="size-4 animate-spin" />}
            {hold.remaining > 0 ? `Pay ${formatCurrency(hold.remaining)}` : 'Confirm payment'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 2: Typecheck and lint**

Run: `npx tsc --noEmit && npx eslint components/payment`
Expected: no errors. If `Button` rejects `variant="brand"` or `DialogContent` rejects `showCloseButton`, check `components/ui/button.tsx` and `components/ui/dialog.tsx`; `BuyNowButton.tsx` uses both exactly like this, so they exist. (Do not commit.)

---

### Task 5: Payments due list on /wallet

**Files:**
- Create: `frontend/hooks/useAuctionPreview.ts`
- Create: `frontend/components/wallet/PaymentsDueList.tsx`
- Modify: `frontend/app/(dashboard)/wallet/page.tsx`

**Interfaces:**
- Consumes: `usePendingPayments` (Task 3), `PayNowDialog` (Task 4), `toAuctionPreview` / `AuctionPreview` / `formatHoursLeft` (Task 2), `auctionService.getAuctionById(id): Promise<AuctionDetail | null>` (existing), `ImageThumbnail` (existing, `components/shared/ImageThumbnail`).
- Produces: `useAuctionPreview(auctionId: string): AuctionPreview | null | undefined` (`undefined` while loading, `null` when unavailable); `PaymentsDueList()` (renders nothing when there is nothing to show).

- [ ] **Step 1: Create the preview hook**

Create `frontend/hooks/useAuctionPreview.ts`:

```ts
'use client'

import { useEffect, useState } from 'react'
import { auctionService } from '@/services/auction.service'
import { toAuctionPreview, type AuctionPreview } from '@/lib/payment-utils'

/** Per-session cache of the in-flight/finished lookups; failures are evicted so the next mount retries. */
const cache = new Map<string, Promise<AuctionPreview | null>>()

function loadPreview(auctionId: string): Promise<AuctionPreview | null> {
  const cached = cache.get(auctionId)
  if (cached) return cached
  const request = auctionService
    .getAuctionById(auctionId)
    .then((auction) => (auction ? toAuctionPreview(auction) : null))
    .catch(() => null)
  cache.set(auctionId, request)
  void request.then((preview) => {
    if (!preview) cache.delete(auctionId)
  })
  return request
}

/** Title and image of an auction. `undefined` while loading, `null` when it could not be loaded. */
export function useAuctionPreview(auctionId: string): AuctionPreview | null | undefined {
  const [preview, setPreview] = useState<AuctionPreview | null | undefined>(undefined)

  useEffect(() => {
    let cancelled = false
    void loadPreview(auctionId).then((p) => {
      if (!cancelled) setPreview(p)
    })
    return () => {
      cancelled = true
    }
  }, [auctionId])

  return preview
}
```

- [ ] **Step 2: Create the list component**

Create `frontend/components/wallet/PaymentsDueList.tsx`:

```tsx
'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { PayNowDialog } from '@/components/payment/PayNowDialog'
import { useAuctionPreview } from '@/hooks/useAuctionPreview'
import { usePendingPayments } from '@/hooks/usePendingPayments'
import { formatCurrency } from '@/lib/format'
import { formatHoursLeft } from '@/lib/payment-utils'
import type { PendingPayment } from '@/types/ui/wallet.ui'

function PaymentDueRow({ hold }: { hold: PendingPayment }) {
  const preview = useAuctionPreview(hold.auctionId)
  const [open, setOpen] = useState(false)
  const title = preview?.title ?? 'Auction'

  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className="relative size-12 shrink-0 overflow-hidden rounded-md bg-muted">
        {preview?.imageUrl ? (
          <ImageThumbnail src={preview.imageUrl} alt={title} fill className="object-cover" sizes="48px" />
        ) : preview === undefined ? (
          <Skeleton className="absolute inset-0 size-full rounded-none" />
        ) : null}
      </div>

      <div className="min-w-0 flex-1">
        <Link
          href={`/auctions/${hold.auctionId}`}
          className="block truncate text-sm font-medium underline-offset-2 hover:underline"
        >
          {title}
        </Link>
        <p className="text-xs text-muted-foreground">
          {hold.expired ? 'Deadline passed' : formatHoursLeft(hold.hoursLeft)}
        </p>
      </div>

      <div className="flex flex-col items-end gap-1">
        <span className="font-mono text-sm font-medium">{formatCurrency(hold.remaining)}</span>
        <Button size="sm" variant="brand" disabled={hold.expired} onClick={() => setOpen(true)}>
          Pay
        </Button>
      </div>

      <PayNowDialog hold={hold} title={title} open={open} onOpenChange={setOpen} />
    </div>
  )
}

export function PaymentsDueList() {
  const { holds, hasFetched, loadFailed, refetch } = usePendingPayments()

  if (!hasFetched) {
    if (!loadFailed) return null
    return (
      <div className="flex items-center justify-between rounded-xl border bg-card px-4 py-3 text-sm">
        <span className="text-muted-foreground">Couldn&apos;t load your payments due.</span>
        <Button size="sm" variant="outline" onClick={() => void refetch()}>
          Retry
        </Button>
      </div>
    )
  }
  if (holds.length === 0) return null

  return (
    <section aria-labelledby="payments-due-heading" className="flex flex-col rounded-xl border bg-card">
      <h2 id="payments-due-heading" className="px-4 pb-1 pt-4 text-sm font-medium">
        Payments due
      </h2>
      <ul>
        {holds.map((hold, i) => (
          <li key={hold.auctionId} className={i > 0 ? 'border-t' : undefined}>
            <PaymentDueRow hold={hold} />
          </li>
        ))}
      </ul>
    </section>
  )
}
```

- [ ] **Step 3: Add the list to the wallet page**

Replace `frontend/app/(dashboard)/wallet/page.tsx` with:

```tsx
'use client'

import { CircleDollarSign } from 'lucide-react'
import { WalletPanel } from '@/components/wallet/WalletPanel'
import { PaymentsDueList } from '@/components/wallet/PaymentsDueList'

export default function WalletPage() {
  return (
    <div className="flex flex-col gap-6 max-w-lg">
      <div className="flex items-center gap-2">
        <CircleDollarSign className="size-5 text-[var(--color-text-brand)]" />
        <h1 className="font-display font-medium text-[length:var(--font-size-2xl)]">Wallet</h1>
      </div>
      <PaymentsDueList />
      <div className="rounded-xl border bg-card flex flex-col gap-4 py-4">
        <WalletPanel />
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Typecheck and lint**

Run: `npx tsc --noEmit && npx eslint hooks components/wallet "app/(dashboard)/wallet"`
Expected: no errors. If `ImageThumbnail`'s `sizes` or `Button size="sm"` is rejected, mirror `components/auction/browse/AuctionBrowseCardHorizontal.tsx` (uses `sizes`) and `components/ui/button.tsx` sizes. (Do not commit.)

---

### Task 6: Payment status block in the Won panel

**Files:**
- Create: `frontend/components/payment/PaymentStatusBlock.tsx`
- Modify: `frontend/components/auction/BidPanelEnded.tsx`

**Interfaces:**
- Consumes: `usePendingPayments` (Task 3), `PayNowDialog` (Task 4), `derivePaymentPanelState`, `isRecentWin`, `formatHoursLeft`, `PREPARING_POLL_MS`, `PREPARING_WINDOW_MS` (Task 2), `AuctionDetail.id/title/completedAt` (existing).
- Produces: `PaymentStatusBlock({ auctionId: string; title: string; completedAt?: Date })`.

- [ ] **Step 1: Create the block**

Create `frontend/components/payment/PaymentStatusBlock.tsx`:

```tsx
'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PayNowDialog } from '@/components/payment/PayNowDialog'
import { usePendingPayments } from '@/hooks/usePendingPayments'
import { formatCurrency } from '@/lib/format'
import {
  PREPARING_POLL_MS,
  PREPARING_WINDOW_MS,
  derivePaymentPanelState,
  formatHoursLeft,
  isRecentWin,
} from '@/lib/payment-utils'

interface PaymentStatusBlockProps {
  auctionId:    string
  title:        string
  completedAt?: Date
}

export function PaymentStatusBlock({ auctionId, title, completedAt }: PaymentStatusBlockProps) {
  const { holds, paidIds, hasFetched, loadFailed, refetch } = usePendingPayments()
  const [dialogOpen, setDialogOpen] = useState(false)
  // Only a fresh win can still be waiting for its hold; an old win with no hold is simply "nothing due".
  const [windowOpen, setWindowOpen] = useState(() => isRecentWin(completedAt))

  useEffect(() => {
    if (!windowOpen) return
    const t = setTimeout(() => setWindowOpen(false), PREPARING_WINDOW_MS)
    return () => clearTimeout(t)
  }, [windowOpen])

  const { state, hold } = derivePaymentPanelState({
    holds, auctionId, paidIds, hasFetched, loadFailed, pollWindowOpen: windowOpen,
  })

  useEffect(() => {
    if (state !== 'preparing') return
    const i = setInterval(() => void refetch(), PREPARING_POLL_MS)
    return () => clearInterval(i)
  }, [state, refetch])

  switch (state) {
    case 'loading':
      return <Skeleton className="h-11 w-full" />

    case 'error':
      return (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-muted-foreground">We couldn&apos;t load your payment details.</p>
          <Button variant="outline" className="w-full" onClick={() => void refetch()}>
            Retry
          </Button>
        </div>
      )

    case 'preparing':
      return (
        <div className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
          <Loader2 className="size-4 animate-spin" />
          Preparing your payment…
        </div>
      )

    case 'due':
      if (!hold) return null
      return (
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">Remaining to pay</span>
            <span className="font-mono font-medium">{formatCurrency(hold.remaining)}</span>
          </div>
          <p className="text-xs text-muted-foreground">{formatHoursLeft(hold.hoursLeft)}</p>
          <Button variant="brand" className="h-11 w-full" onClick={() => setDialogOpen(true)}>
            Proceed to payment →
          </Button>
          <PayNowDialog hold={hold} title={title} open={dialogOpen} onOpenChange={setDialogOpen} />
        </div>
      )

    case 'expired':
      return (
        <p role="alert" className="text-sm text-[var(--color-danger-text)]">
          The payment deadline has passed. Your deposit may be forfeited.
        </p>
      )

    case 'paid':
      return (
        <div className="flex items-center gap-2 text-sm font-medium" role="status">
          <CheckCircle2 className="size-4 text-[var(--color-success-default)]" />
          Payment complete
        </div>
      )

    case 'none':
      return (
        <p className="text-sm text-muted-foreground">
          No payment due. See your{' '}
          <Link href="/wallet" className="underline underline-offset-2">
            wallet history
          </Link>{' '}
          for details.
        </p>
      )
  }
}
```

- [ ] **Step 2: Replace the stub button in `BidPanelEnded`**

In `frontend/components/auction/BidPanelEnded.tsx`:

Add the import after `import { PanelFooter } from './PanelFooter'`:

```tsx
import { PaymentStatusBlock } from '@/components/payment/PaymentStatusBlock'
```

Change the destructure line:

```tsx
  const { status, categoryId, currentBid } = auction
```

to:

```tsx
  const { id, title, status, categoryId, currentBid, completedAt } = auction
```

Replace this stub button:

```tsx
          <button
            type="button"
            className="w-full h-11 rounded px-4 text-sm font-medium bg-[var(--color-text-brand)] text-white border border-[var(--color-text-brand)] transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
            onClick={() => {}}
          >
            Proceed to payment →
          </button>
```

with:

```tsx
          <PaymentStatusBlock auctionId={id} title={title} completedAt={completedAt} />
```

Leave the "Contact seller" button and the three-step list unchanged.

- [ ] **Step 3: Typecheck and lint**

Run: `npx tsc --noEmit && npx eslint components/payment components/auction/BidPanelEnded.tsx`
Expected: no errors. (Do not commit.)

- [ ] **Step 4: Manual verification (needs the stack running: `docker compose up -d` in `backend/`, the services, then `npm run dev`)**

Check each, with a bidder who has funds and a seller, using the mock wallet:
1. Win by Buy It Now with enough funds: the Won panel briefly shows "Preparing your payment…", then "Remaining to pay" and "Proceed to payment →". Pay opens the dialog, shows price / deposit applied / remaining / time left. Pay: dialog closes, toast "Payment complete", panel shows "Payment complete", wallet balance dropped by the remainder.
2. Win with **insufficient** funds (remaining > available balance): the dialog shows the shortfall block prefilled with the missing amount and Pay is disabled. Add funds, shortfall block disappears, Pay enables, payment succeeds.
3. Double-click Pay quickly: exactly one confirm request in the Network tab; the dialog cannot be dismissed while it runs.
4. Reload the auction page after paying: panel reads "No payment due. See your wallet history" (known limitation), with **no** 30-second "Preparing" flash for an old win.
5. Pay the same auction from a second tab, then press Pay in the first: message "This payment is no longer due." and Pay disabled.

---

### Task 7: Docs, spec sync and final verification

**Files:**
- Modify: `docs/diagrams/05-winner-payment-flow.md`
- Modify: `docs/epics/wallet/epic.md`
- Modify: `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`

**Interfaces:** none (documentation and verification).

- [ ] **Step 1: Rewrite the winner payment diagram**

Replace the entire contents of `docs/diagrams/05-winner-payment-flow.md` with:

````markdown
# Winner Payment & Forfeit Flow

Applies to a win by bid or by Buy It Now. Phase 1: wallet top-up is a mock (instant credit); real gateway is Phase 2. There is no platform fee on a sale.

```mermaid
sequenceDiagram
    autonumber
    actor Winner
    participant App as Frontend
    participant GW as API Gateway
    participant AS as Auction Service
    participant MQ as Kafka
    participant WS as Wallet Service
    participant NS as Media Service

    Note over Winner, NS: A. Auction closes (scheduler, admin or Buy It Now)
    AS->>MQ: AuctionEndedEvent (auction-ended-topic): winnerId, winningBidAmount
    MQ->>WS: Consume
    WS->>WS: Refund every other LOCKED deposit (AUCTION_LOST); the winner's deposit stays LOCKED
    WS->>WS: Create payment hold: deposit applied, remaining = price − deposit, deadline = now + 48h
    alt available balance ≥ remaining
        WS->>WS: Move remaining from available to locked (HOLD), fundsHeld = true
    else balance too low
        WS->>WS: No money moves, fundsHeld = false
    end
    WS->>MQ: PaymentEvent REQUIRED (payment-event-topic)
    MQ->>NS: Consume
    NS-->>Winner: Payment-required email and in-app PAYMENT_REQUIRED push

    Note over Winner, NS: B. Winner pays (auction page "Proceed to payment" or /wallet "Payments due")
    Winner->>App: Opens the auction page or /wallet
    App->>GW: GET /api/v1/wallets/payments/pending
    GW->>WS: Route
    WS-->>App: Holds: remaining, fundsHeld, hoursLeft, expired
    opt fundsHeld = false and available < remaining
        Winner->>App: Adds funds in the pay dialog (mock top-up)
        App->>GW: POST /api/v1/wallets/deposit
        GW->>WS: Route
        WS-->>App: Balance credited immediately
    end
    Winner->>App: Clicks Pay
    App->>GW: POST /api/v1/wallets/payments/confirm {auctionId}
    GW->>WS: Route
    alt expired, already paid, or insufficient balance
        WS-->>App: 400 PAYMENT_DEADLINE_EXPIRED / 409 PAYMENT_NOT_PENDING / 400 INSUFFICIENT_BALANCE
    else valid
        WS->>WS: Lock hold, then both wallets in ascending id order
        WS->>WS: Winner pays the remainder; deposit lock RELEASED; any deposit excess refunded
        WS->>WS: Seller credited the full winning price (no fee); hold COMPLETED
        WS->>MQ: PaymentEvent COMPLETED
        WS-->>App: 200 ConfirmPaymentResponse
        MQ->>NS: Consume
        NS-->>Winner: Payment receipt (email, in-app PAYMENT_RECEIVED push)
    end

    Note over WS, NS: C. Deadline missed (ForfeitScheduler, every 5 minutes)
    loop each PENDING_PAYMENT hold past its deadline
        WS->>WS: Release the held remainder back to available (HOLD_CANCEL)
        WS->>WS: Forfeit the winner's deposit to the platform wallet (FORFEIT); hold FORFEITED
        WS->>MQ: PaymentEvent FAILED
        MQ->>NS: Consume
        NS-->>Winner: Payment-failed notice (in-app PAYMENT_FAILED push)
    end
```

The frontend refetches pending payments when a `PAYMENT_REQUIRED`, `PAYMENT_RECEIVED` or `PAYMENT_FAILED` push arrives. Auction-service does not yet learn that the winner paid (`winner_paid_at`); see the WALLET-305 follow-ups.
````

- [ ] **Step 2: Add the frontend section to the wallet epic**

In `docs/epics/wallet/epic.md`, insert immediately before the line `## Document History`:

```markdown
## Frontend: Winner Payment UI (as built, Phase 1)

A winner pays from either of two places, both backed by `GET /api/v1/wallets/payments/pending` and `POST /api/v1/wallets/payments/confirm`:

- **Auction page:** in the Won panel, `PaymentStatusBlock` shows the remainder and a "Proceed to payment" button that opens `PayNowDialog`. States: loading, error (retry), preparing (fresh win, hold not created yet; polls 3 s for up to 30 s), due, expired, paid, none.
- **Wallet page:** a "Payments due" list (auction image and title, remainder, time left, Pay).

`PayNowDialog` shows price, deposit applied, remainder and time left. When the funds are not yet held and the available balance is short, it shows the shortfall and an inline mock top-up (`POST /api/v1/wallets/deposit`) before Pay is enabled. State is shared through `paymentStore`, kept fresh by `PAYMENT_REQUIRED` / `PAYMENT_RECEIVED` / `PAYMENT_FAILED` notification pushes.

**Known limitation:** after a page reload a paid auction reads "No payment due" rather than "Paid", because the pending endpoint returns only unpaid holds and wallet transactions expose no auction reference. Fixing it needs a backend change.

Design: `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`.

---

```

- [ ] **Step 3: Sync the spec with the plan's deltas**

In `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`:

(a) In **Frontend Design → State**, replace the bullet beginning `- \`hooks/usePendingPayments.ts\`` with:

```markdown
- `hooks/usePendingPayments.ts`: loads the store once per mount when it is empty; exposes `holds`, `paidIds`, `isLoading`, `hasFetched`, `loadFailed`, `refetch`. Live pushes are applied in `hooks/useUserNotifications.ts` (which already owns the push handler): a `PAYMENT_REQUIRED`, `PAYMENT_RECEIVED` or `PAYMENT_FAILED` push calls `paymentStore.refresh()`, which reloads only if the store was loaded before. The store is reset on logout/user change. After a successful pay, `PayNowDialog` calls `markPaid`, then refetches the wallet and refreshes the store.
```

(b) In the same section, replace the `derivePaymentPanelState` bullet's signature and list so it reads:

```markdown
  - `derivePaymentPanelState({ holds, auctionId, paidIds, hasFetched, loadFailed, pollWindowOpen })` → `{ state: loading | error | preparing | due | expired | paid | none, hold }`.
  - `isRecentWin(completedAt)`: whether the auction completed within 2 minutes (or has no `completedAt` yet). `BidPanelEnded` opens the "preparing" window only for a recent win, so revisiting an old paid win never flashes "Preparing your payment…".
```

(c) In the **Components → BidPanelEnded** table, add a row after `loading`:

```markdown
| `error` | "We couldn't load your payment details." with Retry (only before the first successful load) |
```

(d) In **PayNowDialog**, replace `an amount field prefilled with it (validated with \`parseDollarInput\`)` with `an amount field (the existing \`CurrencyInput\`, as in \`WalletPanel\`) prefilled with it`.

(e) In **Types, service, mapper**, after the mapper bullet add:

```markdown
- The UI `PendingPayment` omits `deadline`; the UI uses `hoursLeft` and `expired` only.
```

- [ ] **Step 4: Run the full verification**

Run: `npm test && npm run lint && npm run build`
Expected: all Vitest tests pass; ESLint passes with no new errors; the Next.js production build succeeds. If `npm run lint` reports pre-existing errors in files this plan did not touch, report them rather than fixing them.

- [ ] **Step 5: Final working-tree check (no commit)**

Run (from the repo root): `git status --short`
Expected: only the files listed in this plan's File Structure appear as new/modified (plus the spec and plan docs). Do not commit; the user commits at the end of the feature.
