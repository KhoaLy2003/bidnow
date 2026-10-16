# My Active Bids — Design

## Goal
A signed-in user opens `/my-bids` and sees every `ACTIVE` auction they have bid on, each marked **Winning** or **Outbid**, with their highest bid, the current price, bid count and a live countdown. Tabs: All / Winning / Outbid (with counts). Sorted by ending soonest. Outbid rows offer "Bid again".

Out of scope: ended auctions (Won/Lost history), per-row sockets, auto-bid management.

## Boundaries
- `bidding-service` owns `bids` (`bidding_db`). `auction-service` owns title, image, price, winner, status and end time (`auction_db`). No cross-database queries.
- The listing is composed in `bidding-service`, which reads auction data through a new internal batch endpoint on `auction-service`.

## API

### Public — bidding-service
`GET /api/v1/bids/me/active` — authenticated (`X-User-Id` injected by the gateway). It must not live under `/api/v1/bids/auction/*`, which the gateway treats as public. No gateway route change is needed.

```json
{
  "data": {
    "items": [{
      "auctionId": "uuid", "title": "…", "primaryImageUrl": "…",
      "currentPrice": 1250.00, "myHighestBid": 1200.00, "totalBids": 14,
      "endTime": "2026-10-17T10:00:00Z", "lastBidAt": "2026-10-16T09:12:00Z",
      "isWinning": false
    }],
    "counts": { "all": 5, "winning": 3, "outbid": 2 }
  }
}
```

Wrapped in `BaseResponse`. **Deliberately not paginated**: a user's concurrently active bids are small and bounded, and tabs filter client-side. The response is capped at 100 items, sorted by `endTime` ascending. Errors: 401 without `X-User-Id`; 503 `SERVICE_UNAVAILABLE` if auction-service is unreachable.

### Internal — auction-service
`POST /api/v1/internal/auctions/bid-summaries`, body `{ "auctionIds": [uuid, …≤100] }`. Returns only `ACTIVE`, non-deleted auctions: `id, title, primaryImageUrl, currentPrice, currentWinnerId, totalBids, endTime`. Internal paths are blocked at the gateway.

## Data flow
1. bidding-service queries the bidder's distinct auction ids with `MAX(amount)` and `MAX(createdAt)`, newest first, limited to the 300 most recent auctions. This uses `idx_bids_bidder_auction_created`; no migration.
2. The ids are chunked by 100 and sent to the internal endpoint. Auctions that are not `ACTIVE` simply do not come back.
3. Join on auction id. `isWinning = currentWinnerId == callerId`. Sort, cap, count.

The 300-auction scan cap means a user whose oldest auctions beyond 300 are still active would miss them. That is not realistic and is accepted.

## Frontend
- Layers: `types/api` → `types/mappers` → `types/ui`; `bid.service.getMyActiveBids()`.
- Pure logic in `lib/my-bids.ts` (filter, sort, counts) with unit tests.
- `hooks/useMyActiveBids`: fetch on mount, poll every 30 s while the tab is visible, refetch on outbid/won notifications, keep stale data and show a retry banner on error.
- Components in `components/bids/`; the page replaces the mock in `app/(dashboard)/my-bids/page.tsx`.
- UI prototyped in Claude Design first; see `docs/prompt/my-bids-design-prompt.md`.

## Testing
Unit tests on both services and the controller, a BDD feature in bidding-service, and vitest tests for the mapper, service and `lib/my-bids`.
