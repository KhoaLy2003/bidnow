## 6. Media Service Tables (notifications)

Tables are prefixed `media_` (renamed from `notif_` in migration 04). Source of truth: `backend/media-service/src/main/resources/db/changelog/migrations/`.

Migrations, in changelog order: `01` initial notification schema, `02` seeded templates, `03` OTP templates, `04` rename `notif_*` to `media_*` and add `media_assets`, `05` audit logs, `06` notification pipeline (dedup, soft delete, auction projection), `07` event templates, `08` fix literal `\n` in seeded template bodies.

Enum-like columns (`type`, `channel`, `status`, `language`) are plain `VARCHAR` in the database, not Postgres enums. The allowed values are enforced by the Java enums (`@Enumerated(EnumType.STRING)`).

### media_notifications

One row per in-app notification. Written only by `NotificationDispatcher`.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | |
| user_id | UUID | NOT NULL | Recipient (logical FK to identity users) |
| type | VARCHAR(50) | NOT NULL | `NotificationType` name (see the list below) |
| channel | VARCHAR(20) | NOT NULL | `NotificationChannel`; rows written by the dispatcher are always `IN_APP` |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | `NotificationStatus`; rows written by the dispatcher are stored as `SENT`. `READ` is never used (see `read_at`) |
| title | VARCHAR(255) | NOT NULL | |
| message | TEXT | NOT NULL | |
| action_url | VARCHAR(500) | | Frontend link, for example `/auctions/{id}` or `/wallet` |
| auction_id | UUID | | Related auction, when there is one |
| bid_id | UUID | | Legacy column, not set by the dispatcher |
| metadata | JSONB | | Extra payload for the client (amounts, counts) |
| sent_at | TIMESTAMP | | Legacy, not set by the dispatcher |
| read_at | TIMESTAMP | | Read flag: NULL = unread, set = read. Mark-unread sets it back to NULL |
| failed_reason | TEXT | | Legacy, not set by the dispatcher |
| retry_count | INTEGER | DEFAULT 0 | Legacy, unused (there is no retry) |
| dedup_key | VARCHAR(200) | NOT NULL | Added in 06. Idempotency key built by `DedupKeys`, for example `AUCTION_WON:{auctionId}`. Rows that existed before 06 were back-filled with `LEGACY:{id}` |
| deleted_at | TIMESTAMP | | Added in 06. Soft delete: the row is hidden from the inbox, never physically removed |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | | |

**Constraints**
- `uq_media_notifications_user_dedup` UNIQUE (`user_id`, `dedup_key`): one notification per user and key. The dispatcher inserts with `ON CONFLICT (user_id, dedup_key) DO NOTHING`, so a Kafka redelivery inserts nothing and triggers no push and no email.

**Indexes**
- `idx_media_notifications_user_id` (`user_id`)
- `idx_media_notifications_status` (`status`)
- `idx_media_notifications_type` (`type`)
- `idx_media_notifications_created_at` (`created_at` DESC)
- `idx_media_notifications_user_feed` (`user_id`, `deleted_at`, `created_at` DESC): inbox listing
- `idx_media_notifications_user_unread` (`user_id`) WHERE `read_at IS NULL AND deleted_at IS NULL`: partial index for the unread count

### media_email_logs

One row per email send attempt. An email is sent once: a failure is logged as `FAILED` and is not retried.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | |
| notification_id | UUID | | Set by the dispatcher path (`sendTemplateEmail(notificationId, ...)`) to link the email to its in-app notification. NULL for emails sent outside the dispatcher (OTP, admin test and group sends). No foreign key |
| recipient_email | VARCHAR(255) | NOT NULL | |
| subject | VARCHAR(255) | NOT NULL | |
| template_name | VARCHAR(255) | NOT NULL | Resolved template, for example `AUCTION_LOST_VI` |
| status | VARCHAR(255) | NOT NULL | `EmailStatus`: `SENT` or `FAILED` in practice (`PENDING` and `RETRY` exist in the enum but nothing sets them) |
| failure_reason | TEXT | | Set when `status = FAILED` |
| retry_count | INT | NOT NULL, DEFAULT 0 | Always 0 (no retry) |
| sent_at | TIMESTAMP | | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | | |

**Indexes:** none besides the primary key.

### media_notification_templates

Email templates, editable through the admin template API.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | |
| name | VARCHAR(255) | NOT NULL, UNIQUE | `{BASE}_{EN\|VI}`, for example `AUCTION_LOST_VI` |
| type | VARCHAR(255) | NOT NULL | `EMAIL` for every seeded template |
| language | VARCHAR(255) | NOT NULL | `EN` or `VI` |
| subject | VARCHAR(255) | | `{placeholder}` substitution |
| body_html | TEXT | | |
| body_text | TEXT | NOT NULL | |
| variables | JSONB | | Documented variable names |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE | An inactive VI template falls back to EN |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | | |

**Indexes:** the unique index on `name`.

**Name convention.** `TemplateResolver` looks up `{BASE}_{lang}`. If the VI template is missing or inactive it falls back to EN. If neither exists, the email is skipped and an error is logged.

**Seeded templates** (each in `_EN` and `_VI`):
- Migration 02: `WELCOME_EMAIL`, `AUCTION_CREATED`, `AUCTION_WON`, `AUCTION_LOST`, `PAYMENT_REMINDER_1`, `PAYMENT_REMINDER_2`, `PAYMENT_SUCCESSFUL`, `DEPOSIT_REFUNDED`.
- Migration 03: `OTP_VERIFICATION`.
- Migration 07: `AUCTION_CANCELLED`, `PAYMENT_FAILED`, `SALE_PAYMENT_RECEIVED`.
- Migration 08 changes no names: it converts literal `\n` in the 02/03 bodies to real newlines.

### media_user_preferences

**Unused.** Notification preferences (language, email opt-out) live in user-service (`user_preferences`), read through user-service's internal notification-preferences endpoint. The table is kept because the project makes no destructive migrations.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PRIMARY KEY | |
| user_id | UUID | UNIQUE, NOT NULL | |
| email_enabled | BOOLEAN | DEFAULT TRUE | |
| push_enabled | BOOLEAN | DEFAULT TRUE | |
| sms_enabled | BOOLEAN | DEFAULT FALSE | |
| type_preferences | JSONB | DEFAULT per-type JSON | |
| quiet_hours_start | TIME | | |
| quiet_hours_end | TIME | | |
| quiet_hours_timezone | VARCHAR(50) | | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | | |

**Indexes:** `idx_media_user_preferences_user_id` (`user_id`).

### Auction projection (media-owned, fed by `auction-created-topic`, `bid-placed-topic` and `auction-extended-topic` on `media-projection-group`)

media-service keeps its own view of auctions and their bidders, so recipients (bidders, losers, the seller) and auction titles never need a call to another service. `AuctionProjectionConsumer` listens in its own group, `media-projection-group`, separate from `media-service-group`, which the notification handlers use on the same topics. Created in migration 06.

Upserts are order-tolerant, because events can arrive in any order and be redelivered (`AuctionProjectionRepository`):
- `title` and `seller_id` use `COALESCE(EXCLUDED.x, stored.x)`: a null value from an out-of-order event never overwrites a known one. A bid event that arrives before the created event still creates the auction row.
- `end_time` uses `GREATEST(EXCLUDED.end_time, stored.end_time)`: it only moves forward.
- A participant row is overwritten only when the incoming bid is at or after the stored one (`WHERE EXCLUDED.last_bid_at >= last_bid_at`): the newer bid wins per participant, and a late redelivery cannot roll it back.

#### media_auctions

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| auction_id | UUID | PRIMARY KEY | |
| title | VARCHAR(255) | | |
| seller_id | UUID | | |
| end_time | TIMESTAMPTZ | | Current end time, including extensions |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | | |

**Indexes:** the primary key only.

#### media_auction_participants

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| auction_id | UUID | NOT NULL | |
| user_id | UUID | NOT NULL | A bidder on the auction |
| last_bid_amount | NUMERIC(19,2) | NOT NULL | The user's latest bid |
| last_bid_at | TIMESTAMP | NOT NULL | |

**Constraints:** PRIMARY KEY (`auction_id`, `user_id`). **Indexes:** the primary key only.

### Other tables in the same database

These are not part of the notification epic.
- `media_assets`: uploaded files and metadata (migration 04, `idx_media_assets_*`).
- `media_audit_logs`: audit trail written from the `audit-events` topic (migration 05, `idx_media_audit_logs_*`).

### Notification types

`NotificationType` has 21 values. The enum only grows: values are never removed.

- **Account:** `USER_REGISTERED`, `OTP_VERIFICATION`
- **Bidding:** `BID_PLACED`, `BID_OUTBID`, `FIRST_BID`, `NEW_BID`
- **Auction:** `AUCTION_CREATED`, `AUCTION_ENDING_SOON`, `AUCTION_EXTENDED`, `AUCTION_WON`, `AUCTION_LOST`, `AUCTION_UNSOLD`, `AUCTION_CANCELLED`
- **Payment:** `PAYMENT_REQUIRED`, `PAYMENT_REMINDER`, `PAYMENT_RECEIVED`, `PAYMENT_FAILED`, `DEPOSIT_REFUNDED`, `DEPOSIT_FORFEITED`
- **System:** `WATCHLIST_ITEM_STARTING`, `SYSTEM_ANNOUNCEMENT`

Some values are reserved and not emitted by any handler today (`BID_PLACED`, `OTP_VERIFICATION`, `DEPOSIT_FORFEITED`, `WATCHLIST_ITEM_STARTING`, `SYSTEM_ANNOUNCEMENT`).
