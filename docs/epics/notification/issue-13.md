### Issue #13: Smart Notification Features & Business Logic

**Title:** `Implement smart notification features (outbid batching, schedulers, anti-sniping alerts)`

**Description:**

Implement advanced notification features including outbid alert batching, seller-configurable auction ending alerts, payment reminder scheduler, and anti-sniping extension notifications.

**Tasks:**

**1. Outbid Alert Batching:**

- [ ] Track outbid events within 2-minute window per user per auction
- [ ] Batch multiple outbids into single notification
- [ ] Message format: "You've been outbid X times on [Auction Title]. Current price: $Y"
- [ ] Send batched notification after 2-minute window expires
- [ ] If only 1 outbid in window, send immediately
- [ ] Store batching state in Redis for performance

**2. Auction Ending Soon - Seller Settings:**

- [ ] Add settings to Auction creation form:
  - Enable/disable ending soon notifications
  - Select time thresholds (checkboxes: 24h, 1h, 30min, 15min)
  - Default values from user's global settings
- [ ] Create `auction_notification_settings` table: auction_id, ending_soon_enabled, time_thresholds (array)
- [ ] Create global user settings page for default notification preferences
- [ ] Scheduler job to check auctions and send notifications at specified thresholds
- [ ] Send to all active bidders only (users who have placed bids)
- [ ] Message: "Auction '[Title]' is ending in [time]"

**3. Payment Reminder Scheduler:**

- [ ] Scheduler job runs every hour to check pending payments
- [ ] Reminder #1: Send immediately when auction ends with winner (T0)
  - Email: "Congratulations! Please complete payment within 48 hours"
- [ ] Reminder #2: Send 24 hours after auction end (T0 + 24h)
  - Email: "Reminder: 24 hours remaining to complete payment"
- [ ] After 48 hours (T0 + 48h): Mark as failed, forfeit deposit, stop reminders
- [ ] Track reminder status in database (reminder_count, last_reminder_sent)

**4. Anti-Sniping Extension Notification:**

- [ ] Listen to AUCTION_EXTENDED event from Bidding Service
- [ ] Send real-time notification to:
  - All active bidders: "Auction '[Title]' extended by 5 minutes due to last-minute bid"
  - Seller: "Your auction '[Title]' has been extended by 5 minutes"
- [ ] No email, real-time only
- [ ] Update auction end time in notification metadata

**5. Notification Deduplication:**

- [ ] Prevent duplicate notifications for same event
- [ ] Check if similar notification sent within last 1 minute
- [ ] Use hash of (user_id, type, auction_id) as deduplication key

**6. Scheduler Infrastructure:**

- [ ] Set up Spring Scheduler or Quartz
- [ ] Jobs:
  - Outbid batching processor (runs every 2 minutes)
  - Auction ending soon checker (runs every 5 minutes)
  - Payment reminder checker (runs every hour)
- [ ] Configure job execution logs
- [ ] Handle job failures and retries

**API Endpoints:**

- [ ] `GET /api/v1/settings/notifications` - Get user's default notification settings
- [ ] `PUT /api/v1/settings/notifications` - Update default settings
- [ ] (Auction service will use these settings when creating auctions)

**Deliverables:**

- Outbid batching working with 2-minute window
- Seller can configure ending soon notifications
- Payment reminders sent on schedule (2 attempts)
- Anti-sniping notifications sent in real-time
- All schedulers running reliably
- Deduplication preventing spam

**Dependencies:**

- Issue #9 (Core infrastructure) completed
- Issue #11 (CRUD APIs) completed
- Redis available for batching state
- Auction service and Bidding service emitting events
