### **Issue 1.2: Admin Auction Moderation**

**Story ID:** `AUCTION-102`
**Issue Type:** Feature
**Priority:** P1 - High

**As an** admin user
**I want to** cancel any auction that violates platform policies
**So that** I can maintain trust & safety on the platform

#### Acceptance Criteria

**Scenario 1: Cancel Active Auction**

- **Given** I am a logged-in admin user
- **And** an auction exists with status = `ACTIVE`
- **When** I submit a cancellation request with:
  - Auction ID
  - Cancellation reason (required, min 10 characters)
- **Then** the system should:
  1. Verify I have admin role (check JWT claims or User Service)
  2. Validate auction is in `ACTIVE` status (cannot cancel COMPLETED/FAILED)
  3. Update auction record:
     - status = `CANCELLED`
     - cancellation_reason = provided reason
     - cancelled_by = my admin user ID
     - cancelled_at = current timestamp
     - completed_at = current timestamp
  4. Insert audit record into `auction_status_history`
  5. Emit `AUCTION_CANCELLED` event to message broker:
     ```json
     {
       "eventType": "AUCTION_CANCELLED",
       "auctionId": 123,
       "sellerId": 456,
       "reason": "Counterfeit product detected",
       "cancelledBy": 999,
       "totalBidsToRefund": 15,
       "timestamp": "2026-04-29T15:00:00Z"
     }
     ```
  6. Return success response with cancellation details

**Expected Side Effects:**

- Wallet Service receives event and:
  - Refunds deposits to ALL bidders who participated
  - Does NOT penalize the seller (admin cancellation is not seller's fault)
- Notification Service sends:
  - Email to seller: "Your auction was cancelled by admin - Reason: [reason]"
  - Email to all bidders: "Auction you participated in was cancelled - Your deposit has been refunded"
  - In-app notification to seller and bidders

---

**Scenario 2: Attempt to Cancel Completed Auction**

- **Given** I am a logged-in admin
- **And** an auction exists with status = `COMPLETED`
- **When** I attempt to cancel it
- **Then** the system should:
  - Return `400 Bad Request`
  - Error message: "Cannot cancel auction - Status is COMPLETED. Auction has already concluded."

**Business Rule:**

- Only `ACTIVE` auctions can be cancelled
- `COMPLETED`, `FAILED`, or already `CANCELLED` auctions are immutable

---

**Scenario 3: Audit Log Visibility**

- **Given** I am a logged-in admin
- **When** I view auction details
- **Then** I should see:
  - Full status history with timestamps
  - Admin actions highlighted (who cancelled, when, why)
  - All extensions applied (anti-sniping audit trail)

---

#### Technical Implementation Notes

**API Endpoints:**

```
POST   /api/v1/admin/auctions/{id}/cancel     - Cancel auction
GET    /api/v1/admin/auctions/{id}/audit      - View full audit trail
GET    /api/v1/admin/auctions/flagged         - List flagged auctions (future)
```

**Authorization:**

- Use Spring Security `@PreAuthorize("hasRole('ADMIN')")`
- Validate JWT token contains admin role claim
- Log all admin actions for compliance

---

#### Testing Requirements

**Unit Tests:**

- ✅ Test cancellation of ACTIVE auction (success)
- ✅ Test rejection of COMPLETED auction cancellation (400 error)
- ✅ Test rejection of unauthorized user (403 error)
- ✅ Test audit log insertion

**Integration Tests:**

- ✅ Full cancellation flow with event emission
- ✅ Verify Wallet Service receives refund event (mock)
- ✅ Test concurrent cancellation attempts (pessimistic locking)

**Security Tests:**

- ✅ Attempt cancellation without admin role (expect 403)
- ✅ Attempt cancellation with expired JWT (expect 401)

---

#### UI/UX Considerations

**Admin Dashboard:**

- Search bar: Find auction by ID or title
- Action button: "Cancel Auction" (requires confirmation modal)
- Confirmation modal:
  - "Are you sure? This action cannot be undone."
  - Reason text area (required, min 10 chars)
  - "Confirm Cancellation" button (red, destructive action)

**Audit Log View:**

- Timeline visualization showing all state transitions
- Color-coded events (created=green, extended=yellow, cancelled=red)
- Admin actions highlighted with user badge

---

#### Dependencies

- ✅ Identity Service: Admin role validation
- ✅ Wallet Service: Must listen to `AUCTION_CANCELLED` events
- ✅ Notification Service: Must listen to `AUCTION_CANCELLED` events

---

#### Definition of Done

- [ ] API endpoint implemented with admin authorization
- [ ] Validation for cancellable statuses
- [ ] Event emission to message broker verified
- [ ] Audit logging complete
- [ ] Unit test coverage ≥ 80%
- [ ] Integration tests with event consumers
- [ ] Security tests passed (role-based access control)
- [ ] API documentation updated (Swagger)
- [ ] Code reviewed and approved
- [ ] Deployed to staging environment
- [ ] Manual QA testing by admin user completed

---

## Epic Dependencies

### Upstream Dependencies (Must Complete First)

- ✅ Identity Service: User authentication and role management
- ✅ Database schema: All tables created with proper indexes
- ✅ Message Broker: RabbitMQ/Kafka configured with queues
- ✅ Cloudinary: Account setup and API credentials

### Downstream Dependencies (Will Consume Our Events)

- ⚠️ Bidding Service: Must emit `BID_PLACED` events (for anti-sniping)
- ⚠️ Wallet Service: Must consume `AUCTION_ENDED`, `AUCTION_CANCELLED` events (for refunds)
- ⚠️ Notification Service: Must consume all auction events (for emails/WebSockets)

---

## Risks & Mitigation

| Risk                                            | Probability | Impact   | Mitigation                                        |
| ----------------------------------------------- | ----------- | -------- | ------------------------------------------------- |
| Scheduler fails to close auctions on time       | Medium      | Critical | Implement distributed locking + monitoring alerts |
| Race condition between anti-sniping and closure | Medium      | High     | Use pessimistic locking on auction record         |
| Image upload to Cloudinary times out            | Low         | Medium   | Implement retry logic with exponential backoff    |
| Message broker unavailable during closure       | Low         | Critical | Store events in database outbox, retry async      |
| Admin abuses cancellation power                 | Low         | Medium   | Audit all admin actions, require reason field     |

---

## Success Criteria Review

At the end of this epic, we should have:

- ✅ Sellers can create and manage auctions in <3 minutes
- ✅ 99.9% of auctions close automatically within 1 second of end time
- ✅ Anti-sniping extends auctions correctly without infinite loops
- ✅ Admin can cancel fraudulent auctions within 2 minutes
- ✅ All state transitions logged in audit trail
- ✅ Events emitted for downstream services to consume
- ✅ Test coverage ≥ 80% on business logic
- ✅ API documentation complete and up-to-date

---

#### Estimated Effort

**Story Points:** 5 (Medium)
**Development:** 2 days
**Testing:** 1 day
**Total:** 3 days
