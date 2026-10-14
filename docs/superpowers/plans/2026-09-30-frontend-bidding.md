# Frontend Bidding Integration (FE-101) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The auction detail page places real bids, shows real paginated bid history, and updates live over STOMP (new bids, anti-snipe extensions, end/cancel, private OUTBID alerts), with error UX that leads the user to the right action.

**Architecture:** Pure, unit-tested logic lives in `lib/` (money, bid-error classification, live-state reducers, STOMP message parsing, WS URL building). A rewritten Zustand `auctionStore` holds one `LiveAuctionState` per open auction page, updated only through those monotonic reducers. A client bridge component hydrates the store from the server-rendered snapshot and runs the STOMP hook; `BidPanel`, `BidForm` and a new `LiveBidHistory` read the merged live view through `useLiveAuction`.

**Tech Stack:** Next.js 16.2 (App Router) · React 19 · TypeScript strict · Zustand 5 · Tailwind v4 · shadcn/Base UI · sonner · `@stomp/stompjs` 7.3 (native WebSocket) · Vitest 4 (new).

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (§6 real-time contract, §7 failure modes) and the Story 7 section of `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`.

## Global Constraints

- All commands run from `frontend/`. Tests: `npm test`. Types: `npx tsc --noEmit`. Lint: `npm run lint`. Build: `npm run build`.
- **Do not commit.** The user commits manually; there are no commit steps in this plan.
- TypeScript strict; **never use `any`**. Type every API payload and component prop.
- **Money is dollars (`number`) end-to-end**, exactly as the API sends it. Compare/add money only through `lib/money.ts` (integer cents internally). `formatCurrency(dollars)` for display; prices in `font-mono`.
- **Dates:** parse API/WS timestamps only with `new Date(isoString)` (offset-aware); compare with `getTime()`. REST `placedAt` carries the JVM offset, WS `placedAt` is UTC — both are correct instants.
- UI: compose shadcn/Base UI primitives from `components/ui/`, icons from `lucide-react`, toasts from `sonner` (`toast`), colours only via CSS variables (e.g. `text-[var(--color-danger-text)]`).
- Next.js 16 has breaking changes: before using any Next API not already used in the file you are editing, read the relevant page under `node_modules/next/dist/docs/`.
- Backend is out of scope — do not modify anything under `backend/`.
- Frontend tests are unit tests of pure `.ts` modules in the Vitest `node` environment. React components are verified by `tsc`, `lint`, `build` and the manual E2E in Task 6.

## Decisions (made while planning — cost if wrong in brackets)

1. **Native WebSocket, not `sockjs-client`.** Spring's SockJS endpoint also serves raw WebSocket at `/ws-notifications/websocket`, which the gateway route `/ws-notifications/**` already covers. This drops a dependency with known `global is not defined` issues in Next bundles and avoids the SockJS XHR sticky-routing risk. [Clients behind proxies that block WebSocket get no live updates; they still work via REST + resync.]
2. **Vitest** is added as the first frontend test runner (pure-module tests only). [One dev dependency.]
3. **Auto-bid switch stays visible but disabled ("coming soon")** — the backend has no auto-bid yet. [Small UI rework when auto-bid ships.]
4. **No "My bids" tab / dashboard wiring.** The backend only offers `GET /api/v1/bids/auction/{id}/my-bids` (per auction); the dashboard `/my-bids` page needs a cross-auction endpoint that does not exist. `getMyBids` is therefore not added (YAGNI). [Follow-up story.]
5. **Resync on connect:** on every successful STOMP connect, including the first (Task 4 ruling), the bridge re-fetches the auction + first history page and merges monotonically, covering events missed before the subscription was active or while disconnected. [Two extra GETs per connect.]

---

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `package.json` | modify | add `vitest`, `@stomp/stompjs`; remove `socket.io-client`; `test` script |
| `vitest.config.mts` | create | Vitest config with `@/` alias |
| `lib/money.ts` (+ `.test.ts`) | create | dollar parsing / cents arithmetic |
| `lib/auction-utils.ts` (+ `.test.ts`) | modify | add `minimumNextBid` |
| `types/mappers/auction.mapper.test.ts` | create | locks dollars pass-through |
| `types/api/bid.api.ts` | create | REST bid payloads + error body + codes |
| `types/ui/auction.ui.ts` | modify | add `BidEntry`; `BidHistoryItem extends BidEntry` |
| `types/mappers/bid.mapper.ts` (+ `.test.ts`) | create | API → `BidEntry` → `BidHistoryItem` |
| `services/bid.service.ts` (+ `.test.ts`) | create | `placeBid`, `getAuctionBids`, `BidRequestError` |
| `lib/bid-errors.ts` (+ `.test.ts`) | create | classify failures, describe them for the UI |
| `services/auction.service.ts`, `lib/mock-data.ts` | modify/delete | remove mock bid history |
| `types/api/realtime.api.ts` | create | STOMP message contract (spec §6) |
| `lib/realtime/auction-live.ts` (+ `.test.ts`) | create | `LiveAuctionState` + monotonic reducers + display derivation |
| `lib/realtime/dispatch.ts` (+ `.test.ts`) | create | parse + route STOMP bodies |
| `lib/realtime/ws-url.ts` (+ `.test.ts`) | create | WS endpoint + token query |
| `lib/apiClient.ts` (+ `.test.ts`) | modify | export `getFreshAccessToken` |
| `store/auctionStore.ts` (+ `.test.ts`) | rewrite | live state store |
| `hooks/useAuctionSocket.ts` | rewrite | STOMP client lifecycle |
| `.env.example`, `.gitignore` | create/modify | `NEXT_PUBLIC_WS_URL` |
| `hooks/useLiveAuction.ts` | create | merged live view for components |
| `components/auction/AuctionLiveBridge.tsx` | create | hydrate + socket + resync |
| `components/auction/LiveBidHistory.tsx` | create | live history + load more |
| `components/auction/BidPanel.tsx`, `BidPanelLive.tsx`, `BidForm.tsx`, `BidButton.tsx`, `CurrentBidDisplay.tsx` | modify | live data, real submit, error UX |
| `app/auctions/[id]/page.tsx` | modify | real history, bridge |
| `CLAUDE.md`, `AGENTS.md` (frontend), roadmap | modify | docs |

---

### Task 1: Test runner and money units

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/vitest.config.mts`
- Create: `frontend/lib/money.ts`, `frontend/lib/money.test.ts`
- Modify: `frontend/lib/auction-utils.ts`; Create: `frontend/lib/auction-utils.test.ts`
- Create: `frontend/types/mappers/auction.mapper.test.ts`
- Modify: `frontend/components/auction/BidButton.tsx:9`, `frontend/components/auction/CurrentBidDisplay.tsx:9` (comments only)

**Interfaces:**
- Produces: `toCents(dollars: number): number`, `fromCents(cents: number): number`, `addDollars(a: number, b: number): number`, `isLessThan(a: number, b: number): boolean`, `parseDollarInput(raw: string): number | null` (all in `@/lib/money`); `minimumNextBid(input: { currentBid: number; bidIncrement: number; startingPrice: number; totalBids: number }): number` (in `@/lib/auction-utils`).

- [ ] **Step 1: Install Vitest and add the script**

Run: `npm install --save-dev vitest@^4.0.2`

Then add to `package.json` `"scripts"`: `"test": "vitest run"`.

- [ ] **Step 2: Create `vitest.config.mts`**

```ts
import { defineConfig } from 'vitest/config'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./', import.meta.url)) },
  },
  test: {
    environment: 'node',
    include: ['**/*.test.ts'],
    exclude: ['node_modules/**', '.next/**'],
  },
})
```

(`'@'` only matches `@` or `@/…`, so scoped packages like `@stomp/stompjs` are unaffected.)

- [ ] **Step 3: Write the failing money tests** — `lib/money.test.ts`

```ts
import { describe, expect, it } from 'vitest'
import { addDollars, fromCents, isLessThan, parseDollarInput, toCents } from '@/lib/money'

describe('money', () => {
  it('converts dollars to integer cents without float drift', () => {
    expect(toCents(0.1 + 0.2)).toBe(30)
    expect(toCents(1234.56)).toBe(123456)
    expect(fromCents(123456)).toBe(1234.56)
  })

  it('adds dollars exactly', () => {
    expect(addDollars(0.1, 0.2)).toBe(0.3)
    expect(addDollars(1499.99, 0.01)).toBe(1500)
  })

  it('compares at cent precision', () => {
    expect(isLessThan(10.004, 10)).toBe(false)
    expect(isLessThan(9.99, 10)).toBe(true)
  })

  it('parses user input with at most two decimals', () => {
    expect(parseDollarInput('1500')).toBe(1500)
    expect(parseDollarInput(' 12.5 ')).toBe(12.5)
    expect(parseDollarInput('12.345')).toBeNull()
    expect(parseDollarInput('')).toBeNull()
    expect(parseDollarInput('-3')).toBeNull()
    expect(parseDollarInput('0')).toBeNull()
    expect(parseDollarInput('abc')).toBeNull()
  })
})
```

- [ ] **Step 4: Run to verify it fails**

Run: `npm test -- lib/money.test.ts`
Expected: FAIL — cannot resolve `@/lib/money`.

- [ ] **Step 5: Implement `lib/money.ts`**

```ts
/**
 * Money helpers. Amounts are dollars (number) everywhere, matching the API;
 * arithmetic and comparison go through integer cents to avoid float drift.
 */
export function toCents(dollars: number): number {
  return Math.round(dollars * 100)
}

export function fromCents(cents: number): number {
  return cents / 100
}

export function addDollars(a: number, b: number): number {
  return fromCents(toCents(a) + toCents(b))
}

export function isLessThan(a: number, b: number): boolean {
  return toCents(a) < toCents(b)
}

const DOLLAR_INPUT = /^\d+(\.\d{1,2})?$/

/** Parses a positive dollar amount typed by the user; null when invalid. */
export function parseDollarInput(raw: string): number | null {
  const trimmed = raw.trim()
  if (!DOLLAR_INPUT.test(trimmed)) return null
  const value = Number(trimmed)
  return value > 0 ? value : null
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `npm test -- lib/money.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 7: Write the failing `minimumNextBid` + mapper tests**

`lib/auction-utils.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { minimumNextBid } from '@/lib/auction-utils'

describe('minimumNextBid', () => {
  it('is the starting price when there are no bids yet', () => {
    expect(minimumNextBid({ currentBid: 100, bidIncrement: 10, startingPrice: 100, totalBids: 0 })).toBe(100)
  })

  it('is current bid plus increment once bidding started', () => {
    expect(minimumNextBid({ currentBid: 150, bidIncrement: 10, startingPrice: 100, totalBids: 3 })).toBe(160)
  })

  it('adds cents exactly', () => {
    expect(minimumNextBid({ currentBid: 10.1, bidIncrement: 0.2, startingPrice: 1, totalBids: 1 })).toBe(10.3)
  })
})
```

`types/mappers/auction.mapper.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mapAuctionDetailResponse } from '@/types/mappers/auction.mapper'
import type { AuctionDetailResponse } from '@/types/api/auction.api'

const dto: AuctionDetailResponse = {
  id: 'a1', title: 'Omega', description: 'd',
  category: { id: 'c1', name: 'Watches', slug: 'watches' },
  startingPrice: 1000, bidIncrement: 25.5, buyNowPrice: null, depositAmount: 100,
  currentPrice: 1234.56, totalBids: 3, status: 'ACTIVE',
  startTime: '2026-10-01T10:00:00Z', endTime: '2026-10-02T10:00:00Z', originalEndTime: '2026-10-02T10:00:00Z',
  extensionCount: 0, images: [], seller: null, createdAt: '2026-09-30T10:00:00Z',
}

describe('mapAuctionDetailResponse money units', () => {
  it('passes dollar amounts through unchanged', () => {
    const a = mapAuctionDetailResponse(dto)
    expect(a.currentBid).toBe(1234.56)
    expect(a.startingPrice).toBe(1000)
    expect(a.bidIncrement).toBe(25.5)
    expect(a.depositAmount).toBe(100)
  })
})
```

- [ ] **Step 8: Run to verify the `minimumNextBid` test fails**

Run: `npm test`
Expected: `auction-utils.test.ts` FAIL (`minimumNextBid` is not exported); `auction.mapper.test.ts` PASS (it locks existing behaviour).

- [ ] **Step 9: Add `minimumNextBid` to `lib/auction-utils.ts`**

Add the import at the top and the function at the end of the file:

```ts
import { addDollars } from '@/lib/money'
```

```ts
/** Server rule: the first bid may equal the starting price; later bids must beat current + increment. */
export function minimumNextBid(input: {
  currentBid: number
  bidIncrement: number
  startingPrice: number
  totalBids: number
}): number {
  if (input.totalBids === 0) return input.startingPrice
  return addDollars(input.currentBid, input.bidIncrement)
}
```

- [ ] **Step 10: Fix the misleading unit comments**

In `components/auction/BidButton.tsx` change `amount?:    number         // cents — shows "Confirm $X.XX" when provided` to `amount?:    number         // dollars — shows "Confirm $X.XX" when provided`.
In `components/auction/CurrentBidDisplay.tsx` change `amount:               number        // cents` to `amount:               number        // dollars`.
(`BidForm.tsx` is rewritten in Task 5.)

- [ ] **Step 11: Verify**

Run: `npm test && npx tsc --noEmit && npm run lint`
Expected: all tests pass (8), no type or lint errors.

---

### Task 2: Bid REST layer — types, mapper, service, error classification

**Files:**
- Create: `frontend/types/api/bid.api.ts`
- Modify: `frontend/types/ui/auction.ui.ts` (the `BidHistoryItem` block)
- Create: `frontend/types/mappers/bid.mapper.ts`, `frontend/types/mappers/bid.mapper.test.ts`
- Create: `frontend/services/bid.service.ts`, `frontend/services/bid.service.test.ts`
- Create: `frontend/lib/bid-errors.ts`, `frontend/lib/bid-errors.test.ts`
- Modify: `frontend/services/auction.service.ts` (remove `getBidHistory`, `delay`, `MOCK_BIDS` import)
- Modify/Delete: `frontend/lib/mock-data.ts`
- Modify: `frontend/app/auctions/[id]/page.tsx` (temporary switch to `bidService`, finalised in Task 5)

**Interfaces:**
- Consumes: `apiFetch(url, options)` from `@/lib/apiClient`; `ApiResponse<T>`, `PageResponse<T>` from `@/types/api/common.api`.
- Produces:
  - `BidEntry` (`@/types/ui/auction.ui`): `Bid & { bidderName: string; bidderAvatarUrl?: string }`; `BidHistoryItem extends BidEntry { isCurrentUser: boolean; isWinning: boolean }`.
  - `PlaceBidRequest`, `PlaceBidResponse`, `BidHistoryResponse`, `ApiErrorResponse`, `BidErrorCode` (`@/types/api/bid.api`).
  - `mapBidHistoryResponse(dto: BidHistoryResponse): BidEntry`, `toBidHistoryItem(entry: BidEntry, ctx: { currentUserId: string | null; currentPrice: number }): BidHistoryItem`.
  - `bidService.placeBid(req: PlaceBidRequest): Promise<PlaceBidResponse>`, `bidService.getAuctionBids(auctionId: string, opts?: { page?: number; size?: number }): Promise<BidPage>`, `interface BidPage { items: BidEntry[]; page: number; hasNext: boolean; total: number }`, `BID_PAGE_SIZE = 20`, `EMPTY_BID_PAGE: BidPage`, `class BidRequestError extends Error { status: number; body: ApiErrorResponse | null }`.
  - `classifyBidError(err: unknown): BidFailure`, `describeBidFailure(f: BidFailure): BidFailureView` (`@/lib/bid-errors`).

- [ ] **Step 1: Create `types/api/bid.api.ts`**

```ts
/** Payloads of bidding-service `/api/v1/bids`. Amounts are dollars; timestamps are ISO-8601 with offset. */
export interface PlaceBidRequest {
  auctionId: string
  amount: number
}

export interface PlaceBidResponse {
  bidId: string
  auctionId: string
  amount: number
  placedAt: string
  currentPrice: number
  totalBids: number
  endTime: string
  extended: boolean
}

export interface BidHistoryResponse {
  id: string
  auctionId: string
  bidderId: string
  bidderName: string | null
  bidderAvatarUrl: string | null
  amount: number
  placedAt: string
  isAutoBid: boolean
  isAntiSnipingTriggered: boolean
}

export type BidErrorCode =
  | 'AUCTION_NOT_FOUND'
  | 'AUCTION_NOT_OPEN'
  | 'BID_TOO_LOW'
  | 'BID_OWN_AUCTION'
  | 'SERVICE_UNAVAILABLE'
  | 'BID_INSUFFICIENT_BALANCE'
  | 'WALLET_NOT_ACTIVE'
  | 'WALLET_NOT_FOUND'
  | 'DEPOSIT_LOCK_CLOSED'
  | 'INVALID_INPUT'

/** Common backend error body (`com.bidnow.common.dto.ErrorResponse`). */
export interface ApiErrorResponse {
  timestamp?: string
  status: number
  errorCode?: string
  message?: string
  path?: string
  errors?: Record<string, string>
}
```

- [ ] **Step 2: Add `BidEntry` in `types/ui/auction.ui.ts`**

Replace

```ts
export interface BidHistoryItem extends Bid {
  bidderName:      string
  bidderAvatarUrl?: string
  isCurrentUser:   boolean
  isWinning:       boolean
}
```

with

```ts
/** A bid with the bidder's display data — what the live store and history hold. */
export interface BidEntry extends Bid {
  bidderName:       string
  bidderAvatarUrl?: string
}

/** A bid as rendered for a specific viewer. */
export interface BidHistoryItem extends BidEntry {
  isCurrentUser: boolean
  isWinning:     boolean
}
```

- [ ] **Step 3: Write the failing mapper test** — `types/mappers/bid.mapper.test.ts`

```ts
import { describe, expect, it } from 'vitest'
import { mapBidHistoryResponse, toBidHistoryItem } from '@/types/mappers/bid.mapper'
import type { BidHistoryResponse } from '@/types/api/bid.api'

const dto: BidHistoryResponse = {
  id: 'b1', auctionId: 'a1', bidderId: 'u1', bidderName: 'Alice', bidderAvatarUrl: null,
  amount: 150.5, placedAt: '2026-10-01T19:00:00+07:00', isAutoBid: false, isAntiSnipingTriggered: true,
}

describe('mapBidHistoryResponse', () => {
  it('maps fields, parses the offset timestamp as an instant, keeps dollars', () => {
    const e = mapBidHistoryResponse(dto)
    expect(e).toMatchObject({ id: 'b1', auctionId: 'a1', bidderId: 'u1', bidderName: 'Alice', amount: 150.5, isAutoBid: false })
    expect(e.bidderAvatarUrl).toBeUndefined()
    expect(e.placedAt.toISOString()).toBe('2026-10-01T12:00:00.000Z')
  })

  it('falls back when the bidder name is missing', () => {
    expect(mapBidHistoryResponse({ ...dto, bidderName: null }).bidderName).toBe('Unknown bidder')
  })
})

describe('toBidHistoryItem', () => {
  const entry = mapBidHistoryResponse(dto)

  it('flags the viewer own bid and the winning bid', () => {
    const item = toBidHistoryItem(entry, { currentUserId: 'u1', currentPrice: 150.5 })
    expect(item.isCurrentUser).toBe(true)
    expect(item.isWinning).toBe(true)
  })

  it('is neither for another viewer once outbid', () => {
    const item = toBidHistoryItem(entry, { currentUserId: 'u2', currentPrice: 200 })
    expect(item.isCurrentUser).toBe(false)
    expect(item.isWinning).toBe(false)
  })

  it('never marks an anonymous viewer as the bidder', () => {
    expect(toBidHistoryItem(entry, { currentUserId: null, currentPrice: 150.5 }).isCurrentUser).toBe(false)
  })
})
```

- [ ] **Step 4: Run to verify it fails**

Run: `npm test -- types/mappers/bid.mapper.test.ts`
Expected: FAIL — cannot resolve `@/types/mappers/bid.mapper`.

- [ ] **Step 5: Implement `types/mappers/bid.mapper.ts`**

```ts
import type { BidHistoryResponse } from '@/types/api/bid.api'
import type { BidEntry, BidHistoryItem } from '@/types/ui/auction.ui'
import { toCents } from '@/lib/money'

export const UNKNOWN_BIDDER = 'Unknown bidder'

export function mapBidHistoryResponse(dto: BidHistoryResponse): BidEntry {
  return {
    id:              dto.id,
    auctionId:       dto.auctionId,
    bidderId:        dto.bidderId,
    bidderName:      dto.bidderName ?? UNKNOWN_BIDDER,
    bidderAvatarUrl: dto.bidderAvatarUrl ?? undefined,
    amount:          dto.amount,
    placedAt:        new Date(dto.placedAt),
    isAutoBid:       dto.isAutoBid,
  }
}

/** Bids on one auction strictly increase, so only the highest bid equals the current price. */
export function toBidHistoryItem(
  entry: BidEntry,
  ctx: { currentUserId: string | null; currentPrice: number },
): BidHistoryItem {
  return {
    ...entry,
    isCurrentUser: ctx.currentUserId !== null && entry.bidderId === ctx.currentUserId,
    isWinning:     toCents(entry.amount) === toCents(ctx.currentPrice),
  }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `npm test -- types/mappers/bid.mapper.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 7: Write the failing service test** — `services/bid.service.test.ts`

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { BidRequestError, bidService } from '@/services/bid.service'

const mockedFetch = vi.mocked(apiFetch)

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

beforeEach(() => mockedFetch.mockReset())

describe('bidService.placeBid', () => {
  it('POSTs the bid and unwraps the envelope', async () => {
    const data = {
      bidId: 'b1', auctionId: 'a1', amount: 150, placedAt: '2026-10-01T12:00:00Z',
      currentPrice: 150, totalBids: 4, endTime: '2026-10-01T13:00:00Z', extended: false,
    }
    mockedFetch.mockResolvedValue(jsonResponse(201, { status: 201, message: 'Bid placed', data }))

    await expect(bidService.placeBid({ auctionId: 'a1', amount: 150 })).resolves.toEqual(data)
    expect(mockedFetch).toHaveBeenCalledWith('/api/v1/bids', {
      method: 'POST',
      body: JSON.stringify({ auctionId: 'a1', amount: 150 }),
    })
  })

  it('throws BidRequestError carrying status and error body', async () => {
    const body = { status: 400, errorCode: 'BID_TOO_LOW', message: 'Bid must be at least 160', errors: { minimumBid: '160' } }
    mockedFetch.mockResolvedValue(jsonResponse(400, body))

    const err = await bidService.placeBid({ auctionId: 'a1', amount: 150 }).catch((e: unknown) => e)
    expect(err).toBeInstanceOf(BidRequestError)
    expect((err as BidRequestError).status).toBe(400)
    expect((err as BidRequestError).body).toEqual(body)
    expect((err as BidRequestError).message).toBe('Bid must be at least 160')
  })

  it('tolerates a non-JSON error body', async () => {
    mockedFetch.mockResolvedValue(new Response('Bad gateway', { status: 503 }))
    const err = await bidService.placeBid({ auctionId: 'a1', amount: 150 }).catch((e: unknown) => e)
    expect((err as BidRequestError).status).toBe(503)
    expect((err as BidRequestError).body).toBeNull()
  })
})

describe('bidService.getAuctionBids', () => {
  it('fetches a page and maps entries', async () => {
    mockedFetch.mockResolvedValue(jsonResponse(200, {
      status: 200, message: 'ok',
      data: {
        data: [{
          id: 'b1', auctionId: 'a1', bidderId: 'u1', bidderName: 'Alice', bidderAvatarUrl: null,
          amount: 150, placedAt: '2026-10-01T12:00:00Z', isAutoBid: false, isAntiSnipingTriggered: false,
        }],
        pagination: { page: 1, limit: 20, total: 21, totalPages: 2, hasNext: false, hasPrev: true },
      },
    }))

    const page = await bidService.getAuctionBids('a1', { page: 1 })
    expect(mockedFetch).toHaveBeenCalledWith('/api/v1/bids/auction/a1?page=1&size=20', { cache: 'no-store' })
    expect(page.page).toBe(1)
    expect(page.hasNext).toBe(false)
    expect(page.total).toBe(21)
    expect(page.items[0].bidderName).toBe('Alice')
    expect(page.items[0].placedAt).toBeInstanceOf(Date)
  })

  it('throws BidRequestError on failure', async () => {
    mockedFetch.mockResolvedValue(jsonResponse(404, { status: 404, errorCode: 'AUCTION_NOT_FOUND' }))
    await expect(bidService.getAuctionBids('a1')).rejects.toBeInstanceOf(BidRequestError)
  })
})
```

- [ ] **Step 8: Run to verify it fails**

Run: `npm test -- services/bid.service.test.ts`
Expected: FAIL — cannot resolve `@/services/bid.service`.

- [ ] **Step 9: Implement `services/bid.service.ts`**

```ts
import { apiFetch } from '@/lib/apiClient'
import { mapBidHistoryResponse } from '@/types/mappers/bid.mapper'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  ApiErrorResponse,
  BidHistoryResponse,
  PlaceBidRequest,
  PlaceBidResponse,
} from '@/types/api/bid.api'
import type { BidEntry } from '@/types/ui/auction.ui'

export const BID_PAGE_SIZE = 20

export interface BidPage {
  items:   BidEntry[]
  page:    number
  hasNext: boolean
  total:   number
}

export const EMPTY_BID_PAGE: BidPage = { items: [], page: 0, hasNext: false, total: 0 }

/** A non-2xx bidding API response; `body` is the backend ErrorResponse when it was JSON. */
export class BidRequestError extends Error {
  constructor(readonly status: number, readonly body: ApiErrorResponse | null) {
    super(body?.message ?? `Request failed with status ${status}`)
    this.name = 'BidRequestError'
  }
}

async function toBidRequestError(response: Response): Promise<BidRequestError> {
  let body: ApiErrorResponse | null = null
  try {
    body = (await response.json()) as ApiErrorResponse
  } catch {
    body = null
  }
  return new BidRequestError(response.status, body)
}

export const bidService = {
  async placeBid(request: PlaceBidRequest): Promise<PlaceBidResponse> {
    const response = await apiFetch('/api/v1/bids', {
      method: 'POST',
      body: JSON.stringify(request),
    })
    if (!response.ok) throw await toBidRequestError(response)
    const body: ApiResponse<PlaceBidResponse> = await response.json()
    return body.data
  },

  async getAuctionBids(
    auctionId: string,
    { page = 0, size = BID_PAGE_SIZE }: { page?: number; size?: number } = {},
  ): Promise<BidPage> {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    const response = await apiFetch(`/api/v1/bids/auction/${auctionId}?${query}`, { cache: 'no-store' })
    if (!response.ok) throw await toBidRequestError(response)
    const body: ApiResponse<PageResponse<BidHistoryResponse>> = await response.json()
    return {
      items:   body.data.data.map(mapBidHistoryResponse),
      page:    body.data.pagination.page,
      hasNext: body.data.pagination.hasNext,
      total:   body.data.pagination.total,
    }
  },
}
```

- [ ] **Step 10: Run to verify it passes**

Run: `npm test -- services/bid.service.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 11: Write the failing error-classification test** — `lib/bid-errors.test.ts`

```ts
import { describe, expect, it } from 'vitest'
import { classifyBidError, describeBidFailure } from '@/lib/bid-errors'
import { BidRequestError } from '@/services/bid.service'

const err = (status: number, errorCode?: string, errors?: Record<string, string>, message = 'server message') =>
  new BidRequestError(status, { status, errorCode, errors, message })

describe('classifyBidError', () => {
  it('insufficient balance carries required and available amounts', () => {
    expect(classifyBidError(err(403, 'BID_INSUFFICIENT_BALANCE', { required: '100.00', availableBalance: '20.50' })))
      .toEqual({ kind: 'insufficient-balance', required: 100, available: 20.5 })
  })

  it('too low carries the server minimum', () => {
    expect(classifyBidError(err(400, 'BID_TOO_LOW', { minimumBid: '160' })))
      .toEqual({ kind: 'too-low', minimumBid: 160 })
  })

  it('maps auction state problems', () => {
    expect(classifyBidError(err(409, 'AUCTION_NOT_OPEN'))).toEqual({ kind: 'not-open' })
    expect(classifyBidError(err(404, 'AUCTION_NOT_FOUND'))).toEqual({ kind: 'not-open' })
    expect(classifyBidError(err(403, 'BID_OWN_AUCTION'))).toEqual({ kind: 'own-auction' })
  })

  it('maps wallet problems with the server message', () => {
    expect(classifyBidError(err(403, 'WALLET_NOT_ACTIVE', undefined, 'Your wallet is not active')))
      .toEqual({ kind: 'wallet-problem', message: 'Your wallet is not active' })
    expect(classifyBidError(err(403, 'WALLET_NOT_FOUND')).kind).toBe('wallet-problem')
    expect(classifyBidError(err(409, 'DEPOSIT_LOCK_CLOSED')).kind).toBe('wallet-problem')
  })

  it('maps 503 and network failures to unavailable', () => {
    expect(classifyBidError(err(503, 'SERVICE_UNAVAILABLE'))).toEqual({ kind: 'unavailable' })
    expect(classifyBidError(err(503))).toEqual({ kind: 'unavailable' })
    expect(classifyBidError(new TypeError('Failed to fetch'))).toEqual({ kind: 'unavailable' })
  })

  it('maps 401 and the api client session_expired error to unauthenticated', () => {
    expect(classifyBidError(err(401))).toEqual({ kind: 'unauthenticated' })
    expect(classifyBidError(new Error('session_expired'))).toEqual({ kind: 'unauthenticated' })
  })

  it('maps validation errors to the first field message', () => {
    expect(classifyBidError(err(400, 'INVALID_INPUT', { amount: 'amount must be positive' })))
      .toEqual({ kind: 'invalid', message: 'amount must be positive' })
  })

  it('falls back to unknown', () => {
    expect(classifyBidError(err(500, 'UNEXPECTED_ERROR'))).toEqual({ kind: 'unknown' })
    expect(classifyBidError('weird')).toEqual({ kind: 'unknown' })
  })

  it('treats an unparseable amount as null', () => {
    expect(classifyBidError(err(400, 'BID_TOO_LOW', { minimumBid: 'x' }))).toEqual({ kind: 'too-low', minimumBid: null })
  })
})

describe('describeBidFailure', () => {
  it('offers a wallet top-up for insufficient balance', () => {
    const view = describeBidFailure({ kind: 'insufficient-balance', required: 100, available: 20.5 })
    expect(view.message).toBe('You need $100 available to lock this auction’s deposit (you have $20.50).')
    expect(view.action).toEqual({ href: '/wallet', label: 'Top up wallet' })
    expect(view.disablesForm).toBe(false)
  })

  it('disables the form when the auction is not open', () => {
    expect(describeBidFailure({ kind: 'not-open' }).disablesForm).toBe(true)
    expect(describeBidFailure({ kind: 'own-auction' }).disablesForm).toBe(true)
  })

  it('uses the retry copy for unavailable', () => {
    expect(describeBidFailure({ kind: 'unavailable' }).message).toBe('Bidding is temporarily unavailable. Please try again.')
  })

  it('describes too-low with the minimum', () => {
    expect(describeBidFailure({ kind: 'too-low', minimumBid: 160 }).message).toBe('Someone bid first — the minimum is now $160.')
  })
})
```

- [ ] **Step 12: Run to verify it fails**

Run: `npm test -- lib/bid-errors.test.ts`
Expected: FAIL — cannot resolve `@/lib/bid-errors`.

- [ ] **Step 13: Implement `lib/bid-errors.ts`**

```ts
import { BidRequestError } from '@/services/bid.service'
import { formatCurrency } from '@/lib/format'

export type BidFailure =
  | { kind: 'unauthenticated' }
  | { kind: 'insufficient-balance'; required: number | null; available: number | null }
  | { kind: 'too-low'; minimumBid: number | null }
  | { kind: 'not-open' }
  | { kind: 'own-auction' }
  | { kind: 'wallet-problem'; message: string }
  | { kind: 'unavailable' }
  | { kind: 'invalid'; message: string }
  | { kind: 'unknown' }

export interface BidFailureView {
  message:      string
  action?:      { href: string; label: string }
  disablesForm: boolean
}

function parseAmount(value: string | undefined): number | null {
  if (value === undefined) return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

export function classifyBidError(err: unknown): BidFailure {
  if (err instanceof BidRequestError) {
    const code = err.body?.errorCode
    const errors = err.body?.errors ?? {}
    if (err.status === 401) return { kind: 'unauthenticated' }
    switch (code) {
      case 'BID_INSUFFICIENT_BALANCE':
        return {
          kind: 'insufficient-balance',
          required: parseAmount(errors.required),
          available: parseAmount(errors.availableBalance),
        }
      case 'BID_TOO_LOW':
        return { kind: 'too-low', minimumBid: parseAmount(errors.minimumBid) }
      case 'AUCTION_NOT_OPEN':
      case 'AUCTION_NOT_FOUND':
        return { kind: 'not-open' }
      case 'BID_OWN_AUCTION':
        return { kind: 'own-auction' }
      case 'WALLET_NOT_ACTIVE':
      case 'WALLET_NOT_FOUND':
      case 'DEPOSIT_LOCK_CLOSED':
        return { kind: 'wallet-problem', message: err.message }
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

export function describeBidFailure(failure: BidFailure): BidFailureView {
  switch (failure.kind) {
    case 'insufficient-balance': {
      const needs = failure.required !== null ? formatCurrency(failure.required) : 'the deposit amount'
      const has = failure.available !== null ? ` (you have ${formatCurrency(failure.available)})` : ''
      return {
        message: `You need ${needs} available to lock this auction’s deposit${has}.`,
        action: { href: '/wallet', label: 'Top up wallet' },
        disablesForm: false,
      }
    }
    case 'too-low':
      return {
        message: failure.minimumBid !== null
          ? `Someone bid first — the minimum is now ${formatCurrency(failure.minimumBid)}.`
          : 'Your bid is below the current minimum.',
        disablesForm: false,
      }
    case 'not-open':
      return { message: 'This auction is not open for bidding.', disablesForm: true }
    case 'own-auction':
      return { message: 'You can’t bid on your own auction.', disablesForm: true }
    case 'wallet-problem':
      return { message: failure.message, action: { href: '/wallet', label: 'Go to wallet' }, disablesForm: false }
    case 'unavailable':
      return { message: 'Bidding is temporarily unavailable. Please try again.', disablesForm: false }
    case 'invalid':
      return { message: failure.message, disablesForm: false }
    case 'unauthenticated':
      return { message: 'Please log in to bid.', action: { href: '/login', label: 'Log in' }, disablesForm: false }
    case 'unknown':
      return { message: 'Could not place your bid. Please try again.', disablesForm: false }
  }
}
```

- [ ] **Step 14: Run to verify it passes**

Run: `npm test -- lib/bid-errors.test.ts`
Expected: PASS (13 tests). If the `’` characters differ, keep the test and implementation strings identical (both use U+2019).

- [ ] **Step 15: Remove the mock bid history**

In `services/auction.service.ts`: delete the `import { MOCK_BIDS } from '@/lib/mock-data'` line, the `const delay = …` line, the whole `getBidHistory` method, and remove `BidHistoryItem` from the `@/types/ui/auction.ui` type import (keep `AuctionDetail`).

Then run `grep -rn "mock-data" app components services hooks lib store types`. If nothing imports `@/lib/mock-data` any more, delete `lib/mock-data.ts`; otherwise delete only the `MOCK_BIDS` export and its now-unused imports.

- [ ] **Step 16: Keep the detail page compiling (temporary)**

In `app/auctions/[id]/page.tsx`:
- add imports `import { bidService, EMPTY_BID_PAGE } from '@/services/bid.service'` and `import { toBidHistoryItem } from '@/types/mappers/bid.mapper'`;
- replace `auctionService.getBidHistory(id),` with `bidService.getAuctionBids(id).catch(() => EMPTY_BID_PAGE),` and rename the destructured `bids` to `firstBidPage`;
- replace `<BidHistory items={bids} />` with
  `<BidHistory items={firstBidPage.items.map((b) => toBidHistoryItem(b, { currentUserId: null, currentPrice: auction.currentBid }))} />`.

(Task 5 replaces this with the live component.)

- [ ] **Step 17: Verify**

Run: `npm test && npx tsc --noEmit && npm run lint`
Expected: all tests pass; no type or lint errors.

---

### Task 3: Live auction state — realtime contract and monotonic reducers

**Files:**
- Create: `frontend/types/api/realtime.api.ts`
- Create: `frontend/lib/realtime/auction-live.ts`, `frontend/lib/realtime/auction-live.test.ts`

**Interfaces:**
- Consumes: `BidEntry` (`@/types/ui/auction.ui`), `AuctionDetail`, `PlaceBidResponse` (`@/types/api/bid.api`), `UNKNOWN_BIDDER` (`@/types/mappers/bid.mapper`), `toCents` (`@/lib/money`), `getAuctionStatus` (`@/lib/auction-utils`), `AuctionStatus` (`@/lib/design-tokens`).
- Produces (all from `@/lib/realtime/auction-live`):
  - `interface LiveAuctionState { auctionId: string; currentBid: number; totalBids: number; endsAt: Date; status: AuctionStatus; currentWinnerId?: string; winnerId?: string; extensionCount: number; bids: BidEntry[]; isOutbid: boolean }`
  - `seedLiveState(auction: AuctionDetail, bids: BidEntry[]): LiveAuctionState`
  - `mergeSnapshot(prev: LiveAuctionState, next: LiveAuctionState): LiveAuctionState`
  - `upsertBids(existing: BidEntry[], incoming: BidEntry[]): BidEntry[]`
  - `applyBidPlaced(s: LiveAuctionState, p: BidPlacedPayload, meId: string | null): LiveAuctionState`
  - `applyOwnBid(s: LiveAuctionState, r: PlaceBidResponse, me: { id: string; name: string }): LiveAuctionState`
  - `applyAuctionExtended(s, p: AuctionExtendedPayload)`, `applyAuctionEnded(s, p: AuctionEndedPayload)`, `applyAuctionCancelled(s)`, `applyOutbid(s)`, `appendOlderBids(s, older: BidEntry[])` — all `→ LiveAuctionState`
  - `deriveDisplayAuction(base: AuctionDetail, live: LiveAuctionState | null, userId: string | null): { auction: AuctionDetail; isOutbid: boolean }`
- Produces (from `@/types/api/realtime.api`): `BidPlacedPayload`, `AuctionExtendedPayload`, `AuctionEndedPayload`, `AuctionCancelledPayload`, `OutbidPayload`, `AuctionRealtimeMessage`, `REALTIME_MESSAGE_TYPES`.

- [ ] **Step 1: Create `types/api/realtime.api.ts`** (mirrors media-service `RealtimePayloads`, spec §6)

```ts
/** STOMP messages pushed by media-service. Envelope `{ type, auctionId, payload }`; amounts in dollars; ISO timestamps. */
export interface BidPlacedPayload {
  bidId:                string
  bidderId:             string
  bidderName:           string | null
  amount:               number
  placedAt:             string | null
  totalBids:            number | null
  endTime:              string | null
  antiSnipingTriggered: boolean
}

export interface AuctionExtendedPayload {
  previousEndTime: string | null
  newEndTime:      string
  extensionCount:  number | null
}

export interface AuctionEndedPayload {
  winnerId:   string | null
  finalPrice: number | null
  endedAt:    string | null
}

export interface AuctionCancelledPayload {
  reason: string | null
}

export interface OutbidPayload {
  auctionTitle:  string | null
  currentPrice:  number
  newLeaderName: string | null
}

export type AuctionRealtimeMessage =
  | { type: 'BID_PLACED';        auctionId: string; payload: BidPlacedPayload }
  | { type: 'AUCTION_EXTENDED';  auctionId: string; payload: AuctionExtendedPayload }
  | { type: 'AUCTION_ENDED';     auctionId: string; payload: AuctionEndedPayload }
  | { type: 'AUCTION_CANCELLED'; auctionId: string; payload: AuctionCancelledPayload }
  | { type: 'OUTBID';            auctionId: string; payload: OutbidPayload }

export const REALTIME_MESSAGE_TYPES: ReadonlyArray<AuctionRealtimeMessage['type']> = [
  'BID_PLACED', 'AUCTION_EXTENDED', 'AUCTION_ENDED', 'AUCTION_CANCELLED', 'OUTBID',
]
```

- [ ] **Step 2: Write the failing reducer tests** — `lib/realtime/auction-live.test.ts`

```ts
import { describe, expect, it } from 'vitest'
import { AuctionStatus } from '@/lib/design-tokens'
import {
  appendOlderBids, applyAuctionCancelled, applyAuctionEnded, applyAuctionExtended, applyBidPlaced,
  applyOutbid, applyOwnBid, deriveDisplayAuction, mergeSnapshot, seedLiveState, upsertBids,
  type LiveAuctionState,
} from '@/lib/realtime/auction-live'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'
import type { BidPlacedPayload } from '@/types/api/realtime.api'

const FAR = new Date('2099-01-01T12:00:00Z')

const auction: AuctionDetail = {
  id: 'a1', title: 'Omega', description: '', categoryId: 'c', categoryName: 'Watches',
  startingPrice: 100, bidIncrement: 10, depositAmount: 20, currentBid: 150, currentWinnerId: 'alice',
  totalBids: 2, status: AuctionStatus.Active, startsAt: new Date('2026-01-01T00:00:00Z'), endsAt: FAR,
  originalEndAt: FAR, extensionCount: 0, images: [], seller: null, createdAt: new Date('2026-01-01T00:00:00Z'),
}

const bid = (id: string, amount: number, iso: string, bidderId = 'x'): BidEntry => ({
  id, auctionId: 'a1', bidderId, bidderName: bidderId, amount, placedAt: new Date(iso), isAutoBid: false,
})

const seeded = (): LiveAuctionState =>
  seedLiveState(auction, [bid('b2', 150, '2026-10-01T12:01:00Z', 'alice'), bid('b1', 140, '2026-10-01T12:00:00Z', 'bob')])

const placed = (over: Partial<BidPlacedPayload> = {}): BidPlacedPayload => ({
  bidId: 'b3', bidderId: 'bob', bidderName: 'Bob', amount: 160, placedAt: '2026-10-01T12:02:00Z',
  totalBids: 3, endTime: FAR.toISOString(), antiSnipingTriggered: false, ...over,
})

describe('seedLiveState', () => {
  it('copies the snapshot', () => {
    const s = seeded()
    expect(s).toMatchObject({ auctionId: 'a1', currentBid: 150, totalBids: 2, currentWinnerId: 'alice', isOutbid: false })
    expect(s.bids.map((b) => b.id)).toEqual(['b2', 'b1'])
  })
})

describe('upsertBids', () => {
  it('dedupes by id (incoming wins) and sorts newest first, amount breaking ties', () => {
    const merged = upsertBids(
      [bid('b1', 100, '2026-10-01T12:00:00Z'), bid('b2', 110, '2026-10-01T12:01:00Z')],
      [{ ...bid('b2', 110, '2026-10-01T12:01:00Z'), bidderName: 'Real Name' }, bid('b3', 120, '2026-10-01T12:01:00Z')],
    )
    expect(merged.map((b) => b.id)).toEqual(['b3', 'b2', 'b1'])
    expect(merged.find((b) => b.id === 'b2')?.bidderName).toBe('Real Name')
  })
})

describe('applyBidPlaced', () => {
  it('raises price, count and leader, and prepends the bid', () => {
    const s = applyBidPlaced(seeded(), placed(), null)
    expect(s.currentBid).toBe(160)
    expect(s.totalBids).toBe(3)
    expect(s.currentWinnerId).toBe('bob')
    expect(s.bids[0]).toMatchObject({ id: 'b3', bidderName: 'Bob', amount: 160 })
  })

  it('is monotonic: a stale, lower message never lowers state', () => {
    const newer = applyBidPlaced(seeded(), placed({ bidId: 'b4', amount: 200, totalBids: 5 }), null)
    const s = applyBidPlaced(newer, placed({ bidId: 'b3', amount: 160, totalBids: 3, bidderId: 'carol' }), null)
    expect(s.currentBid).toBe(200)
    expect(s.totalBids).toBe(5)
    expect(s.currentWinnerId).toBe('bob')
    expect(s.bids.map((b) => b.id)).toContain('b3')
  })

  it('extends endsAt only forward', () => {
    const later = new Date(FAR.getTime() + 300_000).toISOString()
    expect(applyBidPlaced(seeded(), placed({ endTime: later }), null).endsAt.toISOString()).toBe(later)
    const earlier = new Date(FAR.getTime() - 300_000).toISOString()
    expect(applyBidPlaced(seeded(), placed({ endTime: earlier }), null).endsAt).toEqual(FAR)
  })

  it('marks me outbid when I was leading and someone else bids higher', () => {
    expect(applyBidPlaced(seeded(), placed(), 'alice').isOutbid).toBe(true)
  })

  it('clears outbid when my own bid arrives', () => {
    const outbid = applyOutbid(seeded())
    expect(applyBidPlaced(outbid, placed({ bidderId: 'alice' }), 'alice').isOutbid).toBe(false)
  })

  it('is idempotent for a repeated bid', () => {
    const once = applyBidPlaced(seeded(), placed(), null)
    expect(applyBidPlaced(once, placed(), null)).toEqual(once)
  })

  it('falls back for a missing bidder name and placedAt', () => {
    const s = applyBidPlaced(seeded(), placed({ bidderName: null, placedAt: null }), null)
    expect(s.bids.find((b) => b.id === 'b3')?.bidderName).toBe('Unknown bidder')
  })
})

describe('applyOwnBid', () => {
  it('applies the REST response and makes me the leader', () => {
    const s = applyOwnBid(applyOutbid(seeded()), {
      bidId: 'b9', auctionId: 'a1', amount: 170, placedAt: '2026-10-01T12:03:00Z', currentPrice: 170,
      totalBids: 3, endTime: FAR.toISOString(), extended: false,
    }, { id: 'me', name: 'You' })
    expect(s).toMatchObject({ currentBid: 170, totalBids: 3, currentWinnerId: 'me', isOutbid: false })
    expect(s.bids[0]).toMatchObject({ id: 'b9', bidderId: 'me', amount: 170 })
  })
})

describe('lifecycle messages', () => {
  it('extension moves endsAt forward and raises the count', () => {
    const newEnd = new Date(FAR.getTime() + 300_000).toISOString()
    const s = applyAuctionExtended(seeded(), { previousEndTime: FAR.toISOString(), newEndTime: newEnd, extensionCount: 1 })
    expect(s.endsAt.toISOString()).toBe(newEnd)
    expect(s.extensionCount).toBe(1)
  })

  it('end closes with winner and final price', () => {
    const s = applyAuctionEnded(seeded(), { winnerId: 'alice', finalPrice: 150, endedAt: null })
    expect(s).toMatchObject({ status: AuctionStatus.Closed, winnerId: 'alice', currentBid: 150 })
  })

  it('end without bids keeps the price', () => {
    expect(applyAuctionEnded(seeded(), { winnerId: null, finalPrice: null, endedAt: null }).currentBid).toBe(150)
  })

  it('cancel closes without a winner', () => {
    expect(applyAuctionCancelled(seeded())).toMatchObject({ status: AuctionStatus.Closed, winnerId: undefined })
  })

  it('closed is sticky against a late bid', () => {
    const closed = applyAuctionEnded(seeded(), { winnerId: 'alice', finalPrice: 150, endedAt: null })
    expect(applyBidPlaced(closed, placed(), null).status).toBe(AuctionStatus.Closed)
  })
})

describe('mergeSnapshot', () => {
  it('keeps the higher values and the union of bids', () => {
    const live = applyBidPlaced(seeded(), placed({ amount: 200, bidId: 'b5', totalBids: 5 }), null)
    const fresh = seedLiveState(auction, [bid('b2', 150, '2026-10-01T12:01:00Z', 'alice')])
    const s = mergeSnapshot(live, fresh)
    expect(s.currentBid).toBe(200)
    expect(s.totalBids).toBe(5)
    expect(s.currentWinnerId).toBe('bob')
    expect(s.bids.map((b) => b.id)).toEqual(['b5', 'b2', 'b1'])
  })

  it('takes the fresher leader when the snapshot is ahead', () => {
    const fresh = seedLiveState({ ...auction, currentBid: 300, currentWinnerId: 'carol', totalBids: 9 }, [])
    expect(mergeSnapshot(seeded(), fresh)).toMatchObject({ currentBid: 300, currentWinnerId: 'carol', totalBids: 9 })
  })

  it('closed wins', () => {
    const fresh = seedLiveState({ ...auction, status: AuctionStatus.Closed, winnerId: 'alice' }, [])
    expect(mergeSnapshot(seeded(), fresh)).toMatchObject({ status: AuctionStatus.Closed, winnerId: 'alice' })
  })
})

describe('appendOlderBids', () => {
  it('adds older pages without duplicates', () => {
    const s = appendOlderBids(seeded(), [bid('b1', 140, '2026-10-01T12:00:00Z', 'bob'), bid('b0', 120, '2026-10-01T11:00:00Z')])
    expect(s.bids.map((b) => b.id)).toEqual(['b2', 'b1', 'b0'])
  })
})

describe('deriveDisplayAuction', () => {
  it('uses the server snapshot before hydration', () => {
    const { auction: view, isOutbid } = deriveDisplayAuction(auction, null, null)
    expect(view.currentBid).toBe(150)
    expect(view.status).toBe(AuctionStatus.Active)
    expect(isOutbid).toBe(false)
  })

  it('overlays live values', () => {
    const live = applyBidPlaced(seeded(), placed(), null)
    const { auction: view } = deriveDisplayAuction(auction, live, null)
    expect(view).toMatchObject({ currentBid: 160, totalBids: 3, currentWinnerId: 'bob' })
  })

  it('shows Outbid to the outbid viewer while live', () => {
    const live = applyBidPlaced(seeded(), placed(), 'alice')
    const r = deriveDisplayAuction(auction, live, 'alice')
    expect(r.isOutbid).toBe(true)
    expect(r.auction.status).toBe(AuctionStatus.Outbid)
  })

  it('shows Won to the winner and Closed to others after the end', () => {
    const ended = applyAuctionEnded(seeded(), { winnerId: 'alice', finalPrice: 150, endedAt: null })
    expect(deriveDisplayAuction(auction, ended, 'alice').auction.status).toBe(AuctionStatus.Won)
    expect(deriveDisplayAuction(auction, ended, 'bob').auction.status).toBe(AuctionStatus.Closed)
  })

  it('ignores live state of another auction', () => {
    const other = { ...seeded(), auctionId: 'zzz', currentBid: 999 }
    expect(deriveDisplayAuction(auction, other, null).auction.currentBid).toBe(150)
  })
})
```

- [ ] **Step 3: Run to verify it fails**

Run: `npm test -- lib/realtime/auction-live.test.ts`
Expected: FAIL — cannot resolve `@/lib/realtime/auction-live`.

- [ ] **Step 4: Implement `lib/realtime/auction-live.ts`**

```ts
import { AuctionStatus } from '@/lib/design-tokens'
import { getAuctionStatus } from '@/lib/auction-utils'
import { toCents } from '@/lib/money'
import { UNKNOWN_BIDDER } from '@/types/mappers/bid.mapper'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'
import type { PlaceBidResponse } from '@/types/api/bid.api'
import type {
  AuctionEndedPayload,
  AuctionExtendedPayload,
  BidPlacedPayload,
} from '@/types/api/realtime.api'

/**
 * Live view of one auction. Topics are not mutually ordered and REST snapshots can race
 * STOMP pushes, so every reducer is monotonic: price, bid count, end time and extension
 * count only grow, Closed is terminal, and bids are deduplicated by id.
 */
export interface LiveAuctionState {
  auctionId:        string
  currentBid:       number
  totalBids:        number
  endsAt:           Date
  status:           AuctionStatus
  currentWinnerId?: string
  winnerId?:        string
  extensionCount:   number
  bids:             BidEntry[]
  isOutbid:         boolean
}

function later(a: Date, b: Date): Date {
  return b.getTime() > a.getTime() ? b : a
}

function maxDollars(a: number, b: number): number {
  return toCents(b) > toCents(a) ? b : a
}

function mergeStatus(prev: AuctionStatus, next: AuctionStatus): AuctionStatus {
  return prev === AuctionStatus.Closed || next === AuctionStatus.Closed ? AuctionStatus.Closed : next
}

export function upsertBids(existing: BidEntry[], incoming: BidEntry[]): BidEntry[] {
  const byId = new Map<string, BidEntry>()
  for (const b of existing) byId.set(b.id, b)
  for (const b of incoming) byId.set(b.id, b)
  return [...byId.values()].sort(
    (x, y) => y.placedAt.getTime() - x.placedAt.getTime() || toCents(y.amount) - toCents(x.amount),
  )
}

export function seedLiveState(auction: AuctionDetail, bids: BidEntry[]): LiveAuctionState {
  return {
    auctionId:       auction.id,
    currentBid:      auction.currentBid,
    totalBids:       auction.totalBids,
    endsAt:          auction.endsAt,
    status:          auction.status,
    currentWinnerId: auction.currentWinnerId,
    winnerId:        auction.winnerId,
    extensionCount:  auction.extensionCount,
    bids:            upsertBids([], bids),
    isOutbid:        false,
  }
}

export function mergeSnapshot(prev: LiveAuctionState, next: LiveAuctionState): LiveAuctionState {
  const nextLeads = toCents(next.currentBid) >= toCents(prev.currentBid)
  return {
    ...prev,
    currentBid:      maxDollars(prev.currentBid, next.currentBid),
    totalBids:       Math.max(prev.totalBids, next.totalBids),
    endsAt:          later(prev.endsAt, next.endsAt),
    status:          mergeStatus(prev.status, next.status),
    currentWinnerId: nextLeads ? next.currentWinnerId ?? prev.currentWinnerId : prev.currentWinnerId,
    winnerId:        next.winnerId ?? prev.winnerId,
    extensionCount:  Math.max(prev.extensionCount, next.extensionCount),
    bids:            upsertBids(prev.bids, next.bids),
  }
}

export function applyBidPlaced(s: LiveAuctionState, p: BidPlacedPayload, meId: string | null): LiveAuctionState {
  const raisesPrice = toCents(p.amount) > toCents(s.currentBid)
  const leads = toCents(p.amount) >= toCents(s.currentBid)
  let isOutbid = s.isOutbid
  if (meId !== null) {
    if (p.bidderId === meId) isOutbid = false
    else if (raisesPrice && s.currentWinnerId === meId) isOutbid = true
  }
  const entry: BidEntry = {
    id:         p.bidId,
    auctionId:  s.auctionId,
    bidderId:   p.bidderId,
    bidderName: p.bidderName ?? UNKNOWN_BIDDER,
    amount:     p.amount,
    placedAt:   p.placedAt ? new Date(p.placedAt) : new Date(),
    isAutoBid:  false,
  }
  const existing = s.bids.find((b) => b.id === p.bidId)
  return {
    ...s,
    currentBid:      maxDollars(s.currentBid, p.amount),
    totalBids:       Math.max(s.totalBids, p.totalBids ?? 0),
    endsAt:          p.endTime ? later(s.endsAt, new Date(p.endTime)) : s.endsAt,
    currentWinnerId: leads ? p.bidderId : s.currentWinnerId,
    // A repeat of a known bid keeps its original timestamp so replays are idempotent.
    bids:            upsertBids(s.bids, [existing ? { ...entry, placedAt: existing.placedAt } : entry]),
    isOutbid,
  }
}

export function applyOwnBid(
  s: LiveAuctionState,
  r: PlaceBidResponse,
  me: { id: string; name: string },
): LiveAuctionState {
  const leads = toCents(r.currentPrice) >= toCents(s.currentBid)
  return {
    ...s,
    currentBid:      maxDollars(s.currentBid, r.currentPrice),
    totalBids:       Math.max(s.totalBids, r.totalBids),
    endsAt:          later(s.endsAt, new Date(r.endTime)),
    currentWinnerId: leads ? me.id : s.currentWinnerId,
    isOutbid:        false,
    bids: upsertBids(s.bids, [{
      id: r.bidId, auctionId: s.auctionId, bidderId: me.id, bidderName: me.name,
      amount: r.amount, placedAt: new Date(r.placedAt), isAutoBid: false,
    }]),
  }
}

export function applyAuctionExtended(s: LiveAuctionState, p: AuctionExtendedPayload): LiveAuctionState {
  return {
    ...s,
    endsAt:         later(s.endsAt, new Date(p.newEndTime)),
    extensionCount: Math.max(s.extensionCount, p.extensionCount ?? 0),
  }
}

export function applyAuctionEnded(s: LiveAuctionState, p: AuctionEndedPayload): LiveAuctionState {
  return {
    ...s,
    status:     AuctionStatus.Closed,
    winnerId:   p.winnerId ?? undefined,
    currentBid: p.finalPrice !== null ? maxDollars(s.currentBid, p.finalPrice) : s.currentBid,
  }
}

export function applyAuctionCancelled(s: LiveAuctionState): LiveAuctionState {
  return { ...s, status: AuctionStatus.Closed, winnerId: undefined }
}

export function applyOutbid(s: LiveAuctionState): LiveAuctionState {
  return { ...s, isOutbid: true }
}

export function appendOlderBids(s: LiveAuctionState, older: BidEntry[]): LiveAuctionState {
  return { ...s, bids: upsertBids(s.bids, older) }
}

/** Merges live state over the server snapshot and derives the viewer-specific status. */
export function deriveDisplayAuction(
  base: AuctionDetail,
  live: LiveAuctionState | null,
  userId: string | null,
): { auction: AuctionDetail; isOutbid: boolean } {
  const current = live && live.auctionId === base.id ? live : null
  const merged: AuctionDetail = current
    ? {
        ...base,
        currentBid:      current.currentBid,
        totalBids:       current.totalBids,
        endsAt:          current.endsAt,
        status:          current.status,
        currentWinnerId: current.currentWinnerId,
        winnerId:        current.winnerId,
        extensionCount:  current.extensionCount,
      }
    : base

  if (merged.status === AuctionStatus.Closed) {
    const won = userId !== null && merged.winnerId === userId
    return { auction: { ...merged, status: won ? AuctionStatus.Won : AuctionStatus.Closed }, isOutbid: false }
  }

  const isOutbid = !!current?.isOutbid && userId !== null && merged.currentWinnerId !== userId
  const status = isOutbid ? AuctionStatus.Outbid : getAuctionStatus(merged)
  return { auction: { ...merged, status }, isOutbid }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `npm test -- lib/realtime/auction-live.test.ts`
Expected: PASS (all tests in the file).

- [ ] **Step 6: Verify**

Run: `npm test && npx tsc --noEmit && npm run lint`
Expected: all green.

---

### Task 4: Store and STOMP connection

**Files:**
- Modify: `frontend/package.json` (dependencies)
- Create: `frontend/lib/realtime/ws-url.ts`, `frontend/lib/realtime/ws-url.test.ts`
- Create: `frontend/lib/realtime/dispatch.ts`, `frontend/lib/realtime/dispatch.test.ts`
- Modify: `frontend/lib/apiClient.ts`; Create: `frontend/lib/apiClient.test.ts`
- Rewrite: `frontend/store/auctionStore.ts`; Create: `frontend/store/auctionStore.test.ts`
- Rewrite: `frontend/hooks/useAuctionSocket.ts`
- Create: `frontend/.env.example`; Modify: `frontend/.gitignore`

**Interfaces:**
- Consumes: everything produced by Task 3; `PlaceBidResponse`; `useAuthStore`; `authService.refresh`.
- Produces:
  - `resolveWsEndpoint(env: { wsUrl?: string; apiUrl?: string }): string`, `withAccessToken(endpoint: string, token: string | null): string` (`@/lib/realtime/ws-url`).
  - `parseRealtimeMessage(body: string): AuctionRealtimeMessage | null`, `interface RealtimeHandlers { bidPlaced(p: BidPlacedPayload): void; extended(p: AuctionExtendedPayload): void; ended(p: AuctionEndedPayload): void; cancelled(p: AuctionCancelledPayload): void; outbid(auctionId: string, p: OutbidPayload): void }`, `dispatchRealtimeMessage(msg: AuctionRealtimeMessage, auctionId: string, h: RealtimeHandlers): boolean` (`@/lib/realtime/dispatch`).
  - `getFreshAccessToken(): Promise<string | null>` (`@/lib/apiClient`).
  - `useAuctionStore` with state `{ live: LiveAuctionState | null }` and actions `hydrate(auction: AuctionDetail, bids: BidEntry[])`, `bidPlaced(p: BidPlacedPayload, meId: string | null)`, `ownBidPlaced(r: PlaceBidResponse, me: { id: string; name: string })`, `extended(p: AuctionExtendedPayload)`, `ended(p: AuctionEndedPayload)`, `cancelled()`, `outbid()`, `appendOlderBids(bids: BidEntry[])`, `reset()`.
  - `useAuctionSocket(auctionId: string, onResync?: () => void): void`.

- [ ] **Step 1: Swap dependencies**

Run: `npm install @stomp/stompjs@^7.3.0 && npm uninstall socket.io-client`
Expected: `package.json` lists `@stomp/stompjs`, no `socket.io-client`.

- [ ] **Step 2: Write the failing ws-url test** — `lib/realtime/ws-url.test.ts`

```ts
import { describe, expect, it } from 'vitest'
import { resolveWsEndpoint, withAccessToken } from '@/lib/realtime/ws-url'

describe('resolveWsEndpoint', () => {
  it('prefers the explicit WS url', () => {
    expect(resolveWsEndpoint({ wsUrl: 'wss://rt.example.com/ws-notifications/websocket', apiUrl: 'http://x' }))
      .toBe('wss://rt.example.com/ws-notifications/websocket')
  })

  it('derives from the API url (http→ws, https→wss)', () => {
    expect(resolveWsEndpoint({ apiUrl: 'http://localhost:8080' })).toBe('ws://localhost:8080/ws-notifications/websocket')
    expect(resolveWsEndpoint({ apiUrl: 'https://api.example.com/' })).toBe('wss://api.example.com/ws-notifications/websocket')
  })

  it('defaults to the local gateway', () => {
    expect(resolveWsEndpoint({})).toBe('ws://localhost:8080/ws-notifications/websocket')
  })
})

describe('withAccessToken', () => {
  it('adds the token as access_token, encoded', () => {
    expect(withAccessToken('ws://h/ws-notifications/websocket', 'a.b+c'))
      .toBe('ws://h/ws-notifications/websocket?access_token=a.b%2Bc')
  })

  it('leaves the endpoint alone for anonymous viewers', () => {
    expect(withAccessToken('ws://h/ws-notifications/websocket', null)).toBe('ws://h/ws-notifications/websocket')
  })
})
```

- [ ] **Step 3: Run to verify it fails**

Run: `npm test -- lib/realtime/ws-url.test.ts`
Expected: FAIL — cannot resolve module.

- [ ] **Step 4: Implement `lib/realtime/ws-url.ts`**

```ts
const WS_PATH = '/ws-notifications/websocket' // raw-WebSocket transport of the SockJS endpoint
const DEFAULT_API_URL = 'http://localhost:8080'

export function resolveWsEndpoint(env: { wsUrl?: string; apiUrl?: string }): string {
  if (env.wsUrl) return env.wsUrl
  const api = (env.apiUrl || DEFAULT_API_URL).replace(/\/+$/, '')
  return api.replace(/^http/, 'ws') + WS_PATH
}

/** The gateway reads the JWT from `access_token` on WebSocket handshakes (browsers cannot set headers). */
export function withAccessToken(endpoint: string, token: string | null): string {
  if (!token) return endpoint
  const url = new URL(endpoint)
  url.searchParams.set('access_token', token)
  return url.toString()
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `npm test -- lib/realtime/ws-url.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 6: Write the failing dispatch test** — `lib/realtime/dispatch.test.ts`

```ts
import { describe, expect, it, vi } from 'vitest'
import { dispatchRealtimeMessage, parseRealtimeMessage, type RealtimeHandlers } from '@/lib/realtime/dispatch'

const handlers = (): RealtimeHandlers => ({
  bidPlaced: vi.fn(), extended: vi.fn(), ended: vi.fn(), cancelled: vi.fn(), outbid: vi.fn(),
})

const msg = (type: string, auctionId = 'a1', payload: object = {}) => JSON.stringify({ type, auctionId, payload })

describe('parseRealtimeMessage', () => {
  it('accepts a well-formed envelope', () => {
    expect(parseRealtimeMessage(msg('BID_PLACED'))?.type).toBe('BID_PLACED')
  })

  it('rejects junk, unknown types and missing fields', () => {
    expect(parseRealtimeMessage('not json')).toBeNull()
    expect(parseRealtimeMessage(msg('SOMETHING_ELSE'))).toBeNull()
    expect(parseRealtimeMessage(JSON.stringify({ type: 'BID_PLACED', payload: {} }))).toBeNull()
    expect(parseRealtimeMessage(JSON.stringify({ type: 'BID_PLACED', auctionId: 'a1' }))).toBeNull()
    expect(parseRealtimeMessage('null')).toBeNull()
  })
})

describe('dispatchRealtimeMessage', () => {
  it('routes each topic type for the open auction', () => {
    const h = handlers()
    for (const type of ['BID_PLACED', 'AUCTION_EXTENDED', 'AUCTION_ENDED', 'AUCTION_CANCELLED']) {
      expect(dispatchRealtimeMessage(parseRealtimeMessage(msg(type))!, 'a1', h)).toBe(true)
    }
    expect(h.bidPlaced).toHaveBeenCalledOnce()
    expect(h.extended).toHaveBeenCalledOnce()
    expect(h.ended).toHaveBeenCalledOnce()
    expect(h.cancelled).toHaveBeenCalledOnce()
  })

  it('ignores topic messages for another auction', () => {
    const h = handlers()
    expect(dispatchRealtimeMessage(parseRealtimeMessage(msg('BID_PLACED', 'other'))!, 'a1', h)).toBe(false)
    expect(h.bidPlaced).not.toHaveBeenCalled()
  })

  it('routes OUTBID for any auction with its id', () => {
    const h = handlers()
    const payload = { auctionTitle: 'Omega', currentPrice: 200, newLeaderName: 'Bob' }
    expect(dispatchRealtimeMessage(parseRealtimeMessage(msg('OUTBID', 'other', payload))!, 'a1', h)).toBe(true)
    expect(h.outbid).toHaveBeenCalledWith('other', payload)
  })
})
```

- [ ] **Step 7: Run to verify it fails**

Run: `npm test -- lib/realtime/dispatch.test.ts`
Expected: FAIL — cannot resolve module.

- [ ] **Step 8: Implement `lib/realtime/dispatch.ts`**

```ts
import {
  REALTIME_MESSAGE_TYPES,
  type AuctionCancelledPayload,
  type AuctionEndedPayload,
  type AuctionExtendedPayload,
  type AuctionRealtimeMessage,
  type BidPlacedPayload,
  type OutbidPayload,
} from '@/types/api/realtime.api'

export interface RealtimeHandlers {
  bidPlaced(p: BidPlacedPayload): void
  extended(p: AuctionExtendedPayload): void
  ended(p: AuctionEndedPayload): void
  cancelled(p: AuctionCancelledPayload): void
  outbid(auctionId: string, p: OutbidPayload): void
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/** Validates the envelope only; payload fields are trusted per the media-service contract (spec §6). */
export function parseRealtimeMessage(body: string): AuctionRealtimeMessage | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(body)
  } catch {
    return null
  }
  if (!isRecord(parsed)) return null
  const { type, auctionId, payload } = parsed
  if (typeof type !== 'string' || typeof auctionId !== 'string' || !isRecord(payload)) return null
  if (!(REALTIME_MESSAGE_TYPES as ReadonlyArray<string>).includes(type)) return null
  return parsed as unknown as AuctionRealtimeMessage
}

/** Returns true when a handler ran. OUTBID arrives on the user queue for any auction. */
export function dispatchRealtimeMessage(
  msg: AuctionRealtimeMessage,
  auctionId: string,
  h: RealtimeHandlers,
): boolean {
  if (msg.type === 'OUTBID') {
    h.outbid(msg.auctionId, msg.payload)
    return true
  }
  if (msg.auctionId !== auctionId) return false
  switch (msg.type) {
    case 'BID_PLACED':        h.bidPlaced(msg.payload); return true
    case 'AUCTION_EXTENDED':  h.extended(msg.payload);  return true
    case 'AUCTION_ENDED':     h.ended(msg.payload);     return true
    case 'AUCTION_CANCELLED': h.cancelled(msg.payload); return true
  }
}
```

- [ ] **Step 9: Run to verify it passes**

Run: `npm test -- lib/realtime/dispatch.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 10: Write the failing `getFreshAccessToken` test** — `lib/apiClient.test.ts`

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/auth.service', () => ({ authService: { refresh: vi.fn() } }))

import { authService } from '@/services/auth.service'
import { useAuthStore } from '@/store/authStore'
import { getFreshAccessToken } from '@/lib/apiClient'

const refresh = vi.mocked(authService.refresh)

function signIn(expiresInMs: number) {
  useAuthStore.setState({
    accessToken: 'old', refreshToken: 'r1', isAuthenticated: true,
    accessTokenExpiresAt: Date.now() + expiresInMs, user: null,
  })
}

beforeEach(() => {
  vi.stubGlobal('window', { location: { href: '' } })
  refresh.mockReset()
})

afterEach(() => vi.unstubAllGlobals())

describe('getFreshAccessToken', () => {
  it('returns the current token when not near expiry', async () => {
    signIn(10 * 60_000)
    await expect(getFreshAccessToken()).resolves.toBe('old')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('refreshes a token that is about to expire', async () => {
    signIn(10_000)
    refresh.mockResolvedValue({ data: { accessToken: 'new', refreshToken: 'r2', expiresIn: 900 } } as Awaited<ReturnType<typeof authService.refresh>>)
    await expect(getFreshAccessToken()).resolves.toBe('new')
    expect(refresh).toHaveBeenCalledWith('r1')
  })

  it('returns null and logs out when refresh fails', async () => {
    signIn(10_000)
    refresh.mockRejectedValue(new Error('nope'))
    await expect(getFreshAccessToken()).resolves.toBeNull()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('returns null for anonymous users', async () => {
    useAuthStore.getState().logout()
    await expect(getFreshAccessToken()).resolves.toBeNull()
  })
})
```

- [ ] **Step 11: Run to verify it fails**

Run: `npm test -- lib/apiClient.test.ts`
Expected: FAIL — `getFreshAccessToken` is not exported.

- [ ] **Step 12: Refactor `lib/apiClient.ts`**

Move the body of the block-scoped "Proactive refresh" section in `apiFetch` into a new function and call it, then add `getFreshAccessToken`:

```ts
/** Refreshes the access token if it is expired or within the refresh buffer. Throws `session_expired` after logging out. */
async function refreshIfNearExpiry(): Promise<void> {
  const { accessToken, accessTokenExpiresAt, refreshToken } = useAuthStore.getState()
  if (accessToken && accessTokenExpiresAt !== null && Date.now() >= accessTokenExpiresAt - TOKEN_REFRESH_BUFFER_MS) {
    if (!refreshToken) {
      forceLogout()
      throw new Error("session_expired")
    }
    if (!refreshPromise) {
      refreshPromise = authService
        .refresh(refreshToken)
        .then(({ data }) => {
          useAuthStore.getState().setTokens(data.accessToken, data.refreshToken, data.expiresIn)
        })
        .catch(() => {
          forceLogout()
          throw new Error("session_expired")
        })
        .finally(() => { refreshPromise = null })
    }
    await refreshPromise
  }
}

/** A usable access token for non-fetch transports (the STOMP handshake), or null when anonymous or logged out. */
export async function getFreshAccessToken(): Promise<string | null> {
  if (typeof window === "undefined") return null
  try {
    await refreshIfNearExpiry()
  } catch {
    return null
  }
  return useAuthStore.getState().accessToken
}
```

In `apiFetch`, replace the whole `{ … }` proactive block (including its comment) with:

```ts
  // Proactive refresh: if token is expired or within 60 s of expiry, refresh before sending.
  await refreshIfNearExpiry();
```

Leave the reactive 401 path unchanged.

- [ ] **Step 13: Run to verify it passes**

Run: `npm test -- lib/apiClient.test.ts`
Expected: PASS (4 tests). (Zustand `persist` may warn that `localStorage` is unavailable in Node — harmless.)

- [ ] **Step 14: Write the failing store test** — `store/auctionStore.test.ts`

```ts
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuctionStore } from '@/store/auctionStore'
import { AuctionStatus } from '@/lib/design-tokens'
import type { AuctionDetail } from '@/types/ui/auction.ui'

const FAR = new Date('2099-01-01T12:00:00Z')
const auction = (over: Partial<AuctionDetail> = {}): AuctionDetail => ({
  id: 'a1', title: 'Omega', description: '', categoryId: 'c', categoryName: 'W',
  startingPrice: 100, bidIncrement: 10, depositAmount: 20, currentBid: 150, totalBids: 2,
  status: AuctionStatus.Active, startsAt: FAR, endsAt: FAR, originalEndAt: FAR, extensionCount: 0,
  images: [], seller: null, createdAt: FAR, ...over,
})

beforeEach(() => useAuctionStore.getState().reset())

describe('auctionStore', () => {
  it('ignores pushes before hydration', () => {
    useAuctionStore.getState().outbid()
    expect(useAuctionStore.getState().live).toBeNull()
  })

  it('hydrates, applies a bid, and merges a later snapshot monotonically', () => {
    const s = useAuctionStore.getState()
    s.hydrate(auction(), [])
    s.bidPlaced({ bidId: 'b3', bidderId: 'bob', bidderName: 'Bob', amount: 200, placedAt: null, totalBids: 3, endTime: null, antiSnipingTriggered: false }, null)
    s.hydrate(auction({ currentBid: 150, totalBids: 2 }), [])
    expect(useAuctionStore.getState().live).toMatchObject({ currentBid: 200, totalBids: 3, currentWinnerId: 'bob' })
  })

  it('replaces state when hydrating a different auction', () => {
    const s = useAuctionStore.getState()
    s.hydrate(auction(), [])
    s.hydrate(auction({ id: 'a2', currentBid: 10 }), [])
    expect(useAuctionStore.getState().live).toMatchObject({ auctionId: 'a2', currentBid: 10 })
  })
})
```

- [ ] **Step 15: Run to verify it fails**

Run: `npm test -- store/auctionStore.test.ts`
Expected: FAIL — `hydrate` is not a function (old store).

- [ ] **Step 16: Rewrite `store/auctionStore.ts`**

```ts
import { create } from 'zustand'
import {
  appendOlderBids, applyAuctionCancelled, applyAuctionEnded, applyAuctionExtended,
  applyBidPlaced, applyOutbid, applyOwnBid, mergeSnapshot, seedLiveState,
  type LiveAuctionState,
} from '@/lib/realtime/auction-live'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'
import type { PlaceBidResponse } from '@/types/api/bid.api'
import type {
  AuctionEndedPayload, AuctionExtendedPayload, BidPlacedPayload,
} from '@/types/api/realtime.api'

interface AuctionState {
  live:            LiveAuctionState | null
  hydrate:         (auction: AuctionDetail, bids: BidEntry[]) => void
  bidPlaced:       (p: BidPlacedPayload, meId: string | null) => void
  ownBidPlaced:    (r: PlaceBidResponse, me: { id: string; name: string }) => void
  extended:        (p: AuctionExtendedPayload) => void
  ended:           (p: AuctionEndedPayload) => void
  cancelled:       () => void
  outbid:          () => void
  appendOlderBids: (bids: BidEntry[]) => void
  reset:           () => void
}

/** Live state of the auction detail page being viewed. Pushes before hydration are dropped (resync covers them). */
export const useAuctionStore = create<AuctionState>((set) => {
  const update = (fn: (live: LiveAuctionState) => LiveAuctionState) =>
    set((state) => (state.live ? { live: fn(state.live) } : state))

  return {
    live: null,

    hydrate: (auction, bids) =>
      set((state) => {
        const seeded = seedLiveState(auction, bids)
        return { live: state.live?.auctionId === auction.id ? mergeSnapshot(state.live, seeded) : seeded }
      }),

    bidPlaced:       (p, meId) => update((live) => applyBidPlaced(live, p, meId)),
    ownBidPlaced:    (r, me) => update((live) => applyOwnBid(live, r, me)),
    extended:        (p) => update((live) => applyAuctionExtended(live, p)),
    ended:           (p) => update((live) => applyAuctionEnded(live, p)),
    cancelled:       () => update(applyAuctionCancelled),
    outbid:          () => update(applyOutbid),
    appendOlderBids: (bids) => update((live) => appendOlderBids(live, bids)),
    reset:           () => set({ live: null }),
  }
})
```

- [ ] **Step 17: Run to verify it passes**

Run: `npm test -- store/auctionStore.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 18: Rewrite `hooks/useAuctionSocket.ts`**

```ts
'use client'

import { useEffect } from 'react'
import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'
import { toast } from 'sonner'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuthStore } from '@/store/authStore'
import { getFreshAccessToken } from '@/lib/apiClient'
import { formatCurrency } from '@/lib/format'
import { dispatchRealtimeMessage, parseRealtimeMessage, type RealtimeHandlers } from '@/lib/realtime/dispatch'
import { resolveWsEndpoint, withAccessToken } from '@/lib/realtime/ws-url'

const USER_QUEUE = '/user/queue/notifications'

/**
 * Live auction updates over STOMP (spec §6). Subscribes to the auction topic and, when logged in,
 * the private notification queue. Reconnects with exponential backoff; each (re)connect fetches a
 * fresh token, and every reconnect after the first calls `onResync` to recover missed events.
 * Re-runs (disconnects and reconnects) when the auction or the logged-in user changes, so logout
 * drops the private queue.
 */
export function useAuctionSocket(auctionId: string, onResync?: () => void): void {
  const userId = useAuthStore((s) => s.user?.id ?? null)

  useEffect(() => {
    const endpoint = resolveWsEndpoint({
      wsUrl:  process.env.NEXT_PUBLIC_WS_URL,
      apiUrl: process.env.NEXT_PUBLIC_API_URL,
    })

    const handlers: RealtimeHandlers = {
      bidPlaced: (p) => useAuctionStore.getState().bidPlaced(p, userId),
      extended: (p) => {
        useAuctionStore.getState().extended(p)
        toast.info(`Auction extended — now ends at ${new Date(p.newEndTime).toLocaleTimeString()}`)
      },
      ended: (p) => useAuctionStore.getState().ended(p),
      cancelled: () => useAuctionStore.getState().cancelled(),
      outbid: (outbidAuctionId, p) => {
        if (outbidAuctionId === auctionId) useAuctionStore.getState().outbid()
        const where = p.auctionTitle ? ` on “${p.auctionTitle}”` : ''
        toast.warning(`You’ve been outbid${where} — now ${formatCurrency(p.currentPrice)}`)
      },
    }

    const onMessage = (message: IMessage) => {
      const parsed = parseRealtimeMessage(message.body)
      if (parsed) dispatchRealtimeMessage(parsed, auctionId, handlers)
    }

    let connectedBefore = false
    const client = new Client({
      reconnectDelay:    1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 0, // the broker has no heartbeats configured (see roadmap risks)
      heartbeatOutgoing: 0,
      beforeConnect: async (c) => {
        const token = userId ? await getFreshAccessToken() : null
        c.brokerURL = withAccessToken(endpoint, token)
      },
      onConnect: () => {
        client.subscribe(`/topic/auctions/${auctionId}`, onMessage)
        if (userId) client.subscribe(USER_QUEUE, onMessage)
        if (connectedBefore) onResync?.()
        connectedBefore = true
      },
    })

    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [auctionId, userId, onResync])
}
```

- [ ] **Step 19: Env example and gitignore**

Create `frontend/.env.example`:

```bash
# Gateway base URL for REST calls
NEXT_PUBLIC_API_URL=http://localhost:8080
# Optional: STOMP WebSocket endpoint. Defaults to <NEXT_PUBLIC_API_URL as ws/wss>/ws-notifications/websocket
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws-notifications/websocket
```

In `frontend/.gitignore`, directly under the `.env*` line add `!.env.example`.

Run: `git check-ignore -v .env.example`
Expected: no output (the file is no longer ignored).

- [ ] **Step 20: Verify**

Run: `npm test && npx tsc --noEmit && npm run lint`
Expected: all green. `grep -rn "socket.io" --include=*.ts --include=*.tsx . | grep -v node_modules` → no matches.

---

### Task 5: Wire the detail page — live panel, real bid form, live history

**Files:**
- Create: `frontend/hooks/useLiveAuction.ts`
- Create: `frontend/components/auction/AuctionLiveBridge.tsx`
- Create: `frontend/components/auction/LiveBidHistory.tsx`
- Modify: `frontend/components/auction/BidPanel.tsx`
- Modify: `frontend/components/auction/BidPanelLive.tsx`
- Rewrite: `frontend/components/auction/BidForm.tsx`
- Modify: `frontend/app/auctions/[id]/page.tsx`

**Interfaces:**
- Consumes: `useAuctionStore` (Task 4), `useAuctionSocket` (Task 4), `deriveDisplayAuction` (Task 3), `bidService`, `BID_PAGE_SIZE`, `EMPTY_BID_PAGE`, `classifyBidError`, `describeBidFailure`, `toBidHistoryItem` (Task 2), `minimumNextBid`, `parseDollarInput`, `isLessThan`, `addDollars` (Task 1), `useHasMounted` (`@/hooks/useHasMounted`), `useWallet` (`@/hooks/useWallet`).
- Produces: `useLiveAuction(auction: AuctionDetail): { auction: AuctionDetail; bids: BidEntry[] | null; isOutbid: boolean; userId: string | null }`; `<AuctionLiveBridge auction initialBids />`; `<LiveBidHistory auction initialBids initialHasMore />`; `BidForm` props `{ auctionId: string; currentBid: number; bidIncrement: number; startingPrice: number; totalBids: number; className?: string }`.

- [ ] **Step 1: Create `hooks/useLiveAuction.ts`**

```ts
'use client'

import { useMemo } from 'react'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuthStore } from '@/store/authStore'
import { useHasMounted } from '@/hooks/useHasMounted'
import { deriveDisplayAuction } from '@/lib/realtime/auction-live'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

/**
 * The auction as the viewer should see it: live store values over the server snapshot.
 * The user id is withheld until mount so server and first client render match.
 */
export function useLiveAuction(base: AuctionDetail): {
  auction:  AuctionDetail
  bids:     BidEntry[] | null
  isOutbid: boolean
  userId:   string | null
} {
  const live = useAuctionStore((s) => (s.live?.auctionId === base.id ? s.live : null))
  const storedUserId = useAuthStore((s) => s.user?.id ?? null)
  const mounted = useHasMounted()
  const userId = mounted ? storedUserId : null

  return useMemo(() => {
    const { auction, isOutbid } = deriveDisplayAuction(base, live, userId)
    return { auction, bids: live?.bids ?? null, isOutbid, userId }
  }, [base, live, userId])
}
```

Check `hooks/useHasMounted.ts` exports `useHasMounted` returning `boolean`; if the export name differs, use the actual name.

- [ ] **Step 2: Create `components/auction/AuctionLiveBridge.tsx`**

```tsx
'use client'

import { useCallback, useEffect } from 'react'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuctionSocket } from '@/hooks/useAuctionSocket'
import { auctionService } from '@/services/auction.service'
import { bidService } from '@/services/bid.service'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

interface AuctionLiveBridgeProps {
  auction:     AuctionDetail
  initialBids: BidEntry[]
}

/** Seeds the live store from the server render and keeps it current over STOMP. Renders nothing. */
export function AuctionLiveBridge({ auction, initialBids }: AuctionLiveBridgeProps) {
  // Declared before the socket hook so the store is hydrated before the first push is applied.
  useEffect(() => {
    useAuctionStore.getState().hydrate(auction, initialBids)
  }, [auction, initialBids])

  useEffect(() => () => useAuctionStore.getState().reset(), [])

  const resync = useCallback(() => {
    void Promise.all([auctionService.getAuctionById(auction.id), bidService.getAuctionBids(auction.id)])
      .then(([fresh, page]) => {
        if (fresh) useAuctionStore.getState().hydrate(fresh, page.items)
      })
      .catch(() => {
        // Best effort: the next push or a reload catches up.
      })
  }, [auction.id])

  useAuctionSocket(auction.id, resync)
  return null
}
```

- [ ] **Step 3: Update `components/auction/BidPanel.tsx`**

Add `import { useLiveAuction } from '@/hooks/useLiveAuction'` and rename the prop to `base`:

```tsx
export function BidPanel({ auction: base }: BidPanelProps) {
  const { auction, userId } = useLiveAuction(base)
  const isOwner = !!userId && !!auction.seller && userId === auction.seller.id
  const isCurrentUserWinning = !!userId && userId === auction.currentWinnerId
```

Remove the `useAuthStore` import and the `const user = …` line; the rest of the function body is unchanged.

- [ ] **Step 4: Update `components/auction/BidPanelLive.tsx`**

Replace the `<BidForm … />` element with:

```tsx
        <BidForm
          auctionId={auction.id}
          currentBid={auction.currentBid}
          bidIncrement={auction.bidIncrement}
          startingPrice={auction.startingPrice}
          totalBids={auction.totalBids}
        />
```

- [ ] **Step 5: Rewrite `components/auction/BidForm.tsx`**

```tsx
'use client'

import { useState, useCallback } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Zap } from 'lucide-react'
import { toast } from 'sonner'
import { BidInput } from './BidInput'
import { BidButton } from './BidButton'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { formatCurrency } from '@/lib/format'
import { addDollars, isLessThan, parseDollarInput } from '@/lib/money'
import { minimumNextBid } from '@/lib/auction-utils'
import { classifyBidError, describeBidFailure, type BidFailure } from '@/lib/bid-errors'
import { bidService } from '@/services/bid.service'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuthStore } from '@/store/authStore'
import { useWallet } from '@/hooks/useWallet'
import { cn } from '@/lib/utils'

interface BidFormProps {
  auctionId:     string
  currentBid:    number   // dollars
  bidIncrement:  number   // dollars
  startingPrice: number   // dollars
  totalBids:     number
  className?:    string
}

export function BidForm({ auctionId, currentBid, bidIncrement, startingPrice, totalBids, className }: BidFormProps) {
  const router = useRouter()
  const user = useAuthStore((s) => s.user)
  const { refetch: refetchWallet } = useWallet()

  const [bidValue, setBidValue]           = useState('')
  const [inputError, setInputError]       = useState<string>()
  const [failure, setFailure]             = useState<BidFailure | null>(null)
  const [serverMinimum, setServerMinimum] = useState<number | null>(null)
  const [isLoading, setIsLoading]         = useState(false)

  const computedMinimum = minimumNextBid({ currentBid, bidIncrement, startingPrice, totalBids })
  const minBid = serverMinimum !== null && isLessThan(computedMinimum, serverMinimum) ? serverMinimum : computedMinimum
  const parsedBid = parseDollarInput(bidValue)
  const failureView = failure ? describeBidFailure(failure) : null
  const formDisabled = failureView?.disablesForm ?? false

  const handleIncrement = useCallback(() => {
    const base = parsedBid ?? currentBid
    setBidValue(addDollars(isLessThan(base, minBid) ? minBid : base, parsedBid === null ? 0 : bidIncrement).toFixed(2))
    setInputError(undefined)
  }, [parsedBid, currentBid, minBid, bidIncrement])

  const handleDecrement = useCallback(() => {
    const next = addDollars(parsedBid ?? minBid, -bidIncrement)
    setBidValue((isLessThan(next, minBid) ? minBid : next).toFixed(2))
    setInputError(undefined)
  }, [parsedBid, minBid, bidIncrement])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!user) {
      router.push('/login')
      return
    }
    if (parsedBid === null) {
      setInputError('Enter a valid amount (up to 2 decimals)')
      return
    }
    if (isLessThan(parsedBid, minBid)) {
      setInputError(`Minimum bid is ${formatCurrency(minBid)}`)
      return
    }

    setInputError(undefined)
    setFailure(null)
    setIsLoading(true)
    try {
      const placed = await bidService.placeBid({ auctionId, amount: parsedBid })
      useAuctionStore.getState().ownBidPlaced(placed, { id: user.id, name: 'You' })
      toast.success(`Bid placed: ${formatCurrency(placed.amount)}`)
      setBidValue('')
      setServerMinimum(null)
      void refetchWallet() // the first bid locks the deposit
    } catch (err) {
      const f = classifyBidError(err)
      if (f.kind === 'unauthenticated') {
        router.push('/login')
        return
      }
      if (f.kind === 'too-low' && f.minimumBid !== null) setServerMinimum(f.minimumBid)
      setFailure(f)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className={cn('flex flex-col gap-3', className)}>
      <BidInput
        value={bidValue}
        onChange={(e) => { setBidValue(e.target.value); setInputError(undefined) }}
        placeholder={minBid.toFixed(2)}
        min={minBid}
        step={bidIncrement}
        error={inputError}
        onIncrement={handleIncrement}
        onDecrement={handleDecrement}
        disabled={formDisabled || isLoading}
        showSteppers
      />

      <BidButton
        amount={parsedBid ?? undefined}
        isLoading={isLoading}
        disabled={!bidValue || formDisabled}
      />

      {failureView && (
        <div role="alert" className="flex flex-col gap-1 text-xs text-[var(--color-danger-text)]">
          <p>{failureView.message}</p>
          {failureView.action && (
            <Link href={failureView.action.href} className="font-medium underline underline-offset-2">
              {failureView.action.label}
            </Link>
          )}
        </div>
      )}

      <Separator />

      <div className="flex items-center justify-between">
        <Label htmlFor={`autobid-${auctionId}`} className="flex items-center gap-1.5 text-muted-foreground">
          <Zap className="size-3.5 text-[var(--color-text-brand)]" />
          Auto-bid <span className="text-[10px] uppercase tracking-wide">coming soon</span>
        </Label>
        <Switch id={`autobid-${auctionId}`} checked={false} disabled />
      </div>

      <p className="text-xs text-muted-foreground text-center">
        Minimum next bid: <span className="font-mono">{formatCurrency(minBid)}</span>
      </p>
    </form>
  )
}
```

Notes for the implementer:
- `BidButton` renders a `<Button>` inside the `<form>`; confirm it submits (a `Button` without `type` defaults to `submit`). If `components/ui/button.tsx` sets a default `type="button"`, pass `type="submit"` through `BidButton` by adding an optional `type?: 'button' | 'submit'` prop.
- If `Switch` does not accept `disabled`, check `components/ui/switch.tsx` for the prop name and use it.
- `handleIncrement` from an empty input fills the minimum; from a typed value it adds one increment.

- [ ] **Step 6: Create `components/auction/LiveBidHistory.tsx`**

```tsx
'use client'

import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { BidHistory } from './BidHistory'
import { useLiveAuction } from '@/hooks/useLiveAuction'
import { useAuctionStore } from '@/store/auctionStore'
import { bidService, BID_PAGE_SIZE } from '@/services/bid.service'
import { toBidHistoryItem } from '@/types/mappers/bid.mapper'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

interface LiveBidHistoryProps {
  auction:        AuctionDetail
  initialBids:    BidEntry[]
  initialHasMore: boolean
}

export function LiveBidHistory({ auction: base, initialBids, initialHasMore }: LiveBidHistoryProps) {
  const { auction, bids, userId } = useLiveAuction(base)
  const [page, setPage]         = useState(0)
  const [hasMore, setHasMore]   = useState(initialHasMore)
  const [isLoading, setLoading] = useState(false)

  const entries = bids ?? initialBids
  const items = useMemo(
    () => entries.map((b) => toBidHistoryItem(b, { currentUserId: userId, currentPrice: auction.currentBid })),
    [entries, userId, auction.currentBid],
  )

  // New live bids shift offsets; a later page may repeat rows, which the store dedupes by id.
  async function loadMore() {
    if (isLoading) return
    setLoading(true)
    try {
      const next = await bidService.getAuctionBids(base.id, { page: page + 1, size: BID_PAGE_SIZE })
      useAuctionStore.getState().appendOlderBids(next.items)
      setPage(next.page)
      setHasMore(next.hasNext)
    } catch {
      toast.error('Could not load more bids')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section>
      <h2 className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-4">
        Bid History · {auction.totalBids}
      </h2>
      <BidHistory items={items} hasMore={hasMore && !isLoading} onLoadMore={loadMore} />
    </section>
  )
}
```

- [ ] **Step 7: Update `app/auctions/[id]/page.tsx`**

- Imports: remove `BidHistory` and `toBidHistoryItem`; add `import { AuctionLiveBridge } from '@/components/auction/AuctionLiveBridge'`, `import { LiveBidHistory } from '@/components/auction/LiveBidHistory'`, and extend the bid service import to `import { bidService, EMPTY_BID_PAGE, BID_PAGE_SIZE } from '@/services/bid.service'`.
- Data: `bidService.getAuctionBids(id, { page: 0, size: BID_PAGE_SIZE }).catch(() => EMPTY_BID_PAGE)` (result `firstBidPage`).
- Pass the **raw** `auction` (not `displayAuction`) to the live components — they derive the effective status themselves:
  - change `<BidPanel auction={displayAuction} />` to `<BidPanel auction={auction} />`;
  - directly inside `<main …>` as its first child add `<AuctionLiveBridge auction={auction} initialBids={firstBidPage.items} />`;
  - replace the whole trailing `<section>…Bid History…</section>` with
    `<LiveBidHistory auction={auction} initialBids={firstBidPage.items} initialHasMore={firstBidPage.hasNext} />`.
- Keep `displayAuction` for `ItemSpecs` and `effectiveStatus` for the title `StatusBadge`.

- [ ] **Step 8: Verify**

Run: `npm test && npx tsc --noEmit && npm run lint && npm run build`
Expected: all green; build succeeds. If the build reports a hydration or server/client boundary error, fix it (all new components start with `'use client'`; `page.tsx` stays a server component).

---

### Task 6: Docs and end-to-end verification

**Files:**
- Modify: `frontend/CLAUDE.md`, `frontend/AGENTS.md`
- Modify: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (Story 7 checkboxes, risks)
- Modify: `docs/architecture.md` only if it describes the frontend's socket.io client (check with `grep -n "socket.io" docs/architecture.md`)

- [ ] **Step 1: Update `frontend/CLAUDE.md`**

- **Commands:** replace "No test suite is configured. There is no test runner command." with `npm test         # Vitest unit tests (pure modules: lib/, services/, store/, types/mappers/)`.
- **Environment:** replace `NEXT_PUBLIC_SOCKET_URL=<websocket server url>` with `NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws-notifications/websocket   # optional; derived from NEXT_PUBLIC_API_URL when unset` and mention `.env.example`.
- **State Management:** `auctionStore` — "live state (`LiveAuctionState`) of the auction detail page being viewed, updated only through the monotonic reducers in `lib/realtime/auction-live.ts`; reset on unmount".
- **Real-time section:** replace with: `hooks/useAuctionSocket.ts` connects with `@stomp/stompjs` over native WebSocket to `/ws-notifications/websocket` through the gateway (JWT as `access_token`, refreshed on every connect), subscribes to `/topic/auctions/{id}` (`BID_PLACED`, `AUCTION_EXTENDED`, `AUCTION_ENDED`, `AUCTION_CANCELLED`) and, when logged in, `/user/queue/notifications` (`OUTBID`). Messages are parsed/routed by `lib/realtime/dispatch.ts`; `AuctionLiveBridge` hydrates the store and resyncs after reconnects; components read through `useLiveAuction`. Clients are receive-only.
- **Money:** replace the cents paragraph with: "Amounts are **dollars** (`number`) end-to-end, exactly as the API sends them. Do arithmetic/comparison through `lib/money.ts` (integer cents internally) and display with `formatCurrency(dollars)` in `font-mono`."

- [ ] **Step 2: Update `frontend/AGENTS.md`**

Change the Tech Stack line `- **Real-time:** Socket.io-client for live auction updates.` to `- **Real-time:** STOMP (`@stomp/stompjs`) over native WebSocket for live auction updates — see `hooks/useAuctionSocket.ts`.`

- [ ] **Step 3: Update the roadmap**

In `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` Story 7: tick 7.1–7.5; leave 7.6 unticked until the manual E2E below is done. Replace the `hooks/useAuctionSocket.ts` file bullet's "`@stomp/stompjs` + `sockjs-client`" with "`@stomp/stompjs` over native WebSocket (`/ws-notifications/websocket`)", change `package.json: add @stomp/stompjs and sockjs-client` to `add @stomp/stompjs, vitest`, and add under Story 7: "Plan: `docs/superpowers/plans/2026-09-30-frontend-bidding.md`. Not done: My-bids tab / dashboard (needs a cross-auction endpoint); auto-bid UI disabled until auto-bid ships." In the risks section, amend the SockJS sticky-routing bullet: "The frontend uses the raw WebSocket transport, so this only affects non-browser SockJS clients."

- [ ] **Step 4: Full verification**

Run: `npm test && npx tsc --noEmit && npm run lint && npm run build`
Expected: all tests pass; no type/lint errors; build succeeds. Record the test count.

- [ ] **Step 5: Manual E2E (needs the full stack — run by the user, not by an agent)**

Start Docker infra, all backend services, then `npm run dev`. Prepare users A and B with funded wallets, user C with an empty wallet, and an ACTIVE auction by a seller S ending in ~10 minutes.
1. Open the auction as anonymous in one window: history and price render; DevTools → Network → WS shows a connection to `/ws-notifications/websocket` without `access_token`.
2. Log in as A in window 2 and B in window 3 on the same auction. A bids the minimum → toast "Bid placed", A's panel shows "You're winning", the price updates in all three windows without reload, the new row appears at the top of history, the wallet balance drops by the deposit.
3. B bids higher → A gets the "You've been outbid" toast and the Outbid badge; all windows update.
4. A types a bid below the new minimum while B bids again → A sees "Someone bid first — the minimum is now $X", and the minimum label updates.
5. Wait until < 2 minutes remain, bid → all windows get the "Auction extended" toast and the countdown jumps forward.
6. As C, bid → "You need $X available…" with a "Top up wallet" link to `/wallet`.
7. As S, open the auction → seller panel, no bid form.
8. Stop media-service for 30 s, bid as B, restart it → A's window reconnects and shows B's bid (resync).
9. Let the auction end → all windows switch to the ended panel; the winner sees "Won".
10. Log out in A's window → the WS reconnects without `access_token`.

This also covers roadmap 6.5 (live path through the gateway). Tick 7.6 and 6.4/6.5 only after it passes.
