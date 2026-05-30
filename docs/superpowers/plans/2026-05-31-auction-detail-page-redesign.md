# Auction Detail Page Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mock-backed auction detail page with a real-API-aligned type layer, remove fields the API doesn't return, and add Buy Now support.

**Architecture:** New `AuctionDetail` UI type + `AuctionDetailResponse` API type + mapper keep the browse page (`Auction` type) untouched. `getAuctionById` fetches `GET /api/v1/auctions/public/{id}`, maps to `AuctionDetail`, and all detail-page components consume that type directly.

**Tech Stack:** Next.js 16.2 App Router, TypeScript strict mode, Tailwind CSS v4, Lucide React icons. Verification via `npx tsc --noEmit` (no test runner configured).

---

## File Map

| File | Change |
|---|---|
| `frontend/types/api/auction.api.ts` | Add `UserSummaryResponse`, `AuctionDetailResponse` |
| `frontend/types/ui/auction.ui.ts` | Add `AuctionDetailSeller`, `AuctionDetail` |
| `frontend/types/mappers/auction.mapper.ts` | Add `mapAuctionDetailResponse` |
| `frontend/lib/auction-utils.ts` | Widen `getAuctionStatus` signature to accept `AuctionDetail` |
| `frontend/services/auction.service.ts` | Replace mock in `getAuctionById` with real API fetch |
| `frontend/components/auction/AuctionGallery.tsx` | Accept `AuctionImageResponse[]` instead of `string[]` |
| `frontend/components/auction/ItemSpecs.tsx` | Accept `AuctionDetail`; swap rows |
| `frontend/components/auction/SellerCard.tsx` | Accept `AuctionDetailSeller \| null`; simplified card with badge |
| `frontend/components/auction/BidPanelLive.tsx` | Accept `AuctionDetail`; add Buy Now block; inline footer |
| `frontend/components/auction/BidPanelUpcoming.tsx` | Accept `AuctionDetail`; inline footer |
| `frontend/components/auction/BidPanelEnded.tsx` | Accept `AuctionDetail`; inline footer |
| `frontend/components/auction/BidPanel.tsx` | Accept `AuctionDetail` |
| `frontend/components/auction/WatchingFooter.tsx` | **Delete** |
| `frontend/app/auctions/[id]/page.tsx` | Remove `CATEGORY_LABELS`, cleanup; pass `AuctionDetail` |

---

## Task 1: Add API and UI types

**Files:**
- Modify: `frontend/types/api/auction.api.ts`
- Modify: `frontend/types/ui/auction.ui.ts`

- [ ] **Step 1: Add `UserSummaryResponse` and `AuctionDetailResponse` to the API types file**

Open `frontend/types/api/auction.api.ts` and append after the last existing interface:

```ts
export interface UserSummaryResponse {
  id: string
  name: string
  avatarUrl?: string
}

export interface AuctionDetailResponse {
  id: string
  title: string
  description: string
  category: AuctionCategoryResponse
  startingPrice: number
  bidIncrement: number
  buyNowPrice?: number
  depositAmount: number
  currentPrice: number
  currentWinnerId?: string
  totalBids: number
  status: string
  startTime: string
  endTime: string
  originalEndTime: string
  extensionCount: number
  completedAt?: string
  winnerId?: string
  images: AuctionImageResponse[]
  seller: UserSummaryResponse | null
  createdAt: string
}
```

- [ ] **Step 2: Add `AuctionDetailSeller` and `AuctionDetail` to the UI types file**

Open `frontend/types/ui/auction.ui.ts`. Add this import at the top (after the existing import):

```ts
import type { AuctionImageResponse } from '@/types/api/auction.api'
```

Then append after the existing interfaces:

```ts
export interface AuctionDetailSeller {
  id: string
  name: string
  avatarUrl?: string
}

export interface AuctionDetail {
  id: string
  title: string
  description: string
  categoryId: string
  categoryName: string
  startingPrice: number
  bidIncrement: number
  buyNowPrice?: number
  depositAmount: number
  currentBid: number
  currentWinnerId?: string
  totalBids: number
  status: AuctionStatus
  startsAt: Date
  endsAt: Date
  originalEndAt: Date
  extensionCount: number
  completedAt?: Date
  winnerId?: string
  images: AuctionImageResponse[]
  seller: AuctionDetailSeller | null
  createdAt: Date
}
```

- [ ] **Step 3: Verify types compile**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no new errors related to `AuctionDetail` or `AuctionDetailResponse`.

- [ ] **Step 4: Commit**

```bash
git add frontend/types/api/auction.api.ts frontend/types/ui/auction.ui.ts
git commit -m "feat(types): add AuctionDetailResponse and AuctionDetail types"
```

---

## Task 2: Add mapper

**Files:**
- Modify: `frontend/types/mappers/auction.mapper.ts`

- [ ] **Step 1: Add the mapper function**

Open `frontend/types/mappers/auction.mapper.ts`. Add these imports at the top:

```ts
import type { AuctionDetailResponse } from '@/types/api/auction.api'
import type { AuctionDetail, AuctionDetailSeller } from '@/types/ui/auction.ui'
import { AuctionStatus } from '@/lib/design-tokens'
```

Then append this function after the existing `mapAuctionSummaryToSellerAuction`:

```ts
function parseDetailStatus(status: string): AuctionStatus {
  switch (status?.toUpperCase()) {
    case 'ACTIVE':    return AuctionStatus.Active
    case 'SCHEDULED': return AuctionStatus.Scheduled
    default:          return AuctionStatus.Closed
  }
}

export function mapAuctionDetailResponse(dto: AuctionDetailResponse): AuctionDetail {
  const seller: AuctionDetailSeller | null = dto.seller
    ? { id: dto.seller.id, name: dto.seller.name, avatarUrl: dto.seller.avatarUrl }
    : null

  return {
    id:              dto.id,
    title:           dto.title,
    description:     dto.description,
    categoryId:      dto.category.id,
    categoryName:    dto.category.name,
    startingPrice:   dto.startingPrice,
    bidIncrement:    dto.bidIncrement,
    buyNowPrice:     dto.buyNowPrice,
    depositAmount:   dto.depositAmount,
    currentBid:      dto.currentPrice,
    currentWinnerId: dto.currentWinnerId,
    totalBids:       dto.totalBids,
    status:          parseDetailStatus(dto.status),
    startsAt:        new Date(dto.startTime),
    endsAt:          new Date(dto.endTime),
    originalEndAt:   new Date(dto.originalEndTime),
    extensionCount:  dto.extensionCount,
    completedAt:     dto.completedAt ? new Date(dto.completedAt) : undefined,
    winnerId:        dto.winnerId,
    images:          dto.images,
    seller,
    createdAt:       new Date(dto.createdAt),
  }
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/types/mappers/auction.mapper.ts
git commit -m "feat(mapper): add mapAuctionDetailResponse"
```

---

## Task 3: Widen `getAuctionStatus` and wire the service

**Files:**
- Modify: `frontend/lib/auction-utils.ts`
- Modify: `frontend/services/auction.service.ts`

- [ ] **Step 1: Widen `getAuctionStatus` to accept `AuctionDetail` as well as `Auction`**

Open `frontend/lib/auction-utils.ts`. Replace the import and function signature so that it accepts any object with the two fields it actually uses:

```ts
import { AuctionStatus } from '@/lib/design-tokens'

// Remove the `import type { Auction }` line — it is no longer needed.

const WARNING_THRESHOLD_SECONDS  = 5 * 60
const CRITICAL_THRESHOLD_SECONDS = 60

export type TimerState = 'normal' | 'warning' | 'critical'

export function deriveTimerState(endsAt: Date): TimerState {
  const secondsLeft = Math.floor((endsAt.getTime() - Date.now()) / 1000)
  if (secondsLeft <= 0)                            return 'normal'
  if (secondsLeft <= CRITICAL_THRESHOLD_SECONDS)   return 'critical'
  if (secondsLeft <= WARNING_THRESHOLD_SECONDS)    return 'warning'
  return 'normal'
}

export function getAuctionStatus(auction: { status: AuctionStatus; endsAt: Date }): AuctionStatus {
  const terminal: AuctionStatus[] = [
    AuctionStatus.Scheduled,
    AuctionStatus.Closed,
    AuctionStatus.Won,
    AuctionStatus.Lost,
    AuctionStatus.Outbid,
  ]

  if (terminal.includes(auction.status)) {
    return auction.status
  }

  const timerState = deriveTimerState(auction.endsAt)
  if (timerState === 'critical') return AuctionStatus.Critical
  if (timerState === 'warning')  return AuctionStatus.EndingSoon
  return AuctionStatus.Active
}
```

- [ ] **Step 2: Replace `getAuctionById` in the service with a real API call**

Open `frontend/services/auction.service.ts`. Add `mapAuctionDetailResponse` to the imports at the top:

```ts
import { mapAuctionDetailResponse } from '@/types/mappers/auction.mapper'
```

Also add `AuctionDetailResponse` to the existing auction API import:

```ts
import type {
  CreateAuctionRequest,
  UpdateAuctionRequest,
  AuctionResponse,
  AuctionSummaryResponse,
  AuctionCategoryResponse,
  AuctionDetailResponse,        // ← add this
} from '@/types/api/auction.api'
```

Then replace the mock `getAuctionById` method with a real fetch. Find the existing method:

```ts
async getAuctionById(id: string): Promise<Auction | null> {
  await delay(300)
  return MOCK_AUCTIONS.find((a) => a.id === id) ?? null
},
```

Replace it with:

```ts
async getAuctionById(id: string): Promise<AuctionDetail | null> {
  const response = await fetch(`${API_URL}/api/v1/auctions/public/${id}`, {
    method: 'GET',
    cache: 'no-store',
  })

  if (response.status === 404) return null
  if (!response.ok) return null

  const body: ApiResponse<AuctionDetailResponse> = await response.json()
  return mapAuctionDetailResponse(body.data)
},
```

Also add `AuctionDetail` to the existing UI type import at the top of the service:

```ts
import type { Auction, BidHistoryItem, AuctionDetail } from '@/types/ui/auction.ui'
```

Also add `ApiResponse` to the common API import (it's already imported, just confirm it includes it):

```ts
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
```

- [ ] **Step 3: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: type errors appear in `app/auctions/[id]/page.tsx` and the BidPanel components because they still expect `Auction`. That is expected — we fix them in subsequent tasks.

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/auction-utils.ts frontend/services/auction.service.ts
git commit -m "feat(service): wire getAuctionById to real API, widen getAuctionStatus signature"
```

---

## Task 4: Update `AuctionGallery`

**Files:**
- Modify: `frontend/components/auction/AuctionGallery.tsx`

- [ ] **Step 1: Update prop type and internal image extraction**

Replace the entire file content:

```tsx
'use client'

import { useState } from 'react'
import Image from 'next/image'
import { ImageIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { AuctionImageResponse } from '@/types/api/auction.api'

interface AuctionGalleryProps {
  readonly images: AuctionImageResponse[]
  readonly title:  string
}

export function AuctionGallery({ images, title }: AuctionGalleryProps) {
  const [activeIndex, setActiveIndex] = useState(0)
  const [dragStartX, setDragStartX] = useState<number | null>(null)

  const sorted     = [...images].sort((a, b) => a.displayOrder - b.displayOrder)
  const hasImages  = sorted.length > 0
  const activeImage = sorted[activeIndex]

  function cancelDrag() { setDragStartX(null) }

  function handlePointerDown(e: React.PointerEvent) { setDragStartX(e.clientX) }

  function handlePointerUp(e: React.PointerEvent) {
    if (dragStartX === null || !hasImages) return
    const delta = e.clientX - dragStartX
    if (delta < -40 && activeIndex < sorted.length - 1) setActiveIndex(activeIndex + 1)
    if (delta > 40  && activeIndex > 0)                 setActiveIndex(activeIndex - 1)
    setDragStartX(null)
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        className="relative aspect-[4/3] w-full overflow-hidden rounded-xl bg-muted select-none"
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerCancel={cancelDrag}
        onPointerLeave={cancelDrag}
      >
        {hasImages ? (
          <Image
            src={activeImage.imageUrl}
            alt={`${title} — image ${activeIndex + 1}`}
            fill
            className="object-cover"
            sizes="(max-width: 768px) 100vw, 600px"
            priority
          />
        ) : (
          <div className="flex h-full items-center justify-center">
            <ImageIcon className="size-12 text-muted-foreground/40" />
          </div>
        )}

        {hasImages && (
          <div className="absolute right-3 top-3 rounded px-2 py-1 text-xs font-mono text-muted-foreground bg-background/90 border">
            {activeIndex + 1} / {sorted.length}
          </div>
        )}
      </div>

      {hasImages && sorted.length > 1 && (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {sorted.map((img, i) => (
            <button
              key={img.id}
              type="button"
              onClick={() => setActiveIndex(i)}
              className={cn(
                'relative size-[72px] shrink-0 overflow-hidden rounded-lg border transition-[outline,outline-offset] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
                i === activeIndex
                  ? 'outline-2 outline-[var(--color-text-brand)] outline-offset-2'
                  : 'outline-none',
              )}
            >
              <Image
                src={img.thumbnailUrl || img.imageUrl}
                alt={`${title} — thumbnail ${i + 1}`}
                fill
                className="object-cover"
                sizes="72px"
              />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: type error in `page.tsx` where `auction.imageUrls` is passed — that gets fixed in Task 11.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/AuctionGallery.tsx
git commit -m "feat(gallery): accept AuctionImageResponse[] with displayOrder sort"
```

---

## Task 5: Update `ItemSpecs`

**Files:**
- Modify: `frontend/components/auction/ItemSpecs.tsx`

- [ ] **Step 1: Replace the component**

Replace the entire file content:

```tsx
import { formatCurrency } from '@/lib/format'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface ItemSpecsProps {
  readonly auction:       AuctionDetail
  readonly categoryLabel: string
}

function SpecRow({
  label,
  value,
  mono = false,
  last = false,
}: {
  readonly label: string
  readonly value: React.ReactNode
  readonly mono?: boolean
  readonly last?: boolean
}) {
  return (
    <div
      className={`flex items-center justify-between py-[11px] ${last ? '' : 'border-b border-[var(--color-border-default)]'}`}
    >
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className={`text-xs font-medium ${mono ? 'font-mono' : ''}`}>{value}</span>
    </div>
  )
}

export function ItemSpecs({ auction, categoryLabel }: ItemSpecsProps) {
  const rows: { label: string; value: React.ReactNode; mono?: boolean }[] = [
    { label: 'Category',        value: categoryLabel },
    { label: 'Item ID',         value: `#AUC-${auction.id}`, mono: true },
    { label: 'Starting price',  value: formatCurrency(auction.startingPrice), mono: true },
    { label: 'Bid increment',   value: formatCurrency(auction.bidIncrement), mono: true },
    { label: 'Deposit required', value: formatCurrency(auction.depositAmount), mono: true },
    ...(auction.buyNowPrice !== undefined
      ? [{ label: 'Buy now price', value: formatCurrency(auction.buyNowPrice), mono: true }]
      : []),
  ]

  const half      = Math.ceil(rows.length / 2)
  const leftRows  = rows.slice(0, half)
  const rightRows = rows.slice(half)

  return (
    <section>
      <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-3">
        Specifications
      </p>
      <div className="grid grid-cols-1 md:grid-cols-2 md:gap-x-9 border border-[var(--color-border-default)] rounded-lg px-4 py-1">
        <div>
          {leftRows.map((r, i) => (
            <SpecRow key={r.label} label={r.label} value={r.value} mono={r.mono} last={i === leftRows.length - 1} />
          ))}
        </div>
        <div className="md:border-l md:border-[var(--color-border-default)] md:pl-9">
          {rightRows.map((r, i) => (
            <SpecRow key={r.label} label={r.label} value={r.value} mono={r.mono} last={i === rightRows.length - 1} />
          ))}
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: error in `page.tsx` where `ItemSpecs` is called with `auction: Auction` — fixed in Task 11.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/ItemSpecs.tsx
git commit -m "feat(specs): swap ItemSpecs rows to match AuctionDetailResponse fields"
```

---

## Task 6: Update `SellerCard`

**Files:**
- Modify: `frontend/components/auction/SellerCard.tsx`

- [ ] **Step 1: Replace the component**

Replace the entire file content:

```tsx
import { BadgeCheck } from 'lucide-react'
import { UserAvatar } from '@/components/shared/UserAvatar'
import type { AuctionDetailSeller } from '@/types/ui/auction.ui'

interface SellerCardProps {
  seller: AuctionDetailSeller | null
}

export function SellerCard({ seller }: SellerCardProps) {
  if (!seller) return null

  return (
    <section>
      <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-3">
        Seller
      </p>
      <div className="flex items-center justify-between gap-4 border border-[var(--color-border-default)] rounded-lg p-4">
        <div className="flex items-center gap-3">
          <UserAvatar name={seller.name} avatarUrl={seller.avatarUrl} size="lg" />
          <div className="flex flex-col gap-1">
            <span className="font-display font-medium text-base leading-tight">{seller.name}</span>
            <span className="flex items-center gap-1 text-[11px] text-[var(--color-success-text)]">
              <BadgeCheck className="size-3.5" />
              Verified Seller
            </span>
          </div>
        </div>
        <button
          type="button"
          className="text-xs border border-[var(--color-border-default)] rounded px-3 h-8 text-muted-foreground hover:text-foreground hover:border-foreground transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
        >
          View profile
        </button>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: error in `page.tsx` where `SellerCard` receives `auction.seller` of old type — fixed in Task 11.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/SellerCard.tsx
git commit -m "feat(seller): simplify SellerCard to name/avatar/badge, handle null seller"
```

---

## Task 7: Update `BidPanelLive`

**Files:**
- Modify: `frontend/components/auction/BidPanelLive.tsx`

- [ ] **Step 1: Replace the component**

Replace the entire file content:

```tsx
'use client'

import { useEffect } from 'react'
import { CountdownTimer } from './CountdownTimer'
import { CurrentBidDisplay } from './CurrentBidDisplay'
import { BidForm } from './BidForm'
import { StatusBadge } from './StatusBadge'
import { formatCurrency } from '@/lib/format'
import { useWalletStore } from '@/store/walletStore'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface BidPanelLiveProps {
  auction:               AuctionDetail
  isCurrentUserWinning:  boolean
}

export function BidPanelLive({ auction, isCurrentUserWinning }: BidPanelLiveProps) {
  const walletAvailable = useWalletStore((s) => s.available)

  useEffect(() => {
    if (useWalletStore.getState().available === 0) {
      useWalletStore.getState().setBalance({ available: 240_000, held: 0, total: 240_000 })
    }
  }, [])

  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col">
      {/* Panel header */}
      <div className="flex items-center justify-between px-[18px] pt-[14px]">
        <StatusBadge status={auction.status} />
        <span className="font-mono text-xs text-muted-foreground">#{auction.id}</span>
      </div>

      {/* Current bid */}
      <div className="px-[18px] pt-[14px] flex flex-col gap-1">
        <CurrentBidDisplay
          amount={auction.currentBid}
          isCurrentUserWinning={isCurrentUserWinning}
          status={auction.status}
          size="lg"
        />
        <span className="font-mono text-xs text-muted-foreground">
          · {auction.totalBids} bids
        </span>
      </div>

      {/* Countdown */}
      <div className="flex items-center gap-2 px-[18px] pt-[14px]">
        <CountdownTimer endsAt={auction.endsAt} size="md" />
        <span className="text-xs text-muted-foreground">remaining</span>
      </div>

      {/* Wallet balance */}
      <div className="flex items-center justify-between px-[18px] pt-[14px] text-xs text-muted-foreground">
        <span>Wallet balance</span>
        <span className="font-mono text-foreground">{formatCurrency(walletAvailable)}</span>
      </div>

      {/* Bid form */}
      <div className="px-[18px] pt-3 pb-[18px] flex flex-col gap-2">
        <BidForm
          auctionId={auction.id}
          currentBid={auction.currentBid}
          minIncrement={auction.bidIncrement}
        />

        {/* Buy Now block — only when buyNowPrice is set */}
        {auction.buyNowPrice !== undefined && (
          <div className="border border-[var(--color-success-border)] rounded-lg p-3 bg-[var(--color-success-subtle)] flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <span className="text-[9px] font-mono uppercase tracking-widest text-[var(--color-success-text)]">
                or buy it now
              </span>
              <span className="font-mono text-sm font-bold text-[var(--color-success-text)]">
                {formatCurrency(auction.buyNowPrice)}
              </span>
            </div>
            <button
              type="button"
              onClick={() => {}}
              className="w-full h-9 rounded text-sm font-medium bg-[var(--color-success-default)] text-white flex items-center justify-center transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:opacity-90"
            >
              Buy Now →
            </button>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="flex items-center justify-end gap-4 px-[18px] py-3 border-t text-xs text-muted-foreground bg-[var(--color-bg-elevated)]">
        <span className="underline underline-offset-2 cursor-pointer">Share</span>
        <span className="underline underline-offset-2 cursor-pointer">Save</span>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: error in `BidPanel.tsx` which still passes `Auction` — fixed in Task 10.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/BidPanelLive.tsx
git commit -m "feat(panel): add Buy Now block to BidPanelLive, wire bidIncrement"
```

---

## Task 8: Update `BidPanelUpcoming`

**Files:**
- Modify: `frontend/components/auction/BidPanelUpcoming.tsx`

- [ ] **Step 1: Replace prop type and remove `WatchingFooter`**

Replace the entire file content:

```tsx
'use client'

import { useState, useEffect } from 'react'
import { Bell, BellOff } from 'lucide-react'
import { CountdownTimer } from './CountdownTimer'
import { StatusBadge } from './StatusBadge'
import { useCountdown } from '@/hooks/useCountdown'
import { formatCurrency } from '@/lib/format'
import { AuctionStatus } from '@/lib/design-tokens'
import { cn } from '@/lib/utils'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface BidPanelUpcomingProps {
  auction: AuctionDetail
}

export function BidPanelUpcoming({ auction }: BidPanelUpcomingProps) {
  const [notifying, setNotifying] = useState(false)
  const [startDateStr, setStartDateStr] = useState<string | null>(null)
  const { isExpired } = useCountdown(auction.startsAt)

  useEffect(() => {
    setStartDateStr(
      auction.startsAt.toLocaleString('en-US', {
        weekday: 'short', month: 'short', day: 'numeric',
        hour: 'numeric', minute: '2-digit', timeZoneName: 'short',
      })
    )
  }, [auction.startsAt])

  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col">
      {/* Panel header */}
      <div className="flex items-center justify-between px-[18px] pt-[14px]">
        <StatusBadge status={AuctionStatus.Scheduled} />
        <span className="font-mono text-xs text-muted-foreground">#{auction.id}</span>
      </div>

      {/* Starting price */}
      <div className="flex flex-col gap-1 px-[18px] pt-4">
        <span className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground">
          Starting price
        </span>
        <span className="font-mono text-[34px] leading-none tracking-tight">
          {formatCurrency(auction.startingPrice)}
        </span>
      </div>

      {/* Countdown */}
      <div className="flex flex-col gap-2 px-[18px] pt-[18px]">
        <span className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground">
          Starts in
        </span>
        <div
          className="flex items-center justify-between px-4 py-4 rounded-lg border"
          style={{
            background:  'var(--color-auction-scheduled-bg)',
            borderColor: 'var(--color-auction-scheduled-border)',
          }}
        >
          {isExpired ? (
            <span
              className="font-mono text-sm"
              style={{ color: 'var(--color-auction-scheduled-text)' }}
            >
              Starting now…
            </span>
          ) : (
            <>
              <CountdownTimer endsAt={auction.startsAt} size="lg" />
              <span className="font-mono text-[10px] text-muted-foreground">HH:MM:SS</span>
            </>
          )}
        </div>
        {startDateStr && (
          <span className="font-mono text-[10.5px] text-muted-foreground">
            Begins {startDateStr}
          </span>
        )}
      </div>

      {/* Notify button */}
      <div className="px-[18px] py-[18px]">
        <button
          type="button"
          onClick={() => setNotifying(!notifying)}
          className={cn(
            'w-full h-11 rounded px-4 text-sm font-medium flex items-center justify-center gap-2 transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] border',
            notifying
              ? 'bg-[var(--color-text-brand)] text-white border-[var(--color-text-brand)]'
              : 'bg-background border-foreground text-foreground',
          )}
        >
          {notifying
            ? <><Bell className="size-3.5" /> You&apos;ll be notified</>
            : <><BellOff className="size-3.5" /> Notify me when live</>
          }
        </button>
      </div>

      {/* Footer */}
      <div className="flex items-center justify-end gap-4 px-[18px] py-3 border-t text-xs text-muted-foreground bg-[var(--color-bg-elevated)]">
        <span className="underline underline-offset-2 cursor-pointer">Share</span>
        <span className="underline underline-offset-2 cursor-pointer">Save</span>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/BidPanelUpcoming.tsx
git commit -m "feat(panel): update BidPanelUpcoming to AuctionDetail, replace WatchingFooter"
```

---

## Task 9: Update `BidPanelEnded`

**Files:**
- Modify: `frontend/components/auction/BidPanelEnded.tsx`

- [ ] **Step 1: Replace prop type and remove `WatchingFooter`**

Replace the entire file content:

```tsx
'use client'

import Link from 'next/link'
import { AuctionResultBanner } from './AuctionResultBanner'
import { AuctionStatus } from '@/lib/design-tokens'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface BidPanelEndedProps {
  auction: AuctionDetail
}

export function BidPanelEnded({ auction }: BidPanelEndedProps) {
  const { status, categoryId, currentBid } = auction

  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col">
      <AuctionResultBanner
        status={status as AuctionStatus.Won | AuctionStatus.Lost | AuctionStatus.Closed}
        finalBid={currentBid}
      />

      {status === AuctionStatus.Won && (
        <div className="flex flex-col gap-3 p-[18px]">
          <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground">
            Congratulations! Next steps:
          </p>
          <ol className="flex flex-col gap-2 text-sm text-muted-foreground">
            <li className="flex gap-2">
              <span className="font-mono text-muted-foreground/50">01</span>
              <span>Complete payment within <span className="font-medium text-foreground">48 hours</span></span>
            </li>
            <li className="flex gap-2">
              <span className="font-mono text-muted-foreground/50">02</span>
              <span>Confirm shipping with seller</span>
            </li>
            <li className="flex gap-2">
              <span className="font-mono text-muted-foreground/50">03</span>
              <span>Track delivery in My Bids</span>
            </li>
          </ol>
          <button
            type="button"
            className="w-full h-11 rounded px-4 text-sm font-medium bg-[var(--color-text-brand)] text-white border border-[var(--color-text-brand)] transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
            onClick={() => {}}
          >
            Proceed to payment →
          </button>
          <button
            type="button"
            className="w-full h-11 rounded px-4 text-sm font-medium bg-background border border-[var(--color-border-default)] text-foreground transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
            onClick={() => {}}
          >
            Contact seller
          </button>
        </div>
      )}

      {status === AuctionStatus.Lost && (
        <div className="flex flex-col gap-3 p-[18px]">
          <div className="flex flex-col gap-1">
            <p className="text-sm font-medium">You were outbid.</p>
            <p className="text-sm text-muted-foreground">Find similar auctions ending soon.</p>
          </div>
          <Link
            href={`/auctions?category=${categoryId}`}
            className="w-full h-11 rounded px-4 text-sm font-medium bg-background border border-foreground text-foreground flex items-center justify-center transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
          >
            Browse similar items →
          </Link>
          <p className="text-[10.5px] text-center text-muted-foreground">
            Or <span className="underline underline-offset-2 cursor-pointer">save this seller</span> to get notified next time.
          </p>
        </div>
      )}

      {status === AuctionStatus.Closed && (
        <div className="flex flex-col gap-3 p-[18px]">
          <div className="flex flex-col gap-1">
            <p className="text-sm font-medium">This auction has ended.</p>
            <p className="text-sm text-muted-foreground">Discover more items in this category.</p>
          </div>
          <Link
            href={`/auctions?category=${categoryId}`}
            className="w-full h-11 rounded px-4 text-sm font-medium bg-background border border-[var(--color-border-default)] text-foreground flex items-center justify-center transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
          >
            Browse similar items →
          </Link>
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-end gap-4 px-[18px] py-3 border-t text-xs text-muted-foreground bg-[var(--color-bg-elevated)]">
        <span className="underline underline-offset-2 cursor-pointer">Share</span>
        <span className="underline underline-offset-2 cursor-pointer">Save</span>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/BidPanelEnded.tsx
git commit -m "feat(panel): update BidPanelEnded to AuctionDetail, replace WatchingFooter"
```

---

## Task 10: Update `BidPanel`, delete `WatchingFooter`, update the page

**Files:**
- Modify: `frontend/components/auction/BidPanel.tsx`
- Delete: `frontend/components/auction/WatchingFooter.tsx`
- Modify: `frontend/app/auctions/[id]/page.tsx`

- [ ] **Step 1: Update `BidPanel` prop type**

Replace the entire file content:

```tsx
'use client'

import { AuctionStatus } from '@/lib/design-tokens'
import { MOCK_CURRENT_USER_ID } from '@/lib/mock-data'
import { BidPanelUpcoming } from './BidPanelUpcoming'
import { BidPanelLive }     from './BidPanelLive'
import { BidPanelEnded }    from './BidPanelEnded'
import type { AuctionDetail } from '@/types/ui/auction.ui'

const LIVE_STATUSES = new Set<AuctionStatus>([
  AuctionStatus.Active,
  AuctionStatus.EndingSoon,
  AuctionStatus.Critical,
  AuctionStatus.Outbid,
])

const ENDED_STATUSES = new Set<AuctionStatus>([
  AuctionStatus.Closed,
  AuctionStatus.Won,
  AuctionStatus.Lost,
])

interface BidPanelProps {
  auction: AuctionDetail
}

export function BidPanel({ auction }: BidPanelProps) {
  const isCurrentUserWinning = auction.winnerId === MOCK_CURRENT_USER_ID

  if (auction.status === AuctionStatus.Scheduled) {
    return <BidPanelUpcoming auction={auction} />
  }
  if (LIVE_STATUSES.has(auction.status)) {
    return <BidPanelLive auction={auction} isCurrentUserWinning={isCurrentUserWinning} />
  }
  if (ENDED_STATUSES.has(auction.status)) {
    return <BidPanelEnded auction={auction} />
  }
  return <BidPanelLive auction={auction} isCurrentUserWinning={isCurrentUserWinning} />
}
```

- [ ] **Step 2: Delete `WatchingFooter`**

```bash
rm frontend/components/auction/WatchingFooter.tsx
```

- [ ] **Step 3: Update the page**

Replace the entire content of `frontend/app/auctions/[id]/page.tsx`:

```tsx
import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { Header }   from '@/components/layout/Header'
import { Footer }   from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { Separator } from '@/components/ui/separator'
import { StatusBadge }      from '@/components/auction/StatusBadge'
import { AuctionGallery }   from '@/components/auction/AuctionGallery'
import { BidPanel }         from '@/components/auction/BidPanel'
import { ItemSpecs }        from '@/components/auction/ItemSpecs'
import { SellerCard }       from '@/components/auction/SellerCard'
import { BidHistory }       from '@/components/auction/BidHistory'
import { auctionService }   from '@/services/auction.service'
import { getAuctionStatus } from '@/lib/auction-utils'
import { formatRelativeTime } from '@/lib/format'

interface AuctionDetailPageProps {
  params: Promise<{ id: string }>
}

export async function generateMetadata({ params }: AuctionDetailPageProps): Promise<Metadata> {
  const { id } = await params
  const auction = await auctionService.getAuctionById(id)
  return { title: auction?.title ?? 'Auction' }
}

export default async function AuctionDetailPage({ params }: AuctionDetailPageProps) {
  const { id } = await params
  const [auction, bids] = await Promise.all([
    auctionService.getAuctionById(id),
    auctionService.getBidHistory(id),
  ])
  if (!auction) notFound()

  const effectiveStatus = getAuctionStatus(auction)
  const displayAuction  = { ...auction, status: effectiveStatus }

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-detail)] px-4 py-8 pb-14 md:pb-8">

        {/* Breadcrumb */}
        <nav className="flex items-center gap-1.5 text-xs text-muted-foreground mb-6" aria-label="Breadcrumb">
          <span>Browse</span>
          <span>›</span>
          <span>{auction.categoryName}</span>
          <span>›</span>
          <span className="text-foreground truncate max-w-[200px]">{auction.title}</span>
        </nav>

        {/* Title row */}
        <div className="flex items-start justify-between gap-4 mb-2">
          <h1 className="font-display font-medium text-[length:var(--font-size-2xl)] md:text-[2.25rem] leading-tight tracking-tight">
            {auction.title}
          </h1>
          <StatusBadge status={effectiveStatus} className="shrink-0 mt-1" />
        </div>

        {/* Meta row */}
        <p className="text-[11px] font-mono uppercase tracking-wide text-muted-foreground mb-6">
          {auction.categoryName} · #{auction.id} · Listed {formatRelativeTime(auction.startsAt)}
        </p>

        {/* Gallery */}
        <div className="mb-8">
          <AuctionGallery images={auction.images} title={auction.title} />
        </div>

        {/* Two-column body */}
        <div className="flex flex-col gap-6 md:grid md:grid-cols-[1fr_360px] md:gap-8 md:items-start mb-8">

          <div className="order-2 md:order-1 flex flex-col gap-8">
            <section>
              <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-3">
                About this item
              </p>
              <p className="text-sm leading-relaxed text-muted-foreground max-w-prose">
                {auction.description}
              </p>
            </section>

            <ItemSpecs auction={displayAuction} categoryLabel={auction.categoryName} />
            <SellerCard seller={auction.seller} />
          </div>

          <div className="order-1 md:order-2 md:sticky md:top-20 md:self-start">
            <BidPanel auction={displayAuction} />
          </div>
        </div>

        <Separator className="mb-8" />

        <section>
          <h2 className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-4">
            Bid History · {auction.totalBids}
          </h2>
          <BidHistory items={bids} />
        </section>
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
```

- [ ] **Step 4: Check for any remaining `WatchingFooter` imports**

```bash
cd frontend && grep -r "WatchingFooter" --include="*.tsx" --include="*.ts" .
```

Expected: no output. If any files still import `WatchingFooter`, remove those imports now.

- [ ] **Step 5: Final type check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: zero errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/components/auction/BidPanel.tsx frontend/app/auctions/[id]/page.tsx
git rm frontend/components/auction/WatchingFooter.tsx
git commit -m "feat(detail): wire AuctionDetail through page and BidPanel, remove WatchingFooter"
```

---

## Verification

After all tasks are complete, start the dev server and open a detail page:

```bash
cd frontend && npm run dev
```

Open `http://localhost:3000/auctions/<any-id>`.

Check:
- [ ] Page loads without runtime errors (check browser console)
- [ ] Breadcrumb shows `auction.categoryName` (not a hardcoded key like "watches")
- [ ] `ItemSpecs` shows: Category, Item ID, Starting price, Bid increment, Deposit required (and Buy now price if set) — no Condition or Reserve rows
- [ ] `SellerCard` shows: avatar, name, "Verified Seller" badge — no stars, no auction count. Hidden entirely when seller is null.
- [ ] `BidPanelLive` shows: green Buy Now block below the bid form (only on auctions with `buyNowPrice`)
- [ ] `BidPanelLive`, `BidPanelUpcoming`, `BidPanelEnded` footers show Share/Save links without a watcher count
- [ ] `WatchingFooter.tsx` no longer exists in the repo
