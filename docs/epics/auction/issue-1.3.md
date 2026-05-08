### **Issue 1.3: Auction Participation Registration**

**Story ID:** `AUCTION-103`
**Issue Type:** Feature
**Priority:** P1 - High

**As a** registered user
**I want to** explicitly register for an auction and pay the mandatory deposit
**So that** I am authorized to place bids and participate in the auction

#### Acceptance Criteria

**Scenario 1: Register for Auction (Sufficient Funds)**

- **Given** I am a logged-in user
- **And** I am on the auction detail page
- **And** I have NOT registered for this auction yet
- **When** I click the "Register to Participate" button
- **Then** the system should:
  1. Verify I am not the owner of the auction
  2. Verify the auction is in `ACTIVE` status
  3. Call Wallet Service to lock the `deposit_amount` specified by the auction
  4. Create a registration record in the `auction_registrations` table
  5. Return success message
  6. Update UI to show the bidding interface (Unlock "Place Bid" button)

**Scenario 2: Register for Auction (Insufficient Funds)**

- **Given** I am a logged-in user
- **When** I click "Register to Participate"
- **And** my wallet balance is less than the required `deposit_amount`
- **Then** the system should:
  1. Return `400 Bad Request` with message: "Insufficient funds to pay the deposit."
  2. Prompt me to top-up my wallet (link to Deposit page)

**Scenario 3: Attempt to Bid Without Registration**

- **Given** I am a logged-in user
- **And** I have NOT registered for the auction
- **When** I attempt to place a bid (direct API call)
- **Then** the system should:
  1. Return `403 Forbidden`
  2. Error message: "You must register and pay the deposit before placing a bid."

**Scenario 4: View My Registrations**

- **Given** I am a logged-in user
- **When** I visit my profile or "My Bids" section
- **Then** I should see a list of auctions I have registered for, including:
  - Registration timestamp
  - Locked deposit amount
  - Current auction status

---

#### Technical Implementation Notes

**API Endpoints:**

```
POST   /api/v1/auctions/{id}/register        - Register for participation
GET    /api/v1/auctions/{id}/registration    - Check my registration status
GET    /api/v1/auctions/my-registrations     - List auctions I've registered for
```

**Database Schema:**

- **Table:** `auction_registrations`
  - `id` (PK)
  - `auction_id` (FK)
  - `user_id` (FK)
  - `deposit_locked_id` (FK to wallet transactions/locks)
  - `status` (ACTIVE, CANCELLED, RELEASED)
  - `created_at`
  - `updated_at`

**Business Logic:**

- **Uniqueness:** A user can only register once per auction.
- **Atomic Operation:** The registration and deposit lock must be atomic (or handled via Saga/Eventual Consistency).
- **Validation:** Bidding Service must check this table before accepting any bid.

---

#### Testing Requirements

**Unit Tests:**

- ✅ Test registration logic (success/fail)
- ✅ Test deposit calculation
- ✅ Test owner restriction (seller cannot register for their own auction)

**Integration Tests:**

- ✅ Full registration flow with Wallet Service lock
- ✅ Verify Bidding Service rejects unregistered users
- ✅ Verify UI state changes after registration

---

#### UI/UX Considerations

**Auction Detail Page:**

- **Before Registration:**
  - Show "Deposit Required: [Amount] VND"
  - Button: "Register to Participate"
  - Bidding input/button is disabled or hidden
- **After Registration:**
  - Show "Registered ✅"
  - Unlock bidding interface
  - Show "Deposit of [Amount] locked"

---

#### Dependencies

- ✅ Identity Service: User validation
- ✅ Wallet Service: Deposit lock API
- ✅ Auction Service: Auction status and metadata

---

#### Definition of Done

- [ ] API endpoints implemented
- [ ] Database migrations for `auction_registrations`
- [ ] Wallet Service integration for locking funds
- [ ] Bidding Service pre-check implemented
- [ ] Unit & Integration tests passing
- [ ] UI integrated and tested
- [ ] Documentation updated
