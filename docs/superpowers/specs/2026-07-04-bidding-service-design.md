# Bidding Service — Bid Placement & Real-Time Auction Bidding (Design)

**Epic:** [KhoaLy2003/bidnow#120](https://github.com/KhoaLy2003/bidnow/issues/120)
**Status:** Draft (2026-09-29). Supersedes the design implied by the original story texts #121–#125 where noted.
**Related:** WALLET-303 deposit lock (`2026-09-28-wallet-deposit-lock-design.md`), auction closure (`2026-06-21-auction-closure-design.md`), diagram `docs/diagrams/03-bidding-antisniping-flow.md`.

---

## 1. Goals

- Accept manual bids with full validation and prove the bidder's financial commitment through the implicit deposit lock on their first bid (WALLET-303).
- **Never accept two bids at the same price level, and never accept a bid on a closed auction.** This must hold across multiple bidding-service instances and against `AuctionClosureJob`.
- Apply anti-sniping auto-extension atomically with bid acceptance.
- Provide bid history, meaning the auction's history and "my bids".
- Push live price, extension and end-of-auction updates to browsers.

**Out of scope:** auto-bid/proxy bidding (Phase 2), Buy Now, outbid email batching and ending-soon alerts (#19), `AuctionEndedEvent.loserIds` (wallet derives losers from `deposit_locks`).

## 2. Key decision: auction-service is the source of truth for price

### Problem with the original design

The original stories validated bids against a Redis copy of the auction context. They then updated auction-service's `current_price`, `current_winner_id` and `total_bids` **asynchronously** through a `bid-placed-topic` consumer (#125, scenario 3). `AuctionClosureService.close()` picks the winner from exactly those columns. That leaves three problems:

1. **Wrong winner.** A bid accepted shortly before `endTime` may not have been consumed yet when the closure job fires. The auction then closes with the previous winner, and wallet creates the payment hold for the wrong user.
2. **Bid after close.** The Redis context still says `ACTIVE` after closure commits, so a bid can be accepted on an auction that has already closed.
3. **Double accept.** Two bidding-service instances can validate two bids against the same cached `currentPrice` and accept both.

### Decision: synchronous, row-locked apply-bid

Auction-service exposes one internal command, `POST /api/v1/internal/auctions/{id}/bids`, which runs in a single transaction under `SELECT … FOR UPDATE` on the `auction_items` row. It does four things:

1. It re-checks `status == ACTIVE`, `now < endTime` and bidder ≠ seller.
2. It re-checks the minimum amount: `amount >= currentPrice` when `totalBids == 0` (the first bid may equal the starting price, since `current_price` is initialised to `starting_price`), otherwise `amount >= currentPrice + bidIncrement`.
3. It updates `current_price`, `current_winner_id`, `total_bids` and `last_bid_id`.
4. If `endTime - now < antiSnipeWindow`, it extends `end_time` by `extensionDuration`, increments `extension_count` and writes an `auction_extensions` row.

It returns the new state. `AuctionClosureService.close()` and both cancel paths (seller and admin) take the **same row lock**. Closure and bid acceptance are therefore strictly serialized. Closure also re-checks `now >= endTime` under the lock, and if the auction was extended it reschedules itself instead of closing. Closure job IDs are name-based on (auctionId, closeAt) so the deferred job for a new end time is a distinct JobRunr job; the stale job for the old end time finds now < endTime and reschedules (idempotent). Closure jobs fire at end_time + `auction.closure.grace-seconds` (20 s) because JobRunr enqueues scheduled jobs up to one poll interval (15 s) early; the job ID stays keyed on the real end time.

This one call **replaces** the separate `extend` endpoint (original BID-104) and the `bid-placed-topic` consumer in auction-service (original #125, scenario 3). Anti-snipe configuration therefore moves into auction-service. Bidding-service's Redis context remains as a **fast pre-filter** that rejects obviously invalid bids without a network hop. It is never the authority.

## 3. Bid placement flow

```
POST /api/v1/bids  { auctionId, amount }         (bidderId = X-User-Id, never from the body)
 1. ctx = AuctionContextCache.get(auctionId)       Redis bidding:auction:{id}:context, miss → Feign GET bid-context
 2. BidValidationService.preValidate(ctx, bidderId, amount)
      seller → 403 BID_OWN_AUCTION · not ACTIVE / now ≥ endTime → 409 AUCTION_NOT_OPEN · below min → 400 BID_TOO_LOW
 3. DepositGate.ensureLocked(bidderId, auctionId, ctx.depositAmount)
      Redis flag bidding:deposit:{auctionId}:{userId} present → skip
      else POST /api/v1/internal/wallet/deposit-lock (idempotent; returns alreadyLocked) → set flag
      INSUFFICIENT_BALANCE → 403 BID_INSUFFICIENT_BALANCE (errors: availableBalance, required)
      WALLET_NOT_ACTIVE / WALLET_NOT_FOUND → 403 · DEPOSIT_LOCK_CLOSED → 409 · 5xx/timeout → 503 SERVICE_UNAVAILABLE
 4. @Transactional:
      bidId = UUID.randomUUID(); insert bids row
      result = AuctionServiceClient.applyBid(auctionId, { bidId, bidderId, amount })   ← authoritative, row-locked
        409 AUCTION_NOT_OPEN / 400 BID_TOO_LOW → rollback, evict ctx, rethrow · 5xx/timeout → rollback, 503
      bid.isAntiSnipingTriggered = result.extended
      afterCommit: publish BidPlacedEvent; write result state into the Redis ctx
 5. 201 Created  PlaceBidResponse { bidId, auctionId, amount, placedAt, currentPrice, totalBids, endTime, extended }
```

- **Implementation notes (BID-102):** `bidTime`/`placedAt` come from the service `Clock` (UTC). The Kafka producer uses `max.block.ms: 2000` so an unreachable broker cannot stall a bid. A replayed `bidId` cannot reach the `bids` INSERT because `bidId` is generated per request and apply-bid is never retried — no duplicate-key handling is needed. auction-service publishes its cancel / force-close / activation events after commit so a stalled broker never extends the auction row lock.

- **Why the lock comes before apply-bid:** a bid must never become the winner without backing funds. If apply-bid then rejects the bid (for example, the bidder was outbid in the same instant), the deposit stays `LOCKED`. That is acceptable, because the lock *is* the auction registration and it is refunded at auction end (WALLET-304).
- **Why the GET deposit-lock pre-check was dropped:** the POST is idempotent (`alreadyLocked`), and the Redis flag removes the wallet round-trip on every later bid. That saves one call compared with the original BID-102 text.
- **Commit failure after a successful apply-bid:** the window is tiny (local insert already done, so only commit can fail). auction-service stores `last_bid_id`. Log `CRITICAL` with the bidId so the two services can be reconciled.
- **Timeouts:** Feign connect 1s / read 2s for auction and wallet. A DB connection is held across the apply-bid call, so the timeouts must stay tight.

## 4. Contracts

### auction-service (internal, `/api/v1/internal/auctions/**`, permitAll, not routed by the gateway)

```
GET  /api/v1/internal/auctions/{id}/bid-context
  200 BidContextResponse { auctionId, title, sellerId, status, currentPrice, bidIncrement, depositAmount,
                           currentWinnerId, totalBids, endTime (OffsetDateTime) }
  404 AUCTION_NOT_FOUND

POST /api/v1/internal/auctions/{id}/bids
  body ApplyBidRequest { bidId (UUID), bidderId (UUID), amount (BigDecimal > 0) }
  200 ApplyBidResponse { auctionId, currentPrice, currentWinnerId, previousWinnerId, totalBids,
                         endTime, extended (boolean), extensionCount }
  400 BID_TOO_LOW (errors: minimumBid) · 403 BID_OWN_AUCTION · 404 AUCTION_NOT_FOUND · 409 AUCTION_NOT_OPEN
  Idempotent on bidId: if last_bid_id == bidId, return the current state unchanged.
```

Config (auction-service `application.yml`):

```yaml
auction:
  anti-snipe:
    window-seconds: 120
    extension-seconds: 300
```

### bidding-service (public, via gateway `/api/v1/bids/**`)

```
POST /api/v1/bids                                   body { auctionId, amount }   → 201 PlaceBidResponse
GET  /api/v1/bids/auction/{auctionId}?page=&size=   → 200 PageResponse<BidHistoryResponse>   (public, no auth)
GET  /api/v1/bids/auction/{auctionId}/my-bids?page=&size=  → 200 PageResponse<BidHistoryResponse> (auth)
BidHistoryResponse { id, auctionId, bidderId, bidderName, bidderAvatarUrl, amount, placedAt, isAutoBid, isAntiSnipingTriggered }
```

Error codes (in `BiddingErrorCodes`): `BID_TOO_LOW` 400, `BID_OWN_AUCTION` 403, `BID_INSUFFICIENT_BALANCE` 403, `WALLET_NOT_ACTIVE` 403, `WALLET_NOT_FOUND` 403, `AUCTION_NOT_FOUND` 404, `AUCTION_NOT_OPEN` 409, `DEPOSIT_LOCK_CLOSED` 409, `SERVICE_UNAVAILABLE` 503.

### user-service (internal)

```
POST /api/v1/users/internal/summaries   body { userIds: [UUID…] (max 100) } → 200 List<UserSummaryResponse>
```

The batch endpoint prevents N+1 calls from bid history. Unknown IDs are omitted, and the caller falls back to "Unknown bidder".

### Events (`common/dto/event`)

- `BidPlacedEvent` on `bid-placed-topic`, keyed by auctionId, published by bidding-service after commit. **Additive fields:** `bidId`, `totalBids`, `endTime` (the auction's end after this bid). Existing fields are unchanged: `bidderName` is resolved through the cached user summary, `auctionTitle` comes from the context, and `previousHighestBidderId` comes from `ApplyBidResponse.previousWinnerId`.
- `AuctionExtendedEvent` on `auction-extended-topic` (new), published by auction-service after commit, with fields `{ auctionId, auctionTitle, previousEndTime, newEndTime, extensionCount, triggeredByBidId, triggeredByUserId }`.
- Consumed by bidding-service: `auction-ended-topic`, `auction-cancelled-topic` and `auction-extended-topic`, which evict/refresh `bidding:auction:{id}:context`.

## 5. Data (bidding_db)

```sql
bids (id UUID PK, auction_id UUID NOT NULL, bidder_id UUID NOT NULL, amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
      is_auto_bid BOOLEAN NOT NULL DEFAULT false, is_anti_sniping_triggered BOOLEAN NOT NULL DEFAULT false,
      created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)
INDEX (auction_id, created_at DESC); INDEX (auction_id, amount DESC); INDEX (bidder_id, auction_id, created_at DESC)
```

Timestamps are `TIMESTAMP` (not `TIMESTAMPTZ`) to match the shared `BaseEntity` (`LocalDateTime`) used by every service. `id` has no DB default — bidding-service assigns it.

auction_db migration `004-bid-tracking.sql`: `ALTER TABLE auction_items ADD COLUMN last_bid_id UUID`.

Redis keys (bidding-service):
- `bidding:auction:{id}:context` holds the JSON `BidContext` with a 10 min TTL. It is overwritten after each accepted bid and evicted on lifecycle events and on 409/400 from apply-bid. A pre-validation `AUCTION_NOT_OPEN` from a cached context is never returned directly: the context is refreshed from auction-service once and re-validated (a cached SCHEDULED status or pre-extension `endTime` may be stale).
- `bidding:deposit:{auctionId}:{userId}` holds `"1"` and expires 24h after the auction's `endTime`.
- `bidding:user:{id}:summary` holds the JSON user summary with a 10 min TTL.

## 6. Real-time delivery (STOMP via media-service)

- media-service already runs a STOMP broker at `/ws-notifications` (SockJS). A new `AuctionRealtimeBroadcaster` consumes `bid-placed-topic`, `auction-extended-topic`, `auction-ended-topic` and `auction-cancelled-topic`. It uses a **per-instance consumer group** (`media-realtime-${random.uuid}`), so every media instance forwards to its own connected clients.
- Destination `/topic/auctions/{auctionId}` carries the message `{ type: "BID_PLACED" | "AUCTION_EXTENDED" | "AUCTION_ENDED" | "AUCTION_CANCELLED", auctionId, payload }`.
  - `BID_PLACED`: `{ bidId, bidderId, bidderName, amount, placedAt, totalBids, endTime, antiSnipingTriggered }`
  - `AUCTION_EXTENDED`: `{ previousEndTime, newEndTime, extensionCount }`
  - `AUCTION_ENDED`: `{ winnerId, finalPrice, endedAt }`
  - `AUCTION_CANCELLED`: `{ reason }`
- The outbid alert (`OUTBID`: `{ auctionTitle, currentPrice, newLeaderName }`) goes to `/user/{previousHighestBidderId}/queue/notifications`. This needs a STOMP `Principal` from the `X-User-Id` header injected by the gateway.
- Gateway: a single route `/ws-notifications/**` → `lb://media-service`, auto-upgraded to WebSocket for upgrade requests (covers the SockJS HTTP fallback too).
- Frontend: `useAuctionSocket` replaces socket.io-client with `@stomp/stompjs` + `sockjs-client` and maps the message types onto the existing `auctionStore` actions.

## 7. Failure modes

| Failure | Behaviour |
|---|---|
| wallet-service down | 503, bid rejected, nothing persisted |
| auction-service down (context miss or apply-bid) | 503, bid rejected, local tx rolled back |
| Kafka down after commit | Bid is accepted and the price is correct in auction-service. Real-time push is missed, so log `CRITICAL`. Clients recover on page reload. |
| Redis down | Treat as a cache miss and fall through to Feign. The deposit flag is missing, so call the idempotent wallet POST. Bids still work, just slower. |
| Closure job fires on an extended auction | `close()` sees `now < endTime` under the lock and reschedules the job for the new `endTime` |

## 8. Changes to existing stories

| Story | Change |
|---|---|
| #121 BID-101 | `bidderId` comes from `X-User-Id`, not the body. The first-bid minimum is `currentPrice`. Add `title` to the context. Add the Redis, OpenFeign and Kafka dependencies. |
| #122 BID-102 | Call the auction apply-bid instead of relying on the cache. Drop the GET deposit-lock pre-check in favour of the Redis flag plus the idempotent POST. Add the lifecycle cache-eviction consumer. |
| #123 BID-103 | Add the user-service batch summaries endpoint (the open question is resolved). History GET is public. |
| #124 BID-104 | The extension happens inside auction-service's apply-bid, and config moves to `auction.anti-snipe.*`. Add closure rescheduling and `AuctionExtendedEvent`. Bidding only persists the flag. Scenario 3 (extension fails after acceptance) can no longer happen. |
| #125 AUC | Replace the `extend` endpoint and `bid-placed-topic` consumer with the row-locked apply-bid. Closure and both cancel paths take the row lock. |
| **New** RT-101 | Real-time STOMP broadcast in media-service plus the gateway WebSocket route |
| **New** FE-101 | Frontend: real bid submit, history API, STOMP hook, error UX |
