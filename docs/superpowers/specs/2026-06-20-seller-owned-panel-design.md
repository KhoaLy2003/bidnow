# Seller-Owned Auction Panel — Design Spec

**Date:** 2026-06-20
**Status:** Approved

## Problem

When a seller navigates to the public detail page of their own auction (`/auctions/[id]`), the full bid/buy panel is shown with no restriction. Sellers should not be able to bid on their own listings. The UI should make this clear and give them a direct path to manage the auction instead.

## Scope

Frontend only. Backend self-bid enforcement is out of scope for this iteration (to be added later by the backend team).

## Approach

Client-side ownership check inside `BidPanel` — the existing `AuctionDetail.seller.id` field is compared against `useAuthStore().user?.id`. No server component changes, no API contract changes.

## Frontend Changes

### `BidPanel.tsx`

- Import `useAuthStore`.
- Replace the `MOCK_CURRENT_USER_ID` winning-bid comparison with `useAuthStore().user?.id === auction.currentWinnerId`.
- Compute `isOwner = !!user && !!auction.seller && user.id === auction.seller.id`.
- If `isOwner` is `true`, render `<SellerOwnedPanel auctionId={auction.id} />` and return early — before any status-based routing.
- Remove the `MOCK_CURRENT_USER_ID` import.

### New `SellerOwnedPanel.tsx`

Located at `components/auction/SellerOwnedPanel.tsx`.

Visual structure (same outer card shell as sibling panels):

```
┌─────────────────────────────────────┐
│  🏷  Your auction                   │
│  Sellers can't bid on their own     │
│  auctions.                          │
│                                     │
│  [ Manage auction → ]               │
└─────────────────────────────────────┘
```

- Outer: `rounded-xl border bg-card overflow-hidden flex flex-col`
- Icon: `Tag` from lucide-react
- Heading: "Your auction" (`font-medium text-sm`)
- Subtext: "Sellers can't bid on their own auctions." (`text-xs text-muted-foreground`)
- Button: full-width `Button variant="outline"` rendered as a `Link` to `/seller/auctions/[id]/manage`

### Files changed

| File | Change |
|---|---|
| `components/auction/BidPanel.tsx` | Ownership check + early return |
| `components/auction/SellerOwnedPanel.tsx` | New component |

### Files unchanged

- `app/auctions/[id]/page.tsx` — server component, no changes
- `BidPanelLive.tsx`, `BidPanelUpcoming.tsx`, `BidPanelEnded.tsx` — unchanged
- All types and services — unchanged

## Edge Cases

- `auction.seller` is `null` (field is `AuctionDetailSeller | null`) — the `!!auction.seller` guard prevents a false positive; unauthenticated users also see the normal panel.
- User is not logged in (`user` is `null`) — `isOwner` is `false`, normal panel shown.

## Out of Scope

- Backend enforcement (403 on bid placement when `bidderId === sellerId`) — deferred.
- Admin users viewing seller auctions — no special handling needed.
