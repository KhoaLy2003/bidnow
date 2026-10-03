> **Superseded where it differs from what was built.** The implemented design is in
> [the notification epic roadmap](../../superpowers/plans/2026-09-30-notification-epic-roadmap.md) (Decisions and
> per-story Refinements). Notes marked **Superseded** below point out each difference; the original text is kept
> for history.

## 📋 Scope Finalized - Media Service MVP

### **A. Channels Implementation**

✅ **Real-time (WebSocket/In-app):**

- Outbid alerts (with batching)
- Auction ending soon
- Auction won/lost
- First bid on your auction
- Anti-sniping extension triggered
- New bid on your auction (for seller)

✅ **Email:**

- Registration welcome
- Auction created successfully
- Auction won
- Payment reminder (2 time)
- Auction lost
- Deposit refunded
- Payment successful

> **Superseded:** there is no separate "Auction won" email. The "You won, pay within 48h" email is payment reminder #1, sent from wallet's `PaymentEvent REQUIRED`; `AuctionEndedEvent` only creates the in-app `AUCTION_WON` (Decision 3).

✅ **Storage:**

- Notification history table (unlimited retention)
- Mark as read/unread
- Delete notifications
- Pagination support

> **Superseded:** "Delete" is a soft delete, and the history lives in `media_notifications` (see [the schema doc](../../database/notification-service.schema.md)). Roadmap Decision 6.

### **B. Key Features & Business Rules**

#### **1. Auction Ending Soon - Seller Configurable**

```
Seller sets:
- Enable/disable ending soon notification
- Time threshold (default: 15 minutes, 1 hour, 24 hours - seller can choose multiple)
- Who receives: All active bidders
```

**Example:**

- Seller creates auction and sets: "Notify bidders at 1 hour and 15 minutes before end"
- System sends notifications accordingly

> **Superseded:** ending soon is a platform default: every auction notifies its active bidders at 60 and 15 minutes before the end (`auction.ending-soon.thresholds-minutes`). There is no seller setting, enable/disable switch or custom thresholds (Decision 7).

#### **2. Outbid Alert - Smart Batching**

```
Logic:
- If user is outbid multiple times within 5 minutes
- Batch and send 1 notification: "You've been outbid 3 times on [Item Name]"
- Send immediately if no other outbid within 5 min window
```

**Example:**

- 10:00 - User A bid $100
- 10:01 - User B bid $110 (User A outbid - wait)
- 10:02 - User C bid $120 (User A outbid again - wait)
- 10:03 - User D bid $130 (User A outbid again - wait)
- 10:06 - Send 1 notification to User A: "You've been outbid 3 times. Current price: $130"

#### **3. Payment Reminder - 2 Attempts**

```
Timeline:
- T0: Auction ends, winner selected → Send email #1 "Congratulations! Please pay within 48 hours"
- T0 + 24h: If not paid → Send email #2 "Reminder: 24 hours left to complete payment"
- T0 + 48h: If still not paid → Mark as failed, forfeit deposit, no more emails
```

> **Superseded:** email #1 is the won/payment-required email sent when wallet publishes `PaymentEvent REQUIRED` (not a separate auction-won email), and reminder #2 is sent when 24 h are left to the deadline (Decision 3).

#### **4. Anti-Sniping Extension Notification**

```
When triggered:
- Send real-time notification to:
  ✅ All active bidders (currently participating)
  ✅ Seller
  ❌ No email (real-time only)

Message:
- To bidders: "Auction extended by 5 minutes due to last-minute bid"
- To seller: "Your auction has been extended by 5 minutes"
```

---

### **C. Technical Implementation Details**

#### **1. Event-Driven Architecture**

```
Service A → Emit Event → Message Broker (RabbitMQ/Kafka)
                               ↓
                     Media Service
                               ↓
                    ┌─────────┴─────────┐
                    ↓                   ↓
            WebSocket Push          Email Queue
```

#### **2. Email Failure Handling**

```
Retry Logic:
- Attempt 1: Immediate send
- Attempt 2: Retry after 5 minutes
- Attempt 3: Retry after 15 minutes
- If all fail → Mark as FAILED and create admin alert

Admin Dashboard:
- View failed emails
- Retry manually
- See failure reason (invalid email, SMTP error, etc.)
```

> **Superseded:** there is no automatic retry and no admin alert. An email is sent once; a failure is logged as `FAILED` in `media_email_logs`, and the in-app notification is still created. An admin email-log list and page exist, but `POST /api/v1/admin/email-logs/{id}/retry` is not functional (`EmailServiceImpl.retryEmail` throws `UnsupportedOperationException`). Retry is out of scope (Decision 11, NOTIF-103).

#### **3. Database Schema (Media Service)**

> **Superseded:** the sketches below are the original design. The tables are `media_*` (`media_notifications`, `media_email_logs`, `media_notification_templates`, ...), with different columns. See [the notification service schema](../../database/notification-service.schema.md).

**Table: notifications**

```
- id (UUID, PK)
- user_id (UUID, FK)
- type (ENUM: OUTBID, AUCTION_WON, PAYMENT_REMINDER, etc.)
- channel (ENUM: REALTIME, EMAIL)
- title (VARCHAR)
- message (TEXT)
- metadata (JSONB) - extra data like auction_id, bid_amount
- action_url (VARCHAR) - deep link
- read (BOOLEAN, default: false)
- deleted (BOOLEAN, default: false)
- created_at (TIMESTAMP)
```

**Table: email_logs**

```
- id (UUID, PK)
- notification_id (UUID, FK, nullable)
- recipient_email (VARCHAR)
- subject (VARCHAR)
- template_name (VARCHAR)
- status (ENUM: SENT, FAILED, PENDING, RETRY)
- failure_reason (TEXT, nullable)
- retry_count (INT, default: 0)
- sent_at (TIMESTAMP, nullable)
- created_at (TIMESTAMP)
```

**Table: notification_templates**

```
- id (UUID, PK)
- name (VARCHAR) - e.g., "AUCTION_WON_EMAIL_EN"
- type (ENUM: EMAIL, REALTIME)
- language (ENUM: EN, VI)
- subject (VARCHAR, for email)
- body_html (TEXT, for email)
- body_text (TEXT)
- variables (JSONB) - placeholders like {userName}, {auctionTitle}
- active (BOOLEAN)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)
```

#### **4. Template Variables Support**

```
Available variables:
- {userName} / {userDisplayName}
- {auctionTitle}
- {auctionId}
- {currentPrice}
- {bidAmount}
- {timeRemaining}
- {paymentDeadline}
- {actionUrl}
```

---

### **D. API Endpoints (Media Service)**

```
# For Users
GET    /api/v1/notifications              # List user's notifications (paginated)
GET    /api/v1/notifications/unread-count # Get unread count
PUT    /api/v1/notifications/{id}/read    # Mark as read
PUT    /api/v1/notifications/read-all     # Mark all as read
DELETE /api/v1/notifications/{id}         # Delete notification
DELETE /api/v1/notifications/all          # Delete all notifications

# For Internal Services (Inter-service communication)
POST   /api/v1/internal/notifications/send   # Trigger notification

# For Admin
GET    /api/v1/admin/email-logs           # View failed emails
POST   /api/v1/admin/email-logs/{id}/retry # Retry failed email
GET    /api/v1/admin/templates            # Manage templates
PUT    /api/v1/admin/templates/{id}       # Update template
```

> **Superseded:** the user inbox is `/api/v1/notifications` with `unread-count`, `{id}` (get, marks read), `{id}/read`, `{id}/unread`, `mark-all-read`, `delete-all` and `delete-read`; see "Inbox REST" in [the architecture doc](../../architecture.md). There is no internal `send` endpoint (Decision 9). Admin endpoints exist for email logs (list; retry is not functional) and templates (create, update, get, list, test send `/{id}/test`, group send `/{id}/send`).

---

### **E. Event Types to Listen**

| Event Name                | Source Service   | Notification Type           | Channel           |
| ------------------------- | ---------------- | --------------------------- | ----------------- |
| USER_REGISTERED           | Identity Service | Welcome email               | Email             |
| AUCTION_CREATED           | Auction Service  | Listing created             | Email             |
| BID_PLACED                | Bidding Service  | Outbid alert (batched)      | Real-time         |
| BID_PLACED                | Bidding Service  | New bid on auction (seller) | Real-time         |
| AUCTION_ENDING_SOON       | Auction Service  | Ending soon                 | Real-time         |
| AUCTION_EXTENDED          | Bidding Service  | Anti-sniping extension      | Real-time         |
| AUCTION_ENDED_WITH_WINNER | Auction Service  | Auction won                 | Real-time + Email |
| AUCTION_ENDED_WITH_WINNER | Auction Service  | Auction lost (losers)       | Real-time + Email |
| PAYMENT_REQUIRED          | Wallet Service   | Payment reminder #1         | Email             |
| PAYMENT_REMINDER_24H      | Wallet Service   | Payment reminder #2         | Email             |
| PAYMENT_COMPLETED         | Wallet Service   | Payment successful          | Email             |
| DEPOSIT_REFUNDED          | Wallet Service   | Refund notification         | Email             |

> **Superseded:** the winner gets only the in-app `AUCTION_WON` from `AUCTION_ENDED_WITH_WINNER`; the winner email comes from `PAYMENT_REQUIRED` (Decision 3). Losers and active bidders come from media's own participants projection (Decision 4).

---

### **F. Multilingual Support (EN/VI)**

**Implementation approach:**

```
1. Template naming convention:
   - AUCTION_WON_EMAIL_EN
   - AUCTION_WON_EMAIL_VI
   - AUCTION_WON_REALTIME_EN
   - AUCTION_WON_REALTIME_VI

2. User language preference:
   - Get from user.language field (default: EN)
   - Select template based on language

3. Template management:
   - Admin can edit templates via UI
   - Preview before saving
   - Version control (optional for post-MVP)
```

> **Superseded:** language comes from user-service preferences (Decision 5). In-app copy is EN only. Admins can manage templates through `/api/v1/admin/templates` and the admin templates page, but there is no preview endpoint, and template versioning is out of scope (Decision 11).

---

## 📝 Issues to Create

### **Issue #1: Media Service - Core Infrastructure**

- Setup Media Service module
- Database schema (notifications, email_logs, notification_templates)
- Event listener setup (consume from message broker)
- WebSocket infrastructure for real-time push

### **Issue #2: Email Notification System**

- Email template engine setup
- Multilingual template support (EN/VI)
- SMTP integration (SendGrid/AWS SES)
- Retry mechanism & failure logging
- Admin panel for failed emails

> **Superseded:** automatic and manual retry are out of scope; failures are only logged (Decision 11). An admin email-log page exists, but its retry action is not functional.

### **Issue #3: Real-time Notification System**

- WebSocket broadcast mechanism
- In-app notification display (frontend)
- Notification center UI (bell icon, dropdown)
- Mark as read/unread
- Delete notifications
- Pagination

### **Issue #4: Smart Notification Features**

- Outbid alert batching logic
- Seller-configurable "ending soon" settings

> **Superseded:** ending soon uses platform-default thresholds, no seller setting (Decision 7).
- Anti-sniping extension notifications
- Payment reminder scheduler (2 attempts)

### **Issue #5: Notification History & Management**

- List notifications API
- Unread count badge
- Read/unread status toggle
- Delete single/all notifications
- Search/filter notifications
