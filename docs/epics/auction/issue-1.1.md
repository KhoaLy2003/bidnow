### **Issue 1.1: Seller Auction Management**

**Story ID:** `AUCTION-101`
**Issue Type:** Feature
**Priority:** P0 - Critical

**As a** registered user with seller role
**I want to** create, view, and manage my auction listings
**So that** I can sell items through the platform and track my sales activity

#### Acceptance Criteria

**Scenario 1: Create New Auction**

- **Given** I am a logged-in seller
- **When** I submit a new auction with:
  - Title (required, 5-255 characters)
  - Description (required, min 20 characters)
  - Category selection (required)
  - At least 1 image, max 10 images (required)
  - Starting price (required, > 0)
  - Bid increment (required, > 0)
  - Deposit amount (required, ≥ 0, set by seller)
  - Buy It Now price (optional, must be > starting price if provided)
  - Auction duration (required, 1 hour to 30 days)
- **Then** the system should:
  - Validate all input fields
  - Upload images to Cloudinary
  - Create auction record with status = `ACTIVE`
  - Set `start_time` = current timestamp
  - Calculate `end_time` = start_time + duration
  - Set `original_end_time` = end_time
  - Return auction ID and success message
  - Emit `AUCTION_CREATED` event to message broker

**Validation Rules:**

- Title: Not empty, 5-255 chars, alphanumeric + basic punctuation
- Starting price: Positive decimal, max 2 decimal places
- Bid increment: Positive decimal, ≥ 0.01
- Buy It Now: If provided, must be > starting_price by at least 2x bid_increment
- Deposit amount: Non-negative, typically 5-20% of starting price (seller decides)
- Duration: Integer between 1 hour and 720 hours (30 days)
- Images: JPEG/PNG, max 5MB each, min 1 required

**Error Handling:**

- `400 Bad Request`: Invalid input data with specific field errors
- `401 Unauthorized`: User not logged in
- `413 Payload Too Large`: Image exceeds size limit
- `500 Internal Server Error`: Cloudinary upload fails

---

**Scenario 2: View My Active Auctions**

- **Given** I am a logged-in seller
- **When** I request my active auctions with pagination (page size = 20)
- **Then** the system should:
  - Return only my auctions where status = `ACTIVE`
  - Include: auction ID, title, primary image, current_price, total_bids, end_time
  - Sort by end_time ascending (ending soonest first)
  - Support pagination (page number, total pages, total count)
  - Filter by category (optional query param)
  - Exclude soft-deleted records (deleted_at IS NULL)

---

**Scenario 3: View My Historical Auctions**

- **Given** I am a logged-in seller
- **When** I request my historical auctions
- **Then** the system should:
  - Return only my auctions where status IN (`COMPLETED`, `FAILED`, `CANCELLED`)
  - Include: auction ID, title, primary image, final_price (current_price), winner details (if COMPLETED), completion timestamp
  - Sort by completed_at descending (most recent first)
  - Support filtering by status
  - Support date range filtering (created between X and Y)

**Additional Fields for Historical:**

- `completed_at`: Actual closure timestamp
- `winner_id`: Winner user ID (if status = COMPLETED)
- `winner_paid_at`: Payment timestamp (if paid)
- `cancellation_reason`: Text (if status = CANCELLED)

**Scenario 4: View Single Auction Details**

- **Given** I am a logged-in seller
- **When** I request details for my auction by ID
- **Then** the system should:
  - Verify I am the owner (seller_id = my user ID)
  - Return full auction details including:
    - All images with display order
    - Category details
    - Pricing breakdown (starting, current, buy it now)
    - Time information (start, end, original_end, extensions)
    - Current winner info (if exists)
    - Status history log
  - Return `403 Forbidden` if I'm not the owner

---

**Scenario 5: Update Auction**

- **Given** I am a logged-in seller and I own an auction
- **When** I request to update the auction details
- **Then** the system should:
  - Verify I am the owner
  - Verify the auction has not started (`start_time` > `now` OR status = `DRAFT`)
  - Allow updating all fields (Title, Description, Category, Images, Pricing, etc.)
  - Validate the new input data
  - Update the record and return success
  - Return `403 Forbidden` if I'm not the owner
  - Return `400 Bad Request` if the auction has already started

---

**Scenario 6: Delete Auction**

- **Given** I am a logged-in seller and I own an auction
- **When** I request to delete the auction
- **Then** the system should:
  - Verify I am the owner
  - Verify the auction has not started (`start_time` > `now` OR status = `DRAFT`)
  - Perform soft-delete (set `deleted_at` = current timestamp)
  - Return success message
  - Return `400 Bad Request` if the auction has already started

---

#### Technical Implementation Notes

**API Endpoints:**

```
POST   /api/v1/auctions                      - Create auction
GET    /api/v1/auctions/my-auctions         - List my auctions (active + historical)
GET    /api/v1/auctions/my-auctions/active  - Filter active only
GET    /api/v1/auctions/my-auctions/history - Filter historical only
GET    /api/v1/auctions/{id}                - Get auction details
PUT    /api/v1/auctions/{id}                - Update auction (Allowed only BEFORE start_time)
DELETE /api/v1/auctions/{id}                - Delete auction (Allowed only BEFORE start_time)
```

**Business Logic:**

- **Ownership Validation:** Every request must verify JWT token and match seller_id
- **Update/Delete Constraint:** Allowed only if `status` is `DRAFT` or `start_time > now()`. Once an auction is `ACTIVE` and `now() >= start_time`, it is immutable for the seller (except for admin cancellation).
- **Image Update:** If images are changed, old images in Cloudinary should be marked for deletion (or handled via cleanup policy) and new ones uploaded.
- **Audit Logging:** Insert record into `auction_status_history` on update/deletion.

**Database Queries:**

- Use indexes on `seller_id` and `status` for fast filtering
- Use `deleted_at IS NULL` filter on all queries (soft delete support)
- Paginate using `LIMIT` and `OFFSET` with total count query

**Event Emission:**

```json
{
  "eventType": "AUCTION_CREATED",
  "auctionId": 123,
  "sellerId": 456,
  "startingPrice": 100.0,
  "endTime": "2026-05-01T14:30:00Z",
  "timestamp": "2026-04-29T10:00:00Z"
}
```

---

#### Testing Requirements

**Unit Tests:**

- ✅ Validate input sanitization (SQL injection, XSS prevention)
- ✅ Test deposit amount calculation logic
- ✅ Test date validation (end_time > start_time)
- ✅ Test Buy It Now price validation
- ✅ Mock Cloudinary service for image upload

**Integration Tests:**

- ✅ Full auction creation flow with real database
- ✅ Test pagination edge cases (empty results, last page)
- ✅ Test concurrent auction creation by same seller
- ✅ Verify event emission to message broker

**Security Tests:**

- ✅ Attempt to view another seller's auction details (expect 403)
- ✅ Attempt SQL injection in title/description
- ✅ Upload malicious file disguised as image
- ⚠️ Attempt to update/delete an auction that has already started (expect 400)
- ⚠️ Attempt to update/delete another seller's auction (expect 403)

---

#### UI/UX Considerations

**Create Auction Form:**

- Multi-step wizard: Basic Info → Images → Pricing → Review
- Real-time validation with inline error messages
- Image upload with drag-and-drop and preview
- Deposit calculator helper (suggest 10% of starting price)
- Duration selector with preset options (1 day, 3 days, 7 days, custom)

**My Auctions Dashboard:**

- Tabs: "Active" (default) | "Historical"
- Quick stats card: Total active, Total bids received, Ending soon count
- Action buttons: View, Edit (Before start), Delete (Before start), Cancel (Active auctions, admin only)
- Status badges with color coding (DRAFT=yellow, ACTIVE=green, COMPLETED=blue, FAILED=red, CANCELLED=gray)

---

#### Dependencies

- ✅ Identity Service: JWT validation and user info retrieval
- ✅ Cloudinary: Image storage service integration
- ⚠️ Message Broker: Must be configured before deployment
- ⚠️ Database: `auction_items`, `auction_categories`, `auction_images` tables created

---

#### Definition of Done

- [ ] API endpoints implemented and tested
- [ ] Input validation with comprehensive error messages
- [ ] Cloudinary integration working (upload + thumbnail generation)
- [ ] Database transactions for atomic operations
- [ ] Event emission to message broker verified
- [ ] Unit test coverage ≥ 80%
- [ ] Integration tests passing
- [ ] API documentation (Swagger) generated
- [ ] Code reviewed and approved
- [ ] Deployed to staging environment
- [ ] Manual QA testing completed
- [ ] Performance tested (1000 concurrent requests)

---

#### Estimated Effort

**Story Points:** 13 (Large)
**Development:** 5 days
**Testing:** 2 days
**Total:** 7 days
