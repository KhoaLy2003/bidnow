# Seller-Owned Auction Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the seller views their own auction's public page, replace the Bid/Buy panel with a "Your auction" card that links to the manage page.

**Architecture:** Client-side ownership check inside `BidPanel` — compare `useAuthStore().user.id` against `auction.seller.id` (already in `AuctionDetail`). If they match, render a new `SellerOwnedPanel` component instead of the bid panel. No server component changes, no API changes.

**Tech Stack:** Next.js 16.2 App Router, React, Zustand (`useAuthStore`), Tailwind CSS v4, Lucide React, shadcn `Button`.

## Global Constraints

- No raw hex colors — use CSS variables (`var(--color-*)`) or shadcn semantic tokens only
- Icons from `lucide-react` only
- Strict TypeScript — no `any`
- `Button` component from `@/components/ui/button`; use `render` + `nativeButton={false}` props for link rendering (see existing usage in the codebase)
- Money amounts in font-mono per design system; not applicable here

---

### Task 1: Create `SellerOwnedPanel` component

**Files:**
- Create: `frontend/components/auction/SellerOwnedPanel.tsx`

**Interfaces:**
- Consumes: nothing from other tasks
- Produces: `SellerOwnedPanel({ auctionId: string }): JSX.Element` — used by Task 2

- [ ] **Step 1: Create the component file**

`frontend/components/auction/SellerOwnedPanel.tsx`:

```tsx
'use client'

import Link   from 'next/link'
import { Tag } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface SellerOwnedPanelProps {
  auctionId: string
}

export function SellerOwnedPanel({ auctionId }: SellerOwnedPanelProps) {
  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col gap-4 px-[18px] py-[18px]">
      <div className="flex items-center gap-2">
        <Tag className="size-4 text-muted-foreground" />
        <span className="font-medium text-sm">Your auction</span>
      </div>
      <p className="text-xs text-muted-foreground">
        Sellers can&apos;t bid on their own auctions.
      </p>
      <Button
        variant="outline"
        size="lg"
        className="w-full"
        render={<Link href={`/seller/auctions/${auctionId}/manage`} />}
        nativeButton={false}
      >
        Manage auction →
      </Button>
    </div>
  )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npm run build 2>&1 | tail -20`

Expected: no TypeScript errors referencing `SellerOwnedPanel.tsx`.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/SellerOwnedPanel.tsx
git commit -m "feat: add SellerOwnedPanel component"
```

---

### Task 2: Wire ownership check into `BidPanel`

**Files:**
- Modify: `frontend/components/auction/BidPanel.tsx`

**Interfaces:**
- Consumes: `SellerOwnedPanel({ auctionId: string })` from Task 1
- Consumes: `useAuthStore(s => s.user)` → `User | null` from `@/store/authStore`
- Consumes: `auction.seller.id: string` and `auction.currentWinnerId?: string` from `AuctionDetail`
- Produces: unchanged external API (`BidPanel({ auction: AuctionDetail })`)

- [ ] **Step 1: Rewrite `BidPanel.tsx`**

Replace the entire file content with:

```tsx
'use client'

import { AuctionStatus }    from '@/lib/design-tokens'
import { useAuthStore }     from '@/store/authStore'
import { BidPanelUpcoming } from './BidPanelUpcoming'
import { BidPanelLive }     from './BidPanelLive'
import { BidPanelEnded }    from './BidPanelEnded'
import { SellerOwnedPanel } from './SellerOwnedPanel'
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
  const user = useAuthStore(s => s.user)
  const isOwner = !!user && !!auction.seller && user.id === auction.seller.id
  const isCurrentUserWinning = user?.id === auction.currentWinnerId

  if (isOwner) {
    return <SellerOwnedPanel auctionId={auction.id} />
  }
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

Note: `MOCK_CURRENT_USER_ID` import is intentionally removed. The winning-bid check now uses the real `user.id` from the auth store.

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npm run build 2>&1 | tail -20`

Expected: no TypeScript errors. `MOCK_CURRENT_USER_ID` is no longer imported anywhere in the auction components (it remains exported from `mock-data.ts` — leave that file alone).

- [ ] **Step 3: Manual verification — seller view**

1. Start dev server: `cd frontend && npm run dev`
2. Log in as a user who owns at least one active or scheduled auction
3. Navigate to that auction's public page: `/auctions/[id]`
4. Verify the right-hand panel shows:
   - `Tag` icon + "Your auction" heading
   - "Sellers can't bid on their own auctions." subtext
   - "Manage auction →" button
5. Click "Manage auction →" — verify it navigates to `/seller/auctions/[id]/manage`

- [ ] **Step 4: Manual verification — buyer view**

1. Log out (or use a different account that does not own the auction)
2. Navigate to the same auction's public page
3. Verify the normal bid panel renders (bid input, Place Bid button, Buy Now block if applicable)

- [ ] **Step 5: Manual verification — logged-out view**

1. Log out completely
2. Navigate to the same auction's public page
3. Verify the normal bid panel renders (no seller panel shown for anonymous visitors)

- [ ] **Step 6: Commit**

```bash
git add frontend/components/auction/BidPanel.tsx
git commit -m "feat: show SellerOwnedPanel when seller views their own auction"
```
