# Auction Detail Page Redesign

**Date:** 2026-05-31
**Branch:** feature/public-auction-discovery
**Scope:** `frontend/app/auctions/[id]` and its component tree

## Problem

The public auction detail page (`app/auctions/[id]/page.tsx`) is built against a mock `Auction` type that includes fields the real `AuctionDetailResponse` API does not return (`condition`, `reserveMet`, `watchers`, seller `rating`, seller `totalAuctions`). The page also misses fields the API does return (`bidIncrement`, `depositAmount`, `extensionCount`). The service method still returns mock data.

This spec covers:
1. Replacing the mock type/service with a real API-aligned type layer
2. Removing unsupported UI elements
3. Surfacing newly available fields
4. Adding a Buy Now CTA for auctions with `buyNowPrice` set

---

## Decisions

| Question | Decision |
|---|---|
| Type strategy | New `AuctionDetail` UI type + new `AuctionDetailResponse` API type + mapper. Existing `Auction` type and browse components are untouched. |
| SellerCard when seller data is limited | Show avatar + name + "Verified Seller" badge by default. Remove rating and auction count. No extra API call. |
| Buy Now placement | Dedicated green-tinted block below the bid form in `BidPanelLive`, rendered only when `buyNowPrice` is defined. |

---

## Type Layer

### `types/api/auction.api.ts` — add `AuctionDetailResponse` and `UserSummaryResponse`

```ts
export interface AuctionDetailResponse {
  id: string
  title: string
  description: string
  category: AuctionCategoryResponse        // { id, name, slug }
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

export interface UserSummaryResponse {
  id: string
  name: string
  avatarUrl?: string
}
```

### `types/ui/auction.ui.ts` — add `AuctionDetail` and `AuctionDetailSeller`

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
  currentBid: number           // mapped from currentPrice
  currentWinnerId?: string
  totalBids: number
  status: AuctionStatus
  startsAt: Date               // mapped from startTime
  endsAt: Date                 // mapped from endTime
  originalEndAt: Date          // mapped from originalEndTime
  extensionCount: number
  completedAt?: Date
  winnerId?: string
  images: AuctionImageResponse[]
  seller: AuctionDetailSeller | null
  createdAt: Date
}
```

### `types/mappers/auction.mapper.ts` — add `mapAuctionDetailResponse`

Maps all field renames and parses `status` string to `AuctionStatus` enum using the same logic as the existing `mapAuctionSummaryToSellerAuction`.

```ts
export function mapAuctionDetailResponse(dto: AuctionDetailResponse): AuctionDetail
```

---

## Service

### `services/auction.service.ts` — update `getAuctionById`

Replace mock return with a real fetch:

```
GET /api/v1/auctions/{id}/public
→ ApiResponse<AuctionDetailResponse>
→ mapAuctionDetailResponse(data)
→ AuctionDetail | null
```

No auth header required (public endpoint). On non-200 or missing data, return `null` (page calls `notFound()`).

---

## Component Changes

### `AuctionGallery`

- Prop: `images: string[]` → `images: AuctionImageResponse[]`
- Extract `imageUrl` for the main view, `thumbnailUrl` for the thumbnail strip (fall back to `imageUrl` if `thumbnailUrl` is absent)

### `ItemSpecs`

- Prop: `auction: Auction` → `auction: AuctionDetail`
- **Remove** rows: `Condition`, `Reserve`
- **Add** rows: `Bid increment` (mono), `Deposit required` (mono)
- **Keep** rows: `Category`, `Item ID`, `Starting price`, `Buy now price` (conditional)
- New row order (left column): Category · Item ID · Starting price
- New row order (right column): Bid increment · Deposit required · Buy now price (if set)

### `SellerCard`

- Prop: `seller: AuctionSeller` → `seller: AuctionDetailSeller | null`
- When `null`: render nothing (section omitted from page)
- When present:
  - Show `UserAvatar` (name + avatarUrl)
  - Show seller name
  - Show "✓ Verified Seller" green badge (always, no condition)
  - Keep "View profile" button
  - **Remove** star rating and auction count

### `BidPanelLive`

- Prop: `auction: Auction` → `auction: AuctionDetail`
- `BidForm` receives `minIncrement={auction.bidIncrement}` instead of hardcoded `100`
- Show hint text below bid form: `Min increment: {formatCurrency(auction.bidIncrement)}`
- **Add** Buy Now block below the bid form, rendered when `auction.buyNowPrice !== undefined`:
  ```
  ┌─ green-tinted border box ──────────────────────────┐
  │  OR BUY IT NOW             $3,500.00 (mono)        │
  │  [      Buy Now →      ] (green filled button)     │
  └────────────────────────────────────────────────────┘
  ```
- Remove `WatchingFooter`. Replace with a slim footer row (Share · Save links, no watcher count)

### `BidPanelUpcoming`

- Prop: `auction: Auction` → `auction: AuctionDetail`
- Remove `WatchingFooter`. Same slim Share/Save footer.

### `BidPanelEnded`

- Prop: `auction: Auction` → `auction: AuctionDetail`
- Remove `WatchingFooter`. Same slim Share/Save footer.
- `categoryId` link (`/auctions?category=...`) uses `auction.categoryId` — unchanged.

### `BidPanel`

- Prop: `auction: Auction` → `auction: AuctionDetail`
- Logic unchanged — status routing to sub-panels stays the same.

### `WatchingFooter`

- **Delete** the component entirely once all three BidPanel variants are updated.

---

## Page (`app/auctions/[id]/page.tsx`)

- **Remove** `CATEGORY_LABELS` map — use `auction.categoryName` from the mapper
- **Remove** `getAuctionStatus` computation — status comes from the API, already mapped to `AuctionStatus` by the mapper
- **Remove** `auctionWithStatus` spread — pass `auction` directly to `BidPanel`
- Breadcrumb and meta row use `auction.categoryName`
- Meta row date uses `auction.startsAt` (same field, renamed)
- `AuctionGallery` receives `auction.images` (typed objects, not strings)
- `SellerCard` receives `auction.seller` (nullable — component handles null itself)
- Bid history section is out of scope for this redesign

---

## Edge Cases

| Case | Handling |
|---|---|
| `seller` is null (user-service unavailable) | `SellerCard` renders nothing |
| `buyNowPrice` absent | Buy Now block not rendered in `BidPanelLive` |
| `images` array empty | `AuctionGallery` existing empty-state (image icon placeholder) — unchanged |
| API returns non-200 | `getAuctionById` returns `null` → page calls `notFound()` |
| `extensionCount > 0` | Surfaced via `extensionCount` and `originalEndAt` on `AuctionDetail` — not yet rendered in UI (future scope) |

---

## Out of Scope

- Bid history section (explicitly excluded)
- Anti-sniping indicator UI (`extensionCount` / `originalEndAt` fields are mapped but not displayed)
- Buy Now confirmation modal / API call wiring (button renders; action is a placeholder)
- Real-time WebSocket integration changes
