# Auction Detail Page — Buyer Experience Design

**Date:** 2026-05-21  
**Scope:** Full rebuild of `/auctions/[id]` for the buyer, covering all three phases: before bidding (upcoming), during bidding (live), and after bidding (ended/result). Mock data only — no real API integration in this pass.

---

## 1. Page Structure & Layout

Single scrollable page using the existing `Header`, `Footer`, and `BottomNav` shell.

```
Header
──────────────────────────────────────────
AuctionGallery          (full container width)
──────────────────────────────────────────
Title + StatusBadge
Category · Item ID
──────────────────────────────────────────
[Left column: 1fr]   [Right column: 360px]
  Description            BidPanel (sticky)
  ItemSpecs
  SellerCard
──────────────────────────────────────────
BidHistory              (full container width)
──────────────────────────────────────────
Footer / BottomNav
```

- Container: `max-w-[var(--container-auction-detail)] mx-auto px-4`
- Two-column grid: `md:grid-cols-[1fr_360px] gap-8`, single column on mobile
- Bid panel: `sticky top-[80px] self-start`
- Mobile stack order: gallery → bid panel → details (description, specs, seller) → bid history
- `pb-14 md:pb-8` to clear `BottomNav` on mobile

---

## 2. Image Gallery (`AuctionGallery`)

**Location:** `components/auction/AuctionGallery.tsx`

- Main image: `aspect-[4/3]`, `rounded-xl`, `object-cover`, full container width
- Thumbnail row: horizontally scrollable row of `72×72px` square thumbnails, `rounded-lg`, below the main image
- Active thumbnail: `ring-2 ring-[var(--color-text-brand)] ring-offset-2`
- Clicking a thumbnail updates the main image (local `useState`)
- On mobile, main image is also swipeable left/right (via pointer events or touch events)
- Fallback: gray `bg-muted` placeholder with a centered icon when `imageUrls` is empty or all images fail to load
- No lightbox in this pass

**Props:**
```ts
interface AuctionGalleryProps {
  images: string[]   // imageUrls from Auction
  title:  string     // alt text
}
```

---

## 3. Bid Panel (`BidPanel` + three sub-panels)

**Location:** `components/auction/BidPanel.tsx` (orchestrator)

`BidPanel` reads `auction.status` and renders one of three sub-panels:

| Status | Sub-panel rendered |
|---|---|
| `Scheduled` | `BidPanelUpcoming` |
| `Active`, `EndingSoon`, `Critical`, `Outbid` | `BidPanelLive` |
| `Closed`, `Won`, `Lost` | `BidPanelEnded` |

All panels share: `rounded-xl border bg-card p-5 flex flex-col gap-4`

### 3a. BidPanelUpcoming (`BidPanelUpcoming.tsx`)

Shown when the auction has not yet started.

```
Starting price: $500.00

⏰ Starts in
   02 : 14 : 33       ← CountdownTimer pointing at auction.startsAt

[🔔 Notify me when live]   ← toggles local watching state
                            filled/brand color when active

👁 42 watching
```

- Reuses `CountdownTimer` with `endsAt={auction.startsAt}`
- Watch button: local `useState<boolean>`, no API call; filled brand style when active
- Starting price displayed as `formatCurrency(auction.startingPrice)`

### 3b. BidPanelLive (`BidPanelLive.tsx`)

Shown while the auction is accepting bids.

```
Current bid
$1,350.00    🏆 You're winning    (green, only if isCurrentUserWinning)
             ⚡ You've been outbid (warning, only if status === Outbid)

⏱ 01:45:22   ← CountdownTimer pointing at auction.endsAt

Wallet balance: $2,400.00

[$ ___________  + −]     ← BidInput with steppers
[     Place Bid     ]    ← BidButton

───────── Auto-bid ──────────
⚡ Auto-bid         [toggle]
Max auto-bid: $___________   (collapsible, shown when toggle on)

Minimum next bid: $1,351.00

👁 89 watching
```

- Reuses `CurrentBidDisplay`, `CountdownTimer`, `BidInput`, `BidButton`, existing `BidForm` auto-bid logic
- `isCurrentUserWinning` sourced from mock `Auction.winnerId === MOCK_CURRENT_USER_ID`
- Wallet balance from `useWallet` store (mock value, display only — no balance enforcement)
- `BidForm` submit remains a placeholder (`await delay(600)`)

### 3c. BidPanelEnded (`BidPanelEnded.tsx`)

Shown when the auction is over.

**Winner view** (`status === Won`):
```
🎉 You won this auction!       ← green banner
Final bid: $1,350.00

┌──────────────────────────┐
│  Congratulations!        │
│  [💳 Proceed to Payment] │
│  [✉️  Contact Seller]    │
└──────────────────────────┘
```

**Non-winner view** (`status === Lost` or `Closed`):
```
Auction ended                  ← neutral banner
Final bid: $1,350.00

┌──────────────────────────┐
│  You were outbid.        │
│  [Browse similar items]  │  ← links to /auctions?category=...
└──────────────────────────┘
```

- `AuctionResultBanner` is a separate sub-component rendering the top banner
- Payment CTA and Contact Seller are non-functional buttons in this pass (placeholder `onClick`)
- "Browse similar" links to `/auctions?category={auction.categoryId}`

---

## 4. Detail Sections (Left Column)

### 4a. Title + Status (above two-column grid)

```tsx
<h1>{auction.title}</h1>   <StatusBadge status={auction.status} />
<p className="text-muted-foreground text-sm">
  {category.label} · #{auction.id}
</p>
```

### 4b. Description

Simple `<section>` with heading "About this item" and `<p>{auction.description}</p>`.

### 4c. ItemSpecs (`ItemSpecs.tsx`)

**Location:** `components/auction/ItemSpecs.tsx`

A `<dl>` rendered as a two-column grid:

| Label | Value |
|---|---|
| Condition | `auction.condition` |
| Category | category label |
| Item ID | `#AUC-{auction.id}` |
| Starting Price | `formatCurrency(auction.startingPrice)` |
| Reserve Met | ✓ Yes (green) / ✗ Not yet (muted) |
| Buy Now Price | `formatCurrency(auction.buyNowPrice)` (hidden if undefined) |

**Props:**
```ts
interface ItemSpecsProps {
  auction: Auction
}
```

### 4d. SellerCard (`SellerCard.tsx`)

**Location:** `components/auction/SellerCard.tsx`

```
Seller
┌───┐  John Doe
│ J │  ★ 4.8  · 132 auctions
└───┘  [View profile →]
```

- Reuses `UserAvatar` (size "md")
- "View profile" is a non-functional link in this pass
- Rating displayed as `★ {seller.rating.toFixed(1)}`

**Props:**
```ts
interface SellerCardProps {
  seller: {
    id:            string
    name:          string
    avatarUrl?:    string
    rating:        number
    totalAuctions: number
  }
}
```

---

## 5. Bid History (Full-Width Section)

Reuses the existing `BidHistory` component.

- Heading: `Bid History ({auction.totalBids})`
- Renders below the two-column grid, separated by `<Separator />`
- Initially shows 10 rows; "Load more" appends 10 more from mock data (local state)
- `hasMore` is `true` when mock data has more than currently shown items

---

## 6. Data Model Changes

### 6a. `AuctionStatus` enum (add `Scheduled`)

```ts
// lib/design-tokens.ts
export enum AuctionStatus {
  Scheduled  = 'scheduled',   // ← NEW
  Active     = 'active',
  EndingSoon = 'ending-soon',
  Critical   = 'critical',
  Outbid     = 'outbid',
  Closed     = 'closed',
  Won        = 'won',
  Lost       = 'lost',
}
```

Add `Scheduled` tokens to `getStatusTokens` (neutral/gray palette).

### 6b. `Auction` type additions

```ts
// types/auction.ts
export interface Auction {
  // ...existing fields...
  condition:   string                  // e.g. "Near Mint", "Good", "Fair"
  reserveMet:  boolean
  seller: {
    id:            string
    name:          string
    avatarUrl?:    string
    rating:        number
    totalAuctions: number
  }
}
```

### 6c. Mock data additions (`lib/mock-data.ts`)

Add 3 new mock auctions to cover missing states:
- `id: '7'` — `status: Scheduled`, `startsAt` in the future
- `id: '8'` — `status: Won`, `winnerId === MOCK_CURRENT_USER_ID`
- `id: '9'` — `status: Lost`, `winnerId !== MOCK_CURRENT_USER_ID`

Add `seller`, `condition`, `reserveMet` to all existing mock auctions.

Define `MOCK_CURRENT_USER_ID = 'current-user'` in mock-data for winning detection.

---

## 7. Component File Map

```
components/auction/
  AuctionGallery.tsx        ← NEW
  BidPanel.tsx              ← NEW (orchestrator)
  BidPanelUpcoming.tsx      ← NEW
  BidPanelLive.tsx          ← NEW
  BidPanelEnded.tsx         ← NEW
  AuctionResultBanner.tsx   ← NEW
  SellerCard.tsx            ← NEW
  ItemSpecs.tsx             ← NEW
  BidForm.tsx               ← KEEP (used inside BidPanelLive)
  BidInput.tsx              ← KEEP
  BidButton.tsx             ← KEEP
  BidHistory.tsx            ← KEEP (promoted to full-width)
  CountdownTimer.tsx        ← KEEP
  CurrentBidDisplay.tsx     ← KEEP
  StatusBadge.tsx           ← KEEP
  AuctionCard.tsx           ← KEEP (unrelated)
  AuctionGrid.tsx           ← KEEP (unrelated)
  index.ts                  ← UPDATE (export new components)

app/auctions/[id]/page.tsx  ← REPLACE entirely
```

---

## 8. Out of Scope (this pass)

- Real API / WebSocket integration (BidForm submit stays as placeholder delay)
- Lightbox for gallery images
- Payment flow implementation
- Contact Seller messaging
- Watch/notify real persistence
- Wallet balance enforcement (display only)
