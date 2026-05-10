# Meeting 3: Progress Review & Core Feature Requirements (Auction & Wallet)

## Basic Information

- **Date:** 2026-05-10
- **Time:** 09:00 PM - 10:00 PM
- **Meeting Type:** Progress Review & Feature Grooming
- **Participants:**
  - Backend Team
  - Frontend Team
  - Product Owner
  - DevOps
- **Host:** Khoa Ly

---

## Agenda

1. **Epic 1 Review:** Status update on User Management (Identity Service) and Notification Service.
2. **Current Implementation Progress:** What has been completed and what remains for Epic 1.
3. **Epic 2 Deep Dive:** Detailed requirements for Auction Service.
4. **Epic 3 Deep Dive:** Detailed requirements for Wallet Service (Deposit, Escrow, Payment).
5. **Technical Sync:** Real-time bidding (WebSockets) and cross-service event flows.

---

## Discussion

### 1. Epic 1: User Onboarding & Communication (Status Update)

**Completed Features:**

- **Identity Service:**
  - User Registration with email/password.
  - Secure Login with JWT authentication.
  - Basic profile management infrastructure.
- **Notification Service:**
  - Core service architecture established.
  - Integration with Message Broker (RabbitMQ/Kafka).
  - Basic email template system for registration.

**Pending Work:**

- Frontend integration for complex notification flows (in-app alerts).
- Advanced profile features (avatar uploads, contact verification).
- Integration testing between Identity and Notification services.

### 2. Epic 2: Auction Management & Discovery

**Core Requirements:**

- **Auction Lifecycle:**
  - Create, Update, Delete (before start).
  - Automated start/end transitions.
  - Anti-sniping mechanism (5-minute extension for late bids).
- **Public Discovery:**
  - Paginated listing for guests and users.
  - Full-text search on titles and descriptions.
  - Filtering by category, price, and ending time.
- **Winner Determination:**
  - Automated selection of the highest bidder.
  - Event emission to notify winner and wallet service.

### 3. Epic 3: Wallet & Financial Operations

**Core Requirements:**

- **Balance Management:**
  - Deposit funds (Mock Gateway for Phase 1, VNPay for Phase 2).
  - Transaction history (DEPOSIT, REFUND, PAYMENT, FORFEIT).
- **Escrow Logic:**
  - **Locking:** mandatory deposit before placing a bid.
  - **Refunding:** automatic release of funds for losing bidders.
  - **Forfeiting:** penalty deduction if winner fails to pay within 48h.
- **Payment Flow:**
  - Winner pays the full amount from their wallet.
  - Seller payout (minus platform commission).

### 4. Technical Integration (Cross-Service)

- **Event-Driven Flow:**
  - `AUCTION_ENDED` -> Wallet Service (Refunds/Holds).
  - `BID_PLACED` -> Auction Service (Anti-sniping check).
- **WebSockets:** Plan for real-time bid updates on the auction detail page.

---

## Decisions

_(To be filled during/after the meeting)_

- [ ] Choice of Message Broker (RabbitMQ vs Kafka).
- [ ] Final deposit calculation formula (Fixed vs Percentage).
- [ ] WebSocket server architecture (Dedicated vs integrated).

---

## Action Items

| Task                                       | Owner | Due Date | Status |
| ------------------------------------------ | ----- | -------- | ------ |
| Complete Identity-Notification integration |       |          | TODO   |
| Draft API Specs for Auction Service        |       |          | TODO   |
| Design Wallet Transaction Schema           |       |          | TODO   |
| UI Mockups for Auction Listing & Search    |       |          | TODO   |

---

## References

- **Epic: User Management:** [docs/epics/user-management/](../epics/user-management/)
- **Epic: Auction Service:** [docs/epics/auction/epic-1.md](../epics/auction/epic-1.md)
- **Epic: Wallet Service:** [docs/epics/wallet/draft.md](../epics/wallet/draft.md)
- **Architecture Overview:** [docs/architecture.md](../architecture.md)

---

## Notes

- Focus for the next sprint will be shifting from Identity to core Auction/Bidding logic.
- Real-time performance is critical for the Bidding experience.
