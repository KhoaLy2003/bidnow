# Epic: Wallet & Financial Management

**Epic ID:** `EPIC-003`
**Epic Title:** Wallet & Financial Management (MVP Phase 1: Mock Payment Gateway)
**Priority:** P0 (Critical - Foundation for Auction System)
**Target Version:** MVP v1.0
**Phase:** Phase 1 (Mock Gateway) | Phase 2 (VNPay Integration - Post-MVP)

---

## Epic Overview

This epic establishes the financial foundation of the BidNow auction platform. It delivers a user wallet system that manages the complete financial lifecycle of auction participation, from deposit locking through bidder refunds to winner payment settlement. Phase 1 focuses on a mock/simulated payment gateway to enable core auction workflows without third-party payment dependencies.

The Wallet Service acts as the **escrow and financial hub** that:

- Validates bidder financial eligibility via deposit requirements
- Locks and releases deposits based on auction outcomes
- Manages winner payment holds and settlement
- Handles deposit forfeiture for non-payment
- Calculates and tracks platform fees
- Maintains transaction audit trail for compliance

---

## Business Value

### For Bidders

- **Trust & Participation:** Mandatory deposits establish a good-faith participation mechanism and reduce frivolous bidding
- **Capital Efficiency:** Lock only required deposit, not full auction value; receive immediate refunds when losing
- **Payment Flexibility:** 24-48 hour payment window to complete purchase without bid-time pressure

### For Sellers

- **Payment Guarantee:** Deposit lock ensures winner has committed funds before auction closes
- **Settlement Certainty:** If winner doesn't pay, deposit is forfeited (can be assigned to seller or kept by platform)
- **Clear Revenue Flow:** Platform fee deducted transparently; seller knows exact net proceeds

### For Platform

- **Liquidity Management:** Internal wallet system allows float management without external payment processor dependencies
- **Risk Mitigation:** Forfeiture mechanism penalizes non-payment and discourages bad-faith bidding
- **Compliance Foundation:** Complete audit trail for financial transactions supports regulatory requirements
- **Revenue Model:** Platform fee creates sustainable business model and funds operations

### For Admins

- **Visibility & Control:** View all wallet balances, transaction history, and dispute resolution capabilities
- **Risk Monitoring:** Detect suspicious patterns (rapid deposits/withdrawals, large forfeits)
- **Operational Efficiency:** Ability to manually process refunds or reversals if needed

---

## Success Metrics

| Metric                          | Target  | Notes                                                            |
| ------------------------------- | ------- | ---------------------------------------------------------------- |
| **Deposit Lock Success Rate**   | >99.5%  | Deposits lock without errors or race conditions                  |
| **Refund Processing Time**      | <1 hour | Losing bidders receive refunds within 1 hour of auction closure  |
| **Payment Deadline Compliance** | >85%    | Winners complete payment within 48-hour deadline                 |
| **Forfeit Accuracy**            | 100%    | No erroneous forfeits; clear audit trail for all deductions      |
| **Zero Double-Spending**        | 100%    | Pessimistic/optimistic locking prevents overdraft scenarios      |
| **Balance Reconciliation**      | 100%    | Total locked + available = wallet total (no money leaks)         |
| **Transaction Audit Trail**     | 100%    | All operations logged with user, timestamp, previous/new balance |
| **System Availability**         | 99.9%   | Wallet service uptime critical for bid acceptance                |

---

## Epic Scope

### Phase 1 (MVP): Core Wallet + Mock Payment Gateway

#### In Scope ✅

**Wallet Initialization & Management:**

- [ ] Auto-create wallet when user registers (event-driven from Identity Service)
- [ ] Initialize with balance = 0, status = ACTIVE
- [ ] Track two balance types: `total_balance` and `available_balance` (available = total - locked)
- [ ] Expose wallet status endpoints for other services

**Mock Deposit System:**

- [ ] User initiates "Add Funds" request
- [ ] Display mock payment gateway UI (simulated transaction flow)
- [ ] User confirms payment (no actual payment processing)
- [ ] System marks deposit as successful, credits wallet
- [ ] Create DEPOSIT transaction record
- [ ] Emit event for Media Service

**Transaction History & Audit:**

- [ ] Store all operations in `transactions` table with immutable records
- [ ] Transaction types: DEPOSIT, REFUND, PAYMENT, FORFEIT, FEE, HOLD
- [ ] Include fields: type, amount, balance_before, balance_after, reference_id (auction_id, bid_id)
- [ ] Support paginated list with filtering by type and date range
- [ ] Expose transaction history API for dashboard

**Deposit Lock for Auction Participation:**

- [ ] User registers for auction → Wallet Service locks required `deposit_amount`
- [ ] Validate available_balance >= deposit_amount
- [ ] Create LOCKED transaction record or use `deposit_locks` table
- [ ] Update available_balance = available - locked_amount
- [ ] If insufficient funds, reject registration with clear error message
- [ ] Prevent bidding without completed registration (Bidding Service validates)

**Refund Logic (Losing Bidders & Cancelled Auctions) — WALLET-304:**

- [ ] Wallet consumes `auction-ended-topic` (`AuctionEndedEvent`) and `auction-cancelled-topic` (`AuctionCancelledEvent`)
- [ ] Refund targets are derived from `deposit_locks` (`status = LOCKED` for the auction): on end, everyone except the winner; on cancel, everyone. `AuctionEndedEvent.loserIds` is not used (it is empty until the bidding-service exists)
- [ ] For each lock, in its own DB transaction (wallet row locked first):
  - `available_balance += amount`, `locked_balance -= amount` (total unchanged)
  - Create REFUND transaction (before/after balances, `reference_id = auctionId`)
  - Update `deposit_locks.status = RELEASED`, `released_at = now`
  - After commit, emit `DepositRefundedEvent` on `deposit-refunded-topic` (reason `AUCTION_LOST` / `AUCTION_CANCELLED`)
- [ ] Zero-amount locks are released without a transaction or event
- [ ] Failures: other locks still refunded; record retried (1s/2s/4s) then sent to `<topic>.DLT`; replay of processing failures is idempotent (undeserializable records need manual decoding)
- [ ] Notification to bidder: "Your deposit has been refunded" (media-service consumes `deposit-refunded-topic` — later story)

**Winner Payment — WALLET-305:**

- [ ] On `auction-ended-topic` with a winner, create a `payment_holds` row (one per auction): `deposit_applied = min(LOCKED deposit, winning bid)`, `remaining = bid − deposit_applied`, deadline `now + 48h`
- [ ] If available ≥ remaining, move remaining to locked (HOLD ledger row); otherwise the hold is unfunded (`funds_held = false`) and the winner must top up before confirming
- [ ] `GET /api/v1/wallets/payments/pending` — pending holds with `deadline`, `hoursLeft`, `expired`
- [ ] `POST /api/v1/wallets/payments/confirm { auctionId }` — atomic: winner pays exactly the winning bid (deposit consumed, excess refunded), seller credited exactly the winning bid, PAYMENT ledger rows for both, hold COMPLETED
- [ ] Errors: `404 PAYMENT_HOLD_NOT_FOUND`, `400 PAYMENT_DEADLINE_EXPIRED`, `409 PAYMENT_NOT_PENDING`, `400 INSUFFICIENT_BALANCE`, `409 DEPOSIT_LOCK_CLOSED`, `404 SELLER_WALLET_NOT_FOUND`
- [ ] `PaymentEvent` on `payment-event-topic`: `REQUIRED` on hold creation, `COMPLETED` on confirm
- [ ] On `auction-cancelled-topic`, a `PENDING_PAYMENT` hold is voided (held funds returned, HOLD_CANCEL ledger row, status CANCELLED)
- [ ] Follow-ups: auction-service consumes `PaymentEvent` (`REQUIRED` → `payment_deadline`, `COMPLETED` → `winner_paid_at`, `FAILED` → mark the sale failed)

**Known gaps (follow-up tickets, outside WALLET-304):**

- Auction-service publishes `AuctionCancelledEvent` (admin and seller cancel) and the admin force-close `AuctionEndedEvent` *before* its DB transaction commits; a rollback after the send would refund deposits on a still-ACTIVE auction. Only `AuctionClosureService` publishes after commit.
- `AuctionItem` has no optimistic lock and close/cancel don't lock the auction row, so an auction can emit both ended and cancelled events. WALLET-305 handles either order: a cancel voids any pending hold and records an `auction_cancellations` marker; a later end event creates no hold when the marker exists or the winner's deposit was already released. A cancel after the payment was COMPLETED is logged at ERROR for manual reconciliation. Residual: if both events are processed at the same instant on different consumer threads, a hold can still be created (needs a per-auction lock or auction-service serialization).
- A deposit lock created after the refund sweep (or after the auction closed) stays LOCKED: wallet does not know auction state. Needs a closed-auction guard in `lockDeposit` or a reconciliation job.
- Lock ordering rule for any future flow touching payment holds: lock the `payment_holds` row before wallets, and order wallet locks with Java `UUID.compareTo` (as confirm, cancel and forfeit do). Forfeited deposits go entirely to the platform wallet; a seller share is an open question.

**Winner Payment Flow:** see **Winner Payment — WALLET-305** above (supersedes the original draft: no `AUCTION_ENDED_WITH_WINNER` event, no PENDING_PAYMENT transaction, no platform fee in Phase 1).

**Forfeit Logic (Non-Payment) — WALLET-306:**

- [ ] `ForfeitScheduler` runs every `wallet.payment.forfeit-interval-ms` (5 min), reads up to `forfeit-batch-size` (100) `payment_holds` in `PENDING_PAYMENT` with `deadline < now`
- [ ] Each hold forfeited in its own transaction: hold row `FOR UPDATE SKIP LOCKED` (safe on several instances), re-check still pending and past deadline, then winner + platform wallets locked in ascending id order
- [ ] Held remainder returned to the winner (HOLD_CANCEL ledger row)
- [ ] Whole LOCKED deposit moved to the platform wallet (`wallet.platform-user-id`): FORFEIT rows on both wallets; `deposit_locks.status = FORFEITED`
- [ ] No LOCKED deposit → nothing forfeited, hold still FORFEITED
- [ ] `payment_holds.status = FORFEITED`; after commit `PaymentEvent{paymentType=FAILED, depositAmount=forfeited}` on `payment-event-topic`
- [ ] Failures logged per hold and retried on the next run
- [ ] Notifications (winner: deposit forfeited; seller: auction failed) — media-service, later story

**Platform Fee Calculation & Tracking:**

- [ ] Define platform fee structure (e.g., 5% of final_payment_amount)
- [ ] Deduct fee during payment completion:
  - platform_fee = final_payment_amount × fee_rate
  - seller_payout = final_payment_amount - platform_fee
- [ ] Create FEE transaction tracking fee deduction
- [ ] Maintain running total of platform collected fees
- [ ] Expose admin endpoint to view total fees collected

**Balance Consistency & Atomic Operations:**

- [ ] All wallet updates wrapped in database transactions (ACID)
- [ ] Use pessimistic locking (SELECT FOR UPDATE) for concurrent access
- [ ] Prevent race conditions when multiple bids/refunds happen simultaneously
- [ ] Balance validation: total_balance - available_balance = sum(all locked amounts)
- [ ] Reconciliation job runs daily to verify consistency

**Data Integrity & Immutability:**

- [ ] Transactions table is append-only (no updates/deletes after creation)
- [ ] Wallet balance changes only via transaction records
- [ ] No manual balance modifications without audit trail
- [ ] Soft deletes only for future cleanup (mark deleted_at, don't hard-delete)

#### Out of Scope ❌ (Phase 2)

- [ ] Real payment gateway integration (VNPay, Stripe, etc.)
- [ ] Withdrawal/cashout functionality for sellers
- [ ] KYC (Know Your Customer) verification
- [ ] Fraud detection and advanced risk scoring
- [ ] Multi-currency support
- [ ] Complex fee structures (tiered, regional, promotional)
- [ ] Chargeback handling and dispute resolution
- [ ] PCI compliance (no card storage - deferred to Phase 2 gateway)

---

## Technical Architecture

### Services Involved

**Primary Service:**

- **Wallet Service** — All financial logic and state management

**Dependencies (Consumers of Wallet Events):**

- **Auction Service** — Emits `AUCTION_ENDED`, `AUCTION_ENDED_WITH_WINNER` events; consumes wallet balance validation
- **Bidding Service** — Validates user is registered (has locked deposit) before accepting bids
- **Media Service** — Consumes wallet events: `DEPOSIT_RECEIVED`, `DEPOSIT_REFUNDED`, `PAYMENT_FAILED`, etc.
- **Identity Service** — Wallet initialization triggered by `USER_REGISTERED` event

**Data Store:**

- **PostgreSQL** (Wallet Service database) — `wallets`, `transactions`, `deposit_locks` tables
- **Redis** (optional, Phase 1.5) — Cache wallet balances for real-time read performance; invalidate on write

### Event-Driven Flows

```
┌─ Auction Service ──┐
│  AUCTION_ENDED     │
└────────┬───────────┘
         │
         ├──────────────────────────────┐
         │                              │
    Wallet Service                 Media Service
    - Process refunds              - Send "Refunded" email
    - Update balances             - Send "Payment pending" email
    - Emit DEPOSIT_REFUNDED

┌─ Auction Service ──────────────────────────────────┐
│  AUCTION_ENDED_WITH_WINNER                         │
│  { auctionId, winnerId, winningBidAmount }         │
└────────┬──────────────────────────────────────────┘
         │
    Wallet Service
    - Create HOLD transaction
    - Set payment_deadline
    - Validate winner has funds
         │
         ├──────────────────────────────┐
         │                              │
         ├─ Media Service ──────┤
         │  "Complete payment within    │
         │   48 hours"                  │
         │                              │
         └─────────────────────────────┘

┌─ Scheduled Job (Wallet Service) ────┐
│  Every 5 min: Check payment deadlines│
│  Expired? → Forfeit deposit          │
└────────┬───────────────────────────┘
         │
    Wallet Service
    - Create FORFEIT transaction
    - Update available_balance
    - Emit PAYMENT_FAILED
         │
    Media Service
    - "Deposit forfeited"
    - "Auction failed"
```

### Key Components

1. **Wallet Repository** — Database access for wallet and transaction records
2. **Wallet Service (Business Logic)** — Core workflows: deposit lock, refund, payment, forfeit
3. **Transaction Manager** — Atomic transaction handling with locking
4. **Scheduler Jobs** — Background workers for refund processing, forfeit detection
5. **Event Publisher** — Emits wallet events to message broker
6. **Event Listeners** — Consumes Auction Service events
7. **REST API Controllers** — User-facing endpoints for deposit, history, payment

### Data Flow Diagram

```
User (Bidder)
    │
    ├─ POST /api/v1/wallet/deposit ──→ Wallet Service
    │  ("Add 500,000 VND")             │
    │  ↓ (Response: confirm)           ├─ Create DEPOSIT tx
    │                                  ├─ Credit balance
    │                                  └─ Emit DEPOSIT_RECEIVED
    │                                     │
    │                                     └→ Media Service
    │
    │ (Later: Join Auction)
    │
    ├─ POST /api/v1/auctions/123/register
    │                               ↓
    │                    Auction Service
    │                     (calls Wallet)
    │                               │
    │                    POST /api/v1/wallet/lock
    │                    { amount: 50000, auctionId: 123 }
    │                               │
    │                    Wallet Service
    │                    ├─ Validate balance
    │                    ├─ Create deposit_lock record
    │                    └─ Update available_balance
    │                               │
    │                    ← Response: OK
    │                               │
    │                    ← Auction Service creates registration
    │
    │ (User can now bid)
    │
    │ (Auction ends - User LOSES)
    │
    └─ (Async: Auction Service emits AUCTION_ENDED)
                         │
                    Wallet Service
                    ├─ Find all deposit_locks for auction
                    ├─ For losing bidders:
                    │  ├─ Create REFUND transaction
                    │  └─ Update available_balance
                    │
                    └─ Emit DEPOSIT_REFUNDED events
                         │
                    Media Service
                    └─ Send "Deposit refunded" notification
```

---

## Database Schema Reference

See detailed schema in [wallet-schema.md](./wallet-schema.md) (to be created). Core tables:

### `wallets`

```
- id (UUID, PK)
- user_id (UUID, FK, UNIQUE)
- total_balance (DECIMAL, default: 0)
- available_balance (DECIMAL, default: 0)
- locked_balance (DECIMAL, virtual: total - available)
- currency (VARCHAR, default: 'VND')
- status (ENUM: ACTIVE, FROZEN, CLOSED)
- created_at, updated_at
```

### `transactions`

```
- id (UUID, PK)
- wallet_id (UUID, FK)
- type (ENUM: DEPOSIT, REFUND, PAYMENT, FORFEIT, FEE, HOLD, RELEASE)
- amount (DECIMAL)
- balance_before (DECIMAL)
- balance_after (DECIMAL)
- reference_id (VARCHAR) -- auction_id, bid_id, etc.
- description (TEXT)
- status (ENUM: PENDING, COMPLETED, FAILED)
- payment_gateway_tx_id (VARCHAR, nullable) -- for Phase 2
- metadata (JSONB) -- extra context
- created_at
```

### `deposit_locks`

```
- id (UUID, PK)
- wallet_id (UUID, FK)
- auction_id (UUID, FK)
- amount (DECIMAL)
- status (ENUM: LOCKED, RELEASED, FORFEITED)
- locked_at (TIMESTAMP)
- released_at (TIMESTAMP, nullable)
- created_at
```

---

## API Endpoints (High-Level)

### User-Facing Endpoints

**Wallet Balance:**

- `GET /api/v1/wallet` — Retrieve wallet balance and status
- `GET /api/v1/wallet/balance` — Quick balance check (read-only, cacheable)

**Deposits:**

- `POST /api/v1/wallet/deposit` — Initiate deposit (mock gateway UI)
- `GET /api/v1/wallet/deposit-status/{transactionId}` — Check deposit status

**Transaction History:**

- `GET /api/v1/wallet/transactions` — List user's transactions (paginated, filterable)
- `GET /api/v1/wallet/transactions?type=REFUND&startDate=...&endDate=...` — Advanced filtering

**Payment (Winner Only):**

- `POST /api/v1/wallets/payments/confirm` `{ auctionId }` — Confirm payment for won auction
- `GET /api/v1/wallets/payments/pending` — List pending payments with deadlines

### Internal Service Endpoints (service-to-service, not routed via API Gateway)

Called directly via Eureka/OpenFeign. The gateway blocks `/api/v1/**/internal/**`; wallet-service permits `/api/v1/internal/**` without user auth. Responses use `BaseResponse<T>`; errors use `ErrorResponse`.

**Bidding Service Integration (WALLET-303 — first bid = implicit registration):**

- `GET /api/v1/internal/wallet/deposit-lock?userId=&auctionId=` — Deposit lock status → `{ locked, amount?, status?, lockedAt? }`
- `POST /api/v1/internal/wallet/deposit-lock` `{ userId, auctionId, depositAmount }` — Idempotently lock the deposit → `{ lockId, amount, status, alreadyLocked, availableBalance, lockedBalance }`
  - Errors: `400 INSUFFICIENT_BALANCE` (`errors: { availableBalance, required }`), `400 INVALID_INPUT`, `403 WALLET_NOT_ACTIVE`, `404 WALLET_NOT_FOUND`, `409 DEPOSIT_LOCK_CLOSED`
- `GET /api/v1/internal/wallet/balance/{userId}` — `{ totalBalance, availableBalance, lockedBalance, currency }`; `404 WALLET_NOT_FOUND`
- Deposit release (unlock) — future story (refund/forfeit flows)

**Event Publishing:**

- `POST /api/v1/internal/wallet/events/refund-batch` — Trigger refund processing
- `POST /api/v1/internal/wallet/events/forfeit-batch` — Trigger forfeit processing

### Admin Endpoints

- `GET /api/v1/admin/wallet/{userId}` — View user's full wallet details
- `GET /api/v1/admin/transactions` — View all transactions (platform-wide)
- `GET /api/v1/admin/wallet-stats` — Dashboard stats (total fees, active locks, etc.)
- `POST /api/v1/admin/transactions/{id}/refund` — Manual refund (with audit reason)
- `POST /api/v1/admin/wallet/{userId}/freeze` — Freeze account (fraud prevention)

---

## Open Questions to Resolve Before Detailed Design

Before breaking into individual user stories, clarify:

### 1. **Deposit Amount Calculation**

- [ ] **Fixed amount:** e.g., 50,000 VND per auction (one-size-fits-all)?
- [ ] **Percentage-based:** e.g., 10% of starting price (flexible, scales with auction size)?
- [ ] **Hybrid:** Fixed minimum + percentage of amount above threshold?
- [ ] **Who decides:** Platform default, or seller configurable per auction?
- **Decision needed by:** Sprint planning

### 2. **Platform Fee Structure**

- [ ] **Fee rate:** What percentage? (e.g., 5%, 10%)?
- [ ] **Fee calculation:** On winning_bid_amount or (winning_bid_amount - starting_price)?
- [ ] **Who bears cost:** Buyer pays fee on top of bid, or seller net? Or split?
- [ ] **Fee flow:** Platform keeps in separate account, or rolled into operational budget?
- **Decision needed by:** Sprint planning

### 3. **Refund Timing**

- [ ] **Immediate refund:** Process within seconds of auction closure (real-time, resource-intensive)?
- [ ] **Delayed batch:** Process in batches every 5-15 minutes (less resource overhead)?
- [ ] **SLA:** Target refund time (e.g., "within 1 hour of auction end")?
- **Decision needed by:** Sprint planning

### 4. **Forfeited Deposit Destination**

- [ ] **To platform:** Keep as revenue/penalty (incentivizes payment)?
- [ ] **To seller:** Compensate seller for wasted time/inventory?
- [ ] **Split:** e.g., 50% platform, 50% seller?
- [ ] **Configurable:** Admin decides per situation?
- **Decision needed by:** Sprint planning

### 5. **Withdrawal Functionality (Phase 1 vs. Phase 2)**

- [ ] **Phase 1:** No withdrawals — sellers can only use wallet balance for future buys?
- [ ] **Phase 1:** Mock withdrawal (deduct balance, log transaction, no actual bank transfer)?
- [ ] **Phase 2:** Real withdrawal via VNPay or bank API?
- **Decision needed by:** Roadmap planning (likely Phase 2 for MVP)

### 6. **Withdrawal Limits & Restrictions**

- [ ] **Minimum withdrawal:** e.g., 100,000 VND minimum?
- [ ] **Maximum per day/week:** Fraud prevention limits?
- [ ] **KYC requirement:** Must verify identity before first withdrawal?
- [ ] **Seller tier:** Different limits for new vs. established sellers?
- **Decision needed by:** If withdrawal included in Phase 1

### 7. **Currency Support (MVP Scope)**

- [ ] **Single currency:** VND only for MVP?
- [ ] **Multi-currency:** Support VND, USD, etc. from day 1?
- [ ] **Conversion:** If multi-currency, handle FX rates?
- **Decision needed by:** Sprint planning

### 8. **Balance Type Naming**

- [ ] **Available balance:** Confirmed, spendable balance?
- [ ] **Locked balance:** Tied up in active auction deposits?
- [ ] **Pending balance:** Awaiting refund processing?
- [ ] **On-hold balance:** Winner's payment held for 48h?
- **Decision needed by:** API design phase

### 9. **Concurrent Access & Race Condition Handling**

- [ ] **Locking strategy:** Pessimistic (SELECT FOR UPDATE) or optimistic (version numbers)?
- [ ] **Timeout:** If lock held >X seconds, fail or wait?
- [ ] **Retry logic:** Auto-retry failed operations, or fail fast?
- **Decision needed by:** Technical design phase

### 10. **Compliance & Audit Requirements**

- [ ] **Data retention:** How long to keep transaction records?
- [ ] **PII in logs:** Can we log user identifiers, or only anonymized?
- [ ] **Tax reporting:** Track taxable events (fees, seller payouts)?
- [ ] **Regulatory:** Any financial regulations we must comply with (Vietnam specific)?
- **Decision needed by:** Security/legal review before Phase 1 completion

---

## Open Decisions (Defaults for Phase 1 MVP)

**Based on common auction platform patterns, proposing:**

| Question            | Proposed Default                                 | Rationale                                                                        |
| ------------------- | ------------------------------------------------ | -------------------------------------------------------------------------------- |
| Deposit calculation | **Seller configurable, 5-20% of starting price** | Flexibility balances buyer and seller incentives                                 |
| Platform fee        | **5% of final_payment_amount**                   | Industry-standard rate; seller bears cost (competitive pressure)                 |
| Refund timing       | **Batch processing every 15 min**                | Balances UX and resource efficiency                                              |
| Forfeited deposits  | **→ Platform account**                           | Penalty mechanism discourages non-payment                                        |
| Withdrawal Phase    | **Phase 2 (post-MVP)**                           | Focus Phase 1 on escrow; real banking adds complexity                            |
| Currency            | **VND only (MVP)**                               | Simplifies Phase 1; multi-currency in Phase 2                                    |
| Locking strategy    | **Pessimistic (SELECT FOR UPDATE)**              | Prevents race conditions; auctions are short-lived so lock contention acceptable |

**👉 These defaults are open for discussion and override during epic refinement.**

---

## Dependencies

### Upstream (Must Complete First)

- ✅ **Identity Service** — User creation, JWT tokens, user info retrieval
- ✅ **Database** — PostgreSQL instance, schema migrations framework
- ✅ **Message Broker** — RabbitMQ/Kafka configured, topics created
- ⚠️ **Auction Service** — Must emit `AUCTION_ENDED`, `AUCTION_ENDED_WITH_WINNER` events
- ⚠️ **Bidding Service** — Must validate user registration before accepting bids

### Downstream (Will Consume Wallet Events)

- ⚠️ **Media Service** — Must listen to wallet events (DEPOSIT_RECEIVED, REFUND, PAYMENT_FAILED)
- ⚠️ **Auction Service** — Must integrate deposit lock API for registration flow
- ⚠️ **Admin Dashboard** — Must display wallet stats, transaction history, manual actions

---

## Risks & Mitigation

| Risk                                                      | Probability | Impact   | Mitigation                                                                                |
| --------------------------------------------------------- | ----------- | -------- | ----------------------------------------------------------------------------------------- |
| **Race condition: concurrent bids + deposit lock**        | High        | Critical | Implement pessimistic locking (SELECT FOR UPDATE) on wallet rows                          |
| **Refund not processed after auction ends**               | Medium      | High     | Add monitoring alerts; reconciliation job verifies all auctions processed                 |
| **Winner non-payment forgiveness request (edge case)**    | Low         | Medium   | Require admin approval for refunds; maintain audit trail                                  |
| **Balance negative (overdraft)**                          | Low         | Critical | Validate available_balance before every debit; prevent negative balances in DB constraint |
| **Scheduled job failure (forfeit detection)**             | Medium      | High     | Add retries + dead letter queue; operational monitoring dashboard                         |
| **High forfeit rate (indicator of fraud/systemic issue)** | Low         | Medium   | Dashboard alerts if forfeit % exceeds threshold; trigger investigation                    |
| **Mock gateway too unrealistic for testing**              | Medium      | Low      | Provide mock with configurable success/failure rates; simulate network delays             |
| **Transaction table grows too large (audit trail)**       | Low         | Low      | Implement data archival strategy; partition by date if needed                             |

---

## Success Criteria Review

At the end of this epic (Phase 1), the BidNow platform will have:

✅ **Functional Wallet System:**

- Every user has a wallet initialized on signup
- Users can deposit mock funds and see real-time balance updates
- Wallet balances prevent overdrafts (pessimistic locking)

✅ **Auction Participation:**

- Sellers can specify deposit amount when creating auctions
- Bidders cannot participate without sufficient available balance
- Deposits automatically locked during auction, released after closure

✅ **Automatic Refunds:**

- Losing bidders refunded within 1 hour of auction end
- Refund transaction logged with audit trail
- Notifications sent to bidders

✅ **Winner Payment & Settlement:**

- Winners receive payment hold notification with 48-hour deadline
- Payment confirmation available (mock transaction)
- Platform fee deducted automatically
- Seller receives payout (seller balance updated)

✅ **Forfeit Mechanism:**

- Non-paying winners forfeit deposits automatically after deadline
- Deposit transferred to platform account (or seller per decision)
- Clear audit trail for all forfeits

✅ **Compliance & Auditability:**

- 100% transaction immutability (append-only records)
- Zero balance inconsistencies (daily reconciliation)
- Full audit trail exportable for compliance

✅ **Performance & Reliability:**

- Wallet service uptime: 99.9%
- Lock/unlock operations: <100ms p95 latency
- Refund batch processing: completes all auctions within 1 hour

✅ **Testing & Documentation:**

- Unit test coverage ≥ 80% on critical paths (locking, refunds, payment)
- Integration tests for multi-service event flows
- API documentation (Swagger) with examples
- Operational runbook for common issues

---

## Estimated Effort

**Phase 1 (MVP - Mock Payment):**

| Component                                | Story Points | Days        |
| ---------------------------------------- | ------------ | ----------- |
| Wallet initialization & balance tracking | 5            | 2           |
| Mock deposit system                      | 5            | 2           |
| Transaction history & audit              | 3            | 1.5         |
| Deposit lock/unlock                      | 8            | 3           |
| Refund logic & batch processing          | 8            | 3           |
| Winner payment & settlement              | 8            | 3           |
| Forfeit detection & processing           | 5            | 2           |
| Platform fee calculation                 | 3            | 1.5         |
| Testing (unit + integration)             | 8            | 3           |
| API documentation & DevOps setup         | 3            | 1.5         |
| **Total Phase 1**                        | **56**       | **22 days** |

**Phase 2 (Real Payment Gateway - Post-MVP):**

- VNPay integration: 13 points
- Withdrawal functionality: 8 points
- Advanced error handling: 5 points
- **Total Phase 2:** ~26 points (estimated, not scoped here)

---

## Next Steps

1. **Review & Approve** — Product owner and tech lead review this epic definition
2. **Clarify Open Questions** — Finalize the 10 business decisions (see section above)
3. **Refine Scope** — Adjust based on clarifications (e.g., Phase 2 features pulled into Phase 1 if needed)
4. **Create Data Model Document** — `wallet-schema.md` with detailed table designs, indexes, constraints
5. **Break into User Stories** — Create individual GitHub issues for each feature (Issue 3.1, 3.2, etc.)
6. **Sprint Planning** — Allocate stories to sprints based on dependencies

---

## References

- **Functional Requirements:** [docs/functional.md](../../functional.md) — Wallet & Financials section
- **Non-Functional Requirements:** [docs/non-functional.md](../../non-functional.md) — Performance, Security, Data Consistency
- **Business Clarifications:** [docs/business-clarifications.md](../../business-clarifications.md) — Deposit policy, refund timing
- **Architecture:** [docs/architecture.md](../../architecture.md) — Microservices interactions
- **Auction Epic:** [epic-1.md](../auction/epic-1.md) — Wallet integration points
- **Draft Discussion:** [draft.md](./draft.md) — Vietnamese design exploration (reference only)

---

## Document History

| Version | Date       | Author | Changes                                 |
| ------- | ---------- | ------ | --------------------------------------- |
| 1.0     | 2026-05-20 | Khoa   | Initial epic definition for Phase 1 MVP |

---

**Status:** 🟡 **Draft - Awaiting Review & Open Questions Clarification**

**Next Review Date:** [To be scheduled with Product Owner & Tech Lead]

**Document Owner:** Backend Team Lead

**Last Updated:** 2026-05-20
