# Architecture Overview - BidNow

## Tech Stack

### Backend

- **Language:** Java 17
- **Framework:** Spring Boot 3.x
- **Microservices Orchestration:** Spring Cloud (Gateway, Service Discovery, Config Server)
- **ORM:** Spring Data JPA / Hibernate
- **Build Tool:** Maven
- **Messaging:** RabbitMQ or Apache Kafka (for inter-service communication)

### Database

- **Primary Database:** PostgreSQL 15+ (Individual database per microservice)
- **Caching:** Redis (Global cache for sessions and real-time bid leaderboards)

### Frontend

- **Framework:** Next.js (TypeScript)
- **Styling:** Tailwind CSS
- **State Management:** Zustand or React Context
- **Real-time:** Socket.io-client or Native WebSockets

### External Services

- **Cloud Storage:** Cloudinary (Product images and user avatars)
- **Email:** SendGrid or AWS SES
- **Payment Gateway:** Stripe or VNPay (Planned for payment phase)

---

## Microservices Architecture

### Core Services

1. **API Gateway**: The entry point for all client requests. Handles routing, rate limiting, and initial security checks.
2. **Identity Service**: Manages user registration (including email OTP verification), login, and JWT token issuance/validation.
3. **User Service**: Manages user profiles, preferences, and account metadata.
4. **Auction Service**: Handles the lifecycle of auction listings (Creation, Active, Closure). Manage "Buy It Now" logic.
5. **Bidding Service**: The high-performance engine for placing bids, calculating auto-bids, and managing the "Anti-sniping" time extensions.
6. **Wallet & Payment Service**: Manages the internal wallet, escrow (deposits), and final transaction processing.
7. **Media Service**: Handles email notifications, templates, media assets, and audit log storage.

---

## Data Flow

### Request Flow

1. **Client** → Request hits the **API Gateway**.
2. **Gateway** → Validates JWT (via Identity Service) and routes to the target microservice.
3. **Microservice** → Executes business logic and persists data to its local **PostgreSQL** instance.
4. **Events** → Service emits an event (e.g., `BID_PLACED`) to the **Message Broker**.
5. **Consumers** → **Media Service** picks up the event and broadcasts it via **WebSocket**.

### Real-time Communication

- **WebSockets**: Used for live price updates on auction pages and "Outbid" alerts.
- **Message Broker**: Ensures eventual consistency between services (e.g., Auction closed → Notification sent → Wallet refund initiated).

### Service-to-Service Internal APIs

Synchronous internal calls go directly between services via Eureka + OpenFeign, never through the API Gateway. The gateway's `AuthenticationFilter` blocks `/api/v1/**/internal/**`, and each owning service `permitAll`s its own internal paths.

| Owner | Endpoint | Caller | Purpose |
|---|---|---|---|
| Wallet | `GET /api/v1/internal/wallet/deposit-lock?userId=&auctionId=` | Bidding | Is the user's deposit locked for this auction? |
| Wallet | `POST /api/v1/internal/wallet/deposit-lock` `{userId, auctionId, depositAmount}` | Bidding | Idempotently lock the deposit on first bid (implicit registration). Errors: `INSUFFICIENT_BALANCE` 400, `WALLET_NOT_ACTIVE` 403, `WALLET_NOT_FOUND` 404, `DEPOSIT_LOCK_CLOSED` 409 |
| Wallet | `GET /api/v1/internal/wallet/balance/{userId}` | Bidding | Total / available / locked balances |

### Wallet Events (Kafka)

| Direction | Topic | Payload | Purpose |
|---|---|---|---|
| Consumes | `auction-ended-topic` | `AuctionEndedEvent` | Refund every `LOCKED` deposit for the auction except the winner's (`AUCTION_LOST`). Losers are derived from `deposit_locks`; `loserIds` is not used. |
| Consumes | `auction-cancelled-topic` | `AuctionCancelledEvent` | Refund every `LOCKED` deposit for the auction (`AUCTION_CANCELLED`). |
| Produces | `deposit-refunded-topic` (key `userId`) | `DepositRefundedEvent { userId, walletId, auctionId, amount, reason, refundedAt }` | One per non-zero refund, published after commit. |
| Consumes | `auction-ended-topic` (winner) | `AuctionEndedEvent` | Create a 48h payment hold for the winner (`payment_holds`); deposit counts toward the price. |
| Consumes | `auction-cancelled-topic` (hold) | `AuctionCancelledEvent` | Void any `PENDING_PAYMENT` hold and return held funds. |
| Produces | `payment-event-topic` (key winner `userId`) | `PaymentEvent { paymentType: REQUIRED \| COMPLETED, … }` | Payment required (with deadline, `insufficientFunds`) / payment completed; after commit. |

Each refund is its own DB transaction. The retry/DLT policy applies to **all** wallet-service listeners (including `user-registered-topic`): failed records are retried 3 times with exponential backoff (1s, 2s, 4s), then published to `<topic>.DLT` on the same partition number — so a `.DLT` topic must have at least as many partitions as its source topic (auto-created topics use the broker default, currently 3). Redelivery and DLT replay of processing failures are safe because released locks are skipped. Undeserializable records go straight to `<topic>.DLT` via `ErrorHandlingDeserializer`; their payload is the raw bytes re-encoded as a base64 JSON string, so they need manual decoding and cannot be replayed as-is.

**Winner payment endpoints (public, via gateway):** `GET /api/v1/wallets/payments/pending`, `POST /api/v1/wallets/payments/confirm { auctionId }`. Confirm settles atomically: hold row locked first, then winner and seller wallets in ascending id order.

---

## High-Level Diagram

```mermaid
graph TD
    %% Layers Definition
    subgraph Client_Layer [Client Layer]
        Client[Next.js Frontend / Mobile]
    end

    subgraph Gateway_Layer
        Gateway[API Gateway]
    end

    subgraph Microservices_Layer [Microservices Layer]
        Identity[Identity Service]
        User[User Service]
        Auction[Auction Service]
        Bidding[Bidding Service]
        Wallet[Wallet Service]
        Notify[Media Service]
    end

    subgraph Infrastructure [Shared Infrastructure]
        Broker[Message Broker - RabbitMQ/Kafka]
        Cache[Redis - Caching/Real-time]
    end

    subgraph Database_Layer [Database Layer]
        DB_ID[(Identity DB)]
        DB_User[(User DB)]
        DB_Auc[(Auction DB)]
        DB_Bid[(Bidding DB)]
        DB_Wal[(Wallet DB)]
        DB_Not[(Media DB)]
    end

    %% Connections
    Client -->|HTTPS / WSS| Gateway

    Gateway --> Identity
    Gateway --> User
    Gateway --> Auction
    Gateway --> Bidding
    Gateway --> Wallet
    Gateway --> Notify

    %% Service to DB mappings
    Identity --> DB_ID
    User --> DB_User
    Auction --> DB_Auc
    Bidding --> DB_Bid
    Wallet --> DB_Wal
    Notify --> DB_Not
```

---

## Deployment Architecture

- **Frontend:** Vercel (Optimized for Next.js).
- **Backend:** Containerized (Docker) and managed via Kubernetes or AWS ECS.
- **Database:** Managed PostgreSQL (e.g., AWS RDS or Supabase).
- **CI/CD:** GitHub Actions for automated testing and deployment.

---

## Key Design Decisions

- **Microservices Choice:** Chosen to isolate the **Bidding Service**, which requires higher scaling and lower latency than the User or Wallet services.
- **PostgreSQL per Service:** Ensures database independence and prevents tight coupling between services.
- **Event-Driven:** Using a Message Broker allows the system to remain responsive under heavy load by processing non-critical tasks (like emails) asynchronously.

---

## Performance & Scalability

- **Bidding Performance:** The Bidding Service uses **Redis** to keep the "current highest bid" in memory for lightning-fast validation.
- **Horizontal Scaling:** Each service can be scaled independently. The Notification service can have multiple instances to handle thousands of WebSocket connections.

---

## References

- [Functional Requirements](functional.md)
- [Non-Functional Requirements](non-functional.md)
- [Business Clarifications](business-clarifications.md)
