## 📋 Scope Finalized - Notification Service MVP

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

✅ **Storage:**

- Notification history table (unlimited retention)
- Mark as read/unread
- Delete notifications
- Pagination support

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
                     Notification Service
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

#### **3. Database Schema (Notification Service)**

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

### **D. API Endpoints (Notification Service)**

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

---

## 📝 Issues to Create

### **Issue #1: Notification Service - Core Infrastructure**

- Setup Notification Service module
- Database schema (notifications, email_logs, notification_templates)
- Event listener setup (consume from message broker)
- WebSocket infrastructure for real-time push

### **Issue #2: Email Notification System**

- Email template engine setup
- Multilingual template support (EN/VI)
- SMTP integration (SendGrid/AWS SES)
- Retry mechanism & failure logging
- Admin panel for failed emails

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
- Anti-sniping extension notifications
- Payment reminder scheduler (2 attempts)

### **Issue #5: Notification History & Management**

- List notifications API
- Unread count badge
- Read/unread status toggle
- Delete single/all notifications
- Search/filter notifications
