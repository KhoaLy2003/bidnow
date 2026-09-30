# Bidding & Anti-sniping Flow

Source of truth: `docs/superpowers/specs/2026-07-04-bidding-service-design.md` §3 (placement) and §6 (real-time).
auction-service owns price and end time: the bid and any anti-sniping extension are applied in one row-locked transaction.

```mermaid
sequenceDiagram
    autonumber
    actor Bidder
    participant App as Frontend
    participant GW as API Gateway
    participant BS as Bidding Service
    participant Redis as Redis (bid context, deposit flag)
    participant WS as Wallet Service
    participant AS as Auction Service
    participant DB_BS as Bidding DB
    participant DB_AS as Auction DB
    participant MQ as Kafka
    participant MS as Media Service (STOMP)

    Note over App, MS: Before bidding, the page subscribes to /topic/auctions/{id}<br/>(and /user/queue/notifications when logged in) via GW → MS

    Bidder->>App: Place bid (amount)
    App->>GW: POST /api/v1/bids {auctionId, amount} (Bearer JWT)
    GW->>BS: Route (X-User-Id injected from JWT)

    %% 1-2. Pre-validation against the cached context
    BS->>Redis: GET bidding:auction:{id}:context
    alt Cache miss
        BS->>AS: GET /internal/auctions/{id}/bid-context
        AS-->>BS: price, increment, deposit, status, seller, totalBids, endTime
        BS->>Redis: SET context
    end
    BS->>BS: preValidate: seller? ACTIVE and now < endTime?<br/>amount ≥ minimum (first bid: starting price, else current + increment)

    alt Invalid
        BS-->>App: 403 BID_OWN_AUCTION / 409 AUCTION_NOT_OPEN / 400 BID_TOO_LOW {minimumBid}
    else Valid
        %% 3. Deposit lock = implicit registration (first bid only)
        BS->>Redis: GET bidding:deposit:{auctionId}:{userId}
        alt No deposit flag
            BS->>WS: POST /internal/wallet/deposit-lock {userId, auctionId, depositAmount} (idempotent)
            alt Insufficient balance / wallet problem
                WS-->>BS: INSUFFICIENT_BALANCE / WALLET_NOT_ACTIVE / WALLET_NOT_FOUND / DEPOSIT_LOCK_CLOSED
                BS-->>App: 403 BID_INSUFFICIENT_BALANCE {availableBalance, required} (or 403 / 409)
            else Locked (or alreadyLocked)
                WS-->>BS: locked
                BS->>Redis: SET deposit flag
            end
        end

        %% 4. Record + authoritative apply in one local transaction
        BS->>DB_BS: INSERT bid (bidId)
        BS->>AS: POST /internal/auctions/{id}/bids {bidId, bidderId, amount}
        AS->>DB_AS: SELECT … FOR UPDATE (serialized with closure / cancel)
        AS->>AS: Re-check status, end time and minimum
        alt Rejected (outbid in the same instant, or closed)
            AS-->>BS: 400 BID_TOO_LOW / 409 AUCTION_NOT_OPEN
            BS->>DB_BS: ROLLBACK (deposit stays locked: it is the registration)
            BS-->>App: 400 / 409
        else Accepted
            AS->>DB_AS: UPDATE current_price, current_winner_id, total_bids, last_bid_id
            opt Less than 120 s left (anti-sniping)
                AS->>DB_AS: end_time += 300 s, extension_count++, INSERT auction_extensions
            end
            AS-->>BS: currentPrice, totalBids, endTime, extended
            BS->>DB_BS: COMMIT (bid.isAntiSnipingTriggered = extended)
            AS->>MQ: after commit (if extended): AuctionExtendedEvent → auction-extended-topic
            BS->>MQ: after commit: BidPlacedEvent → bid-placed-topic
            BS->>Redis: SET updated context
            BS-->>App: 201 {bidId, amount, currentPrice, totalBids, endTime, extended}
            App->>Bidder: Optimistic update ("You're winning")
        end
    end

    %% 6. Real-time fan-out (best effort)
    par Live updates
        MQ-->>MS: bid-placed-topic / auction-extended-topic
        MS-->>App: /topic/auctions/{id}: BID_PLACED, AUCTION_EXTENDED (all viewers)
        MS-->>App: /user/queue/notifications: OUTBID (previous leader only)
        App->>Bidder: Price, history and countdown update without reload
    end

    Note over AS, MS: At end_time (+20 s grace) the closure job re-checks under the row lock: if extended it reschedules,<br/>otherwise it closes and publishes AuctionEndedEvent → MS pushes AUCTION_ENDED
```

**Failure handling (spec §7):** wallet or auction-service timeouts return `503 SERVICE_UNAVAILABLE`. If the local commit fails after a successful apply-bid, bidding-service logs `CRITICAL` with the `bidId` (auction-service keeps `last_bid_id` for reconciliation). Kafka publishing happens after commit and never blocks the bid; browsers recover missed pushes by re-fetching on reconnect or reload.
