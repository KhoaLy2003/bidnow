# Design Spec: Buy It Now

**Date:** 2026-10-14
**Epic:** Auction Service — Epic 4 (Buy It Now), issues 4.1 and 4.2
**Status:** Awaiting review

---

## Overview

A seller can already set an optional `buy_now_price` on an auction, and the detail page already renders a "Buy It Now" block, but the button does nothing and no purchase path exists. This spec delivers the purchase: an eligible buyer can end a live auction immediately at the seller's fixed price and become its winner. The buyer then follows the normal winner flow (48-hour payment hold).

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | How does the buyer pay? | Clicking Buy It Now **locks the auction's deposit** (same call as a first bid), closes the auction with the buyer as winner at `buyNowPrice`, and the existing **48 h payment hold** takes over. The deposit counts toward the price and is forfeited if the buyer never pays. |
| 2 | When is Buy It Now available? | While the auction is `ACTIVE`, not past `end_time`, and `current_price < buy_now_price`. Placed bids do **not** remove it. |
| 3 | Who orchestrates? | **Approach A:** bidding-service exposes the public endpoint and orchestrates (mirrors `BidService.placeBid`); auction-service owns the authoritative, row-locked close. |
| 4 | Existing bidders | Treated as losers: bids stay in history, deposits are refunded by the existing ended-event consumer. If the current leader buys, their already-locked deposit is applied to the price. |
| 5 | Payment UI | Out of scope. The "Proceed to payment" / "Contact seller" buttons in `BidPanelEnded` remain stubs (true for every win today). |

---

## Scope

### In Scope

- `POST /api/v1/bids/buy-now` (bidding-service, authenticated)
- `POST /api/v1/internal/auctions/{id}/buy-now` (auction-service, internal)
- `buyNowPrice` added to the bid-context contract on both sides
- Frontend: confirmation dialog, service call, error mapping, live-state reducer, panel visibility rule
- Tests and documentation updates listed below

### Out of Scope

- Wiring payment UI (`BidPanelEnded` buttons)
- Buy-It-Now-specific notification copy (the normal "You won" is sent)
- Per-seller toggles, reserve rules, or any change to how `buyNowPrice` is validated at create/update
- Making the endpoint idempotent across retries (see Known Limitations)
- Any database schema change

---

## Architecture

### Flow

```
Buyer ──POST /api/v1/bids/buy-now {auctionId}──► Gateway (/api/v1/bids/**, auth required)
                                                    │
                                              bidding-service
                                       1. pre-validate (cached BidContext)
                                       2. DepositGate.ensureLocked ──► wallet-service (lock deposit, idempotent)
                                       3. POST /internal/auctions/{id}/buy-now ──► auction-service
                                                    │                         (row lock: findByIdForUpdate)
                                                    │                         re-validate, set winner/price,
                                                    │                         COMPLETED, history row,
                                                    │                         publish AuctionEndedEvent
                                                    │                         (closureSource=BUY_NOW) after commit
                                       4. evict cached context
                                       5. 201 {auctionId, price, endedAt}
```

### Reused unchanged

- **wallet-service** `auction-ended-topic` consumer: refunds every `LOCKED` deposit except the winner's (`AUCTION_LOST`) and creates the 48 h payment hold at `winningBidAmount` (= `buyNowPrice`). `PaymentEvent REQUIRED` follows.
- **media-service**: realtime `AUCTION_ENDED` broadcast and won / lost / unsold notifications. Nothing consumes `closureSource` today, so `BUY_NOW` needs no handler change.
- **bidding-service** `AuctionLifecycleConsumer` already evicts the cached context on `auction-ended-topic`.
- The scheduled closure job and the ending-soon jobs no-op on a non-`ACTIVE` auction (`AuctionClosureService.close`, `AuctionEndingSoonService`).

---

## Backend Design

### bidding-service

**Endpoint:** `POST /api/v1/bids/buy-now`, header `X-User-Id`, body `{ "auctionId": "<uuid>" }`.
`/api/v1/bids/**` is already routed by the gateway, and only `GET /api/v1/bids/auction/*` is public, so the new route requires authentication with no gateway or `SecurityConfig` change.

**New components**
- `BuyNowService.buyNow(buyerId, request)` — orchestration, in the style of `BidService`.
- `PlaceBid`-style DTOs: `BuyNowRequest { auctionId }` (validated non-null), `BuyNowResponse { auctionId, price, endedAt }`.
- `AuctionServiceClient` (Feign): new `buyNow(UUID auctionId, BuyNowCommand)` and `AuctionBidGateway.buyNow(...)` wrapper that translates downstream errors like `apply` does.
- `BidController`: `POST /buy-now` mapped to `201`.

**Changes**
- `BidContext` gains `BigDecimal buyNowPrice` (nullable). The cache stores JSON with `ignoreUnknown`, so existing entries deserialize with `buyNowPrice = null` until refreshed. A null price may be a stale entry rather than "no buy-now price", so `BuyNowService` refreshes the context once on a null price or a 409 (same approach as `BidService.preValidateWithRefresh`). If the price is still null after the refresh, the request is rejected with `BUY_NOW_NOT_AVAILABLE`.
- `BidValidationService` gains `preValidateBuyNow(ctx, buyerId, now)`:
  1. status `ACTIVE` and `now < endTime` else `AUCTION_NOT_OPEN` (409)
  2. buyer is not the seller else `BID_OWN_AUCTION` (403)
  3. `buyNowPrice != null` and `currentPrice < buyNowPrice` else `BUY_NOW_NOT_AVAILABLE` (409)

**Orchestration order** (`BuyNowService.buyNow`)
1. Load context (cache) and pre-validate; refresh once on a conflict or an unknown price.
2. `depositGate.ensureLocked(buyerId, ctx)`.
3. Call auction-service buy-now. On any failure, evict the context and rethrow the mapped exception.
4. Evict the cached context and return the response. There is no DB write in bidding-service: a buy-now is not recorded as a bid.

### auction-service

**Endpoint:** `POST /api/v1/internal/auctions/{id}/buy-now`, body `{ "buyerId": "<uuid>" }`, in `AuctionInternalController`. Not reachable through the gateway (`/internal/` is blocked).

**Logic:** `AuctionBidService.applyBuyNow(auctionId, request)` (`@Transactional`):
1. `findByIdForUpdate(auctionId)`; absent → `AUCTION_NOT_FOUND` (404). This serializes with apply-bid, scheduled closure, admin cancel and force-close.
2. Reject with `AUCTION_NOT_OPEN` (409) if `status != ACTIVE` or `now >= endTime`.
3. Reject with `BID_OWN_AUCTION` (403) if `buyerId == sellerId`.
4. Reject with `BUY_NOW_NOT_AVAILABLE` (409) if `buyNowPrice == null` or `currentPrice >= buyNowPrice`.
5. Set `currentPrice = buyNowPrice`, `currentWinnerId = winnerId = buyerId`, `status = COMPLETED`, `completedAt = now`. Leave `totalBids` unchanged.
6. Save an `AuctionStatusHistory` row `ACTIVE → COMPLETED`, `triggeredBy = buyerId`, reason `Buy It Now`.
7. After commit, publish `AuctionEndedEvent` through `AuctionKafkaProducer` with `winnerId = buyerId`, `winningBidAmount = buyNowPrice`, `loserIds = List.of()`, real `totalBids`, `endedAt = now`, `closureSource = "BUY_NOW"`.
8. Return `BuyNowResponse { auctionId, price, endedAt }`.

`BidContextResponse` gains `buyNowPrice` (field names must stay identical to bidding's `BidContext`).

### Error contract

| Code | HTTP | When |
|---|---|---|
| `AUCTION_NOT_FOUND` | 404 | unknown auction |
| `AUCTION_NOT_OPEN` | 409 | not `ACTIVE`, or past end time |
| `BID_OWN_AUCTION` | 403 | buyer is the seller |
| `BUY_NOW_NOT_AVAILABLE` (new) | 409 | no buy-now price, or `currentPrice >= buyNowPrice` |
| `BID_INSUFFICIENT_BALANCE`, `WALLET_NOT_ACTIVE`, `WALLET_NOT_FOUND`, `DEPOSIT_LOCK_CLOSED` | 400/403/404/409 | deposit gate, mapped as for bids |
| `SERVICE_UNAVAILABLE` | 503 | downstream unreachable |

### Race and failure behaviour

- Deposit lock succeeds, then the close loses the row lock to a bid, a scheduled close, a cancel, or another buyer: the buyer gets `AUCTION_NOT_OPEN` or `BUY_NOW_NOT_AVAILABLE`. Their deposit remains locked as a registration and is refunded when the (winner-excluded) ended-event consumer runs, provided the lock landed before that sweep (see Known Limitations). No new compensation code.
- If the auction was cancelled, the existing cancelled-event consumer refunds all locks.
- Kafka publishing is best-effort after commit, same as normal closure (see `AuctionClosureService.close`). A lost event leaves the auction `COMPLETED` without a payment hold. This is an existing risk, not introduced here.

---

## Frontend Design

All amounts are dollars end to end (`lib/money.ts`, `formatCurrency`).

**Service and types**
- `types/api/bid.api.ts`: `BuyNowRequest { auctionId }`, `BuyNowResponse { auctionId, price, endedAt }`; `BUY_NOW_NOT_AVAILABLE` added to `BidErrorCode`.
- `services/bid.service.ts`: `bidService.buyNow(auctionId)` → `POST /api/v1/bids/buy-now`, using `apiFetch` and `BidRequestError`.

**Errors** (`lib/bid-errors.ts`)
- New failure kind `buy-now-unavailable`: "Buy It Now is no longer available — the price has been reached or the auction has ended." `disablesForm: true`.
- `not-open`, `own-auction`, `wallet-problem`, `insufficient-balance`, `unavailable`, `unauthenticated` reused as is.

**State** (`lib/realtime/auction-live.ts`, `store/auctionStore.ts`)
- `applyOwnBuyNow(state, response, me)`: guards on matching `auctionId`, sets `status = Closed`, `winnerId = me.id`, `currentBid = response.price` (via the existing max-dollars helper). Idempotent with the following realtime `AUCTION_ENDED` (`applyAuctionEnded`).
- `auctionStore.ownBuyNowCompleted(response, me)` calls it.

**UI**
- New `components/auction/BuyNowButton.tsx`, extracted from the Buy Now block in `BidPanelLive`, using the shadcn `Dialog` (`components/ui/dialog.tsx`):
  - Click opens a confirmation dialog: item title, buy-now price, deposit to lock, and the text "This ends the auction now. You then have 48 hours to pay the remaining balance; your deposit is forfeited if you don't."
  - Cancel / Confirm. While the request runs the dialog cannot be dismissed and Confirm shows a spinner.
  - Failure: inline message via `describeBidFailure`, with the wallet top-up link where it applies. A form-disabling failure disables Confirm. `unauthenticated` routes to `/login`.
  - Success: `ownBuyNowCompleted`, wallet refetch (the deposit is now locked), toast "You won {title} for {price}", close the dialog.
  - A logged-out viewer clicking the trigger is routed to `/login`, as `BidForm` does.
- `BidPanelLive`: render the block only when `buyNowPrice !== undefined` **and** live `currentBid < buyNowPrice` (compared through `lib/money.ts`). Today it shows whenever a price exists.
- Other viewers receive `AUCTION_ENDED` over the socket; `BidPanel` already switches them to the ended panel. The buyer's panel flips to "Won" immediately from the HTTP response.

No mapper changes: `AuctionDetail.buyNowPrice` is already `number | undefined`.

---

## Testing

**Backend (JUnit 5 + Mockito, existing style)**
- `AuctionBidServiceTest` (or `AuctionBuyNowServiceTest`): happy path (winner, price, `COMPLETED`, history row, event fields incl. `closureSource=BUY_NOW` and `winningBidAmount=buyNowPrice`); rejects non-`ACTIVE`, past `endTime`, null `buyNowPrice`, `currentPrice >= buyNowPrice`, buyer == seller; the current leader can buy; a second call after success is rejected.
- `AuctionInternalControllerTest`: new route, status codes, validation.
- `AuctionClosureServiceTest`: scheduled close after a buy-now is a no-op.
- `BuyNowServiceTest` (bidding): order of pre-validate → deposit gate → auction call → eviction; exception mapping; cache eviction on failure.
- `BidValidationServiceTest`: buy-now rules. `BidContextJsonTest`: `buyNowPrice` round-trip and old cache entries without it.
- `BidControllerTest`, `BidControllerSecurityTest`: new route and authentication requirement.
- `place-bid.feature`: one BDD scenario for a successful buy-now. No BDD for the internal auction endpoint (the internal-bid-api scenarios were removed in `dc1a3c7` as failing).
- Check that wallet's `AuctionLifecycleEventConsumerTest` already covers the winner-keeps-lock / others-refunded shape; add a case if not.

**Frontend (Vitest, pure modules)**
- `applyOwnBuyNow`, including replay with the same `AUCTION_ENDED`.
- `classifyBidError` / `describeBidFailure` for `BUY_NOW_NOT_AVAILABLE`.
- `bidService.buyNow` request shape and error mapping.
- `BuyNowButton` and the dialog are verified manually in the browser, as `BidForm` is today.

---

## Documentation Updates

Required by `AGENTS.md` (API surface changes):
- `docs/architecture.md`: both endpoints in the internal-API and Bidding Service tables; `BUY_NOW_NOT_AVAILABLE`; `bid-context` gains `buyNowPrice`.
- `docs/diagrams/04-auction-closure-flow.md`: Buy It Now as a third closure source beside `SCHEDULER` and `ADMIN`.
- `docs/business-clarifications.md`: decisions 1, 2 and 4 above.
- `docs/functional.md`: align the Buy It Now line.
- No change to `docs/database/bidding-service-schema.md` (no bid row is written) or to any schema doc.

---

## Compatibility and Rollout

- No migration, no schema change.
- Deploy order: auction-service (new internal endpoint and `bid-context` field) → bidding-service → frontend. Until bidding is deployed the frontend call gets a 404 and shows the generic error; the button is inert today anyway, so the order is safe.
- Stale cached contexts without `buyNowPrice` are handled by the refresh-once rule.

---

## Known Limitations (deliberate)

- A retry after a timed-out request that actually succeeded returns `AUCTION_NOT_OPEN`. The realtime `AUCTION_ENDED` still flips the UI to "Won".
- The won notification and email use the normal win copy and do not mention Buy It Now.
- Event publishing is best-effort after commit, as for every closure path.
- A buyer whose purchase is rejected after the deposit lock (lost race) is refunded only if the lock commits before wallet-service's auction-ended / auction-cancelled sweep; a lock that lands after the sweep stays LOCKED with no automated recovery (the same race class already exists on the bid path; BuyNowService logs a WARN trail). Closing it needs wallet-service to refuse or sweep late locks - a follow-up outside this feature.
- Payment UI is not part of this feature.
