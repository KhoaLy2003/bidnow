# Winner Payment & Forfeit Flow

Applies to a win by bid or by Buy It Now. Phase 1: wallet top-up is a mock (instant credit); real gateway is Phase 2. There is no platform fee on a sale.

```mermaid
sequenceDiagram
    autonumber
    actor Winner
    participant App as Frontend
    participant GW as API Gateway
    participant AS as Auction Service
    participant MQ as Kafka
    participant WS as Wallet Service
    participant NS as Media Service

    Note over Winner, NS: A. Auction closes (scheduler, admin or Buy It Now)
    AS->>MQ: AuctionEndedEvent (auction-ended-topic): winnerId, winningBidAmount
    MQ->>WS: Consume
    WS->>WS: Refund every other LOCKED deposit (AUCTION_LOST), the winner's deposit stays LOCKED
    WS->>WS: Create payment hold: deposit applied, remaining = price − deposit, deadline = now + 48h
    alt available balance ≥ remaining
        WS->>WS: Move remaining from available to locked (HOLD), fundsHeld = true
    else balance too low
        WS->>WS: No money moves, fundsHeld = false
    end
    WS->>MQ: PaymentEvent REQUIRED (payment-event-topic)
    MQ->>NS: Consume
    NS-->>Winner: Payment-required email and in-app PAYMENT_REQUIRED push

    Note over Winner, NS: B. Winner pays (auction page "Proceed to payment" or /wallet "Payments due")
    Winner->>App: Opens the auction page or /wallet
    App->>GW: GET /api/v1/wallets/payments/pending
    GW->>WS: Route
    WS-->>App: Holds: remaining, fundsHeld, hoursLeft, expired
    opt fundsHeld = false and available < remaining
        Winner->>App: Adds funds in the pay dialog (mock top-up)
        App->>GW: POST /api/v1/wallets/deposit
        GW->>WS: Route
        WS-->>App: Balance credited immediately
    end
    Winner->>App: Clicks Pay
    App->>GW: POST /api/v1/wallets/payments/confirm {auctionId}
    GW->>WS: Route
    alt expired, already paid, or insufficient balance
        WS-->>App: 400 PAYMENT_DEADLINE_EXPIRED / 409 PAYMENT_NOT_PENDING / 400 INSUFFICIENT_BALANCE
    else valid
        WS->>WS: Lock hold, then both wallets in ascending id order
        WS->>WS: Winner pays the remainder, deposit lock RELEASED, any deposit excess refunded
        WS->>WS: Seller credited the full winning price (no fee), hold COMPLETED
        WS->>MQ: PaymentEvent COMPLETED
        WS-->>App: 200 ConfirmPaymentResponse
        MQ->>NS: Consume
        NS-->>Winner: Payment receipt (email, in-app PAYMENT_RECEIVED push)
    end

    Note over WS, NS: C. Deadline missed (ForfeitScheduler, every 5 minutes)
    loop each PENDING_PAYMENT hold past its deadline
        WS->>WS: Release the held remainder back to available (HOLD_CANCEL)
        WS->>WS: Forfeit the winner's deposit to the platform wallet (FORFEIT), hold FORFEITED
        WS->>MQ: PaymentEvent FAILED
        MQ->>NS: Consume
        NS-->>Winner: Payment-failed notice (in-app PAYMENT_FAILED push)
    end

    Note over Winner, WS: D. Winner returns later (reload, another tab, after a timed-out confirm)
    Winner->>App: Opens the auction page
    App->>GW: GET /api/v1/wallets/payments/pending
    GW->>WS: Route
    WS-->>App: No pending hold for this auction
    App->>GW: GET /api/v1/wallets/payments/{auctionId}
    GW->>WS: Route
    WS-->>App: Status COMPLETED, FORFEITED or CANCELLED, or 404 if there is no hold or the caller is not a participant
    App-->>Winner: Payment complete, deposit forfeited, or sale cancelled
```

The frontend refetches pending payments when a `PAYMENT_REQUIRED`, `PAYMENT_RECEIVED` or `PAYMENT_FAILED` push arrives. A returning winner's panel reads the final state from `GET /api/v1/wallets/payments/{auctionId}`: wallet-service owns the payment facts; auction-service keeps a projection of them. `PaymentEventConsumer` consumes `payment-event-topic`: `REQUIRED` sets `payment_deadline`, `COMPLETED` sets `winner_paid_at`, and `FAILED` (forfeit) moves the auction `COMPLETED → FAILED`.

**Seller view.** `GET /api/v1/auctions/public/{id}` (`AuctionDetailResponse`) returns `paymentDeadline` and `winnerPaidAt` (ISO instants, `null` until set), so the seller's manage page can show how long the winner has left to pay. The seller's payment status (`PENDING_PAYMENT`, `COMPLETED`, `CANCELLED`, `FORFEITED`) still comes from `GET /api/v1/wallets/payments/{auctionId}`, which also answers for the auction's seller; the page prefers it over `winnerPaidAt`, a projection. Both new fields are readable by anyone who can read the auction detail, because the endpoint is public.
