# Database Schema - Auction Service

## Overview

This document defines the database schema for the Auction Service in the BidNow platform. All tables use the `auction_` prefix to maintain clear service boundaries.

---

## Tables

### 1. `auction_items`

**Purpose:** Stores the core auction listing information and manages the auction lifecycle.

| Column                | Type                       | Constraints                                    | Description                                                     |
| --------------------- | -------------------------- | ---------------------------------------------- | --------------------------------------------------------------- |
| `id`                  | `BIGSERIAL`                | `PRIMARY KEY`                                  | Unique identifier for the auction                               |
| `seller_id`           | `BIGINT`                   | `NOT NULL`                                     | Foreign key to User Service (user who created the auction)      |
| `title`               | `VARCHAR(255)`             | `NOT NULL`                                     | Auction title/name                                              |
| `description`         | `TEXT`                     | `NOT NULL`                                     | Detailed description of the item                                |
| `category_id`         | `BIGINT`                   | `NOT NULL, FOREIGN KEY`                        | References `auction_categories.id`                              |
| `starting_price`      | `DECIMAL(15,2)`            | `NOT NULL, CHECK (starting_price >= 0)`        | Initial bidding price                                           |
| `bid_increment`       | `DECIMAL(15,2)`            | `NOT NULL, CHECK (bid_increment > 0)`          | Minimum bid increase amount                                     |
| `buy_now_price`       | `DECIMAL(15,2)`            | `NULL, CHECK (buy_now_price > starting_price)` | Optional instant purchase price                                 |
| `deposit_amount`      | `DECIMAL(15,2)`            | `NOT NULL, CHECK (deposit_amount >= 0)`        | Required deposit to participate (set by seller)                 |
| `current_price`       | `DECIMAL(15,2)`            | `NOT NULL, DEFAULT starting_price`             | Current highest bid amount (cached from Bidding Service)        |
| `current_winner_id`   | `BIGINT`                   | `NULL`                                         | User ID of current highest bidder (cached from Bidding Service) |
| `total_bids`          | `INTEGER`                  | `NOT NULL, DEFAULT 0`                          | Total number of bids placed (cached counter)                    |
| `status`              | `VARCHAR(20)`              | `NOT NULL, DEFAULT 'DRAFT'`                    | Enum:`DRAFT`, `ACTIVE`, `COMPLETED`, `FAILED`, `CANCELLED`      |
| `start_time`          | `TIMESTAMP WITH TIME ZONE` | `NOT NULL`                                     | Auction start datetime (UTC)                                    |
| `end_time`            | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, CHECK (end_time > start_time)`      | Auction end datetime (UTC) - can be extended by anti-sniping    |
| `original_end_time`   | `TIMESTAMP WITH TIME ZONE` | `NOT NULL`                                     | Original planned end time (before any extensions)               |
| `extension_count`     | `INTEGER`                  | `NOT NULL, DEFAULT 0`                          | Number of times auction was extended due to anti-sniping        |
| `completed_at`        | `TIMESTAMP WITH TIME ZONE` | `NULL`                                         | Actual completion timestamp                                     |
| `winner_id`           | `BIGINT`                   | `NULL`                                         | Final winner user ID (set when auction closes)                  |
| `winner_paid_at`      | `TIMESTAMP WITH TIME ZONE` | `NULL`                                         | Timestamp when winner completed payment                         |
| `payment_deadline`    | `TIMESTAMP WITH TIME ZONE` | `NULL`                                         | Deadline for winner to pay (e.g., 24-48 hours after completion) |
| `cancellation_reason` | `TEXT`                     | `NULL`                                         | Reason for cancellation (if status = CANCELLED)                 |
| `cancelled_by`        | `BIGINT`                   | `NULL`                                         | Admin user ID who cancelled the auction                         |
| `cancelled_at`        | `TIMESTAMP WITH TIME ZONE` | `NULL`                                         | Timestamp of cancellation                                       |
| `created_at`          | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP`          | Record creation timestamp                                       |
| `updated_at`          | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP`          | Last update timestamp                                           |
| `deleted_at`          | `TIMESTAMP WITH TIME ZONE` | `NULL`                                         | Soft delete timestamp                                           |

**Indexes:**

```sql
CREATE INDEX idx_auction_items_seller_id ON auction_items(seller_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_status ON auction_items(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_category_id ON auction_items(category_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_end_time ON auction_items(end_time) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_auction_items_created_at ON auction_items(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_winner_id ON auction_items(winner_id) WHERE winner_id IS NOT NULL AND deleted_at IS NULL;
```

---

### 2. `auction_categories`

**Purpose:** Defines product categories for organizing auctions.

| Column          | Type                       | Constraints                           | Description                                    |
| --------------- | -------------------------- | ------------------------------------- | ---------------------------------------------- |
| `id`            | `BIGSERIAL`                | `PRIMARY KEY`                         | Unique identifier                              |
| `name`          | `VARCHAR(100)`             | `NOT NULL, UNIQUE`                    | Category name (e.g., "Electronics", "Fashion") |
| `slug`          | `VARCHAR(100)`             | `NOT NULL, UNIQUE`                    | URL-friendly category identifier               |
| `description`   | `TEXT`                     | `NULL`                                | Category description                           |
| `parent_id`     | `BIGINT`                   | `NULL, FOREIGN KEY`                   | Self-referencing for hierarchical categories   |
| `icon_url`      | `VARCHAR(500)`             | `NULL`                                | Category icon/image URL                        |
| `display_order` | `INTEGER`                  | `NOT NULL, DEFAULT 0`                 | Sort order for display                         |
| `is_active`     | `BOOLEAN`                  | `NOT NULL, DEFAULT TRUE`              | Whether category is available for new listings |
| `created_at`    | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp                      |
| `updated_at`    | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | Last update timestamp                          |

**Indexes:**

```sql
CREATE INDEX idx_auction_categories_parent_id ON auction_categories(parent_id);
CREATE INDEX idx_auction_categories_slug ON auction_categories(slug);
CREATE INDEX idx_auction_categories_is_active ON auction_categories(is_active);
```

---

### 3. `auction_images`

**Purpose:** Stores multiple images for each auction listing.

| Column          | Type                       | Constraints                           | Description                          |
| --------------- | -------------------------- | ------------------------------------- | ------------------------------------ |
| `id`            | `BIGSERIAL`                | `PRIMARY KEY`                         | Unique identifier                    |
| `auction_id`    | `BIGINT`                   | `NOT NULL, FOREIGN KEY`               | References `auction_items.id`        |
| `image_url`     | `VARCHAR(1000)`            | `NOT NULL`                            | Full URL to image (Cloudinary)       |
| `thumbnail_url` | `VARCHAR(1000)`            | `NULL`                                | Optimized thumbnail URL              |
| `display_order` | `INTEGER`                  | `NOT NULL, DEFAULT 0`                 | Order of images in gallery           |
| `is_primary`    | `BOOLEAN`                  | `NOT NULL, DEFAULT FALSE`             | Whether this is the main/cover image |
| `uploaded_at`   | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | Upload timestamp                     |

**Indexes:**

```sql
CREATE INDEX idx_auction_images_auction_id ON auction_images(auction_id);
CREATE UNIQUE INDEX idx_auction_images_primary ON auction_images(auction_id) WHERE is_primary = TRUE;
```

**Constraints:**

```sql
ALTER TABLE auction_images ADD CONSTRAINT fk_auction_images_auction
    FOREIGN KEY (auction_id) REFERENCES auction_items(id) ON DELETE CASCADE;
```

---

### 4. `auction_status_history`

**Purpose:** Audit trail for auction state transitions (for compliance and debugging).

| Column         | Type                       | Constraints                           | Description                                              |
| -------------- | -------------------------- | ------------------------------------- | -------------------------------------------------------- |
| `id`           | `BIGSERIAL`                | `PRIMARY KEY`                         | Unique identifier                                        |
| `auction_id`   | `BIGINT`                   | `NOT NULL, FOREIGN KEY`               | References `auction_items.id`                            |
| `from_status`  | `VARCHAR(20)`              | `NULL`                                | Previous status (NULL for initial creation)              |
| `to_status`    | `VARCHAR(20)`              | `NOT NULL`                            | New status                                               |
| `reason`       | `TEXT`                     | `NULL`                                | Reason for transition (e.g., "Auto-closed by scheduler") |
| `triggered_by` | `BIGINT`                   | `NULL`                                | User ID who triggered the change (NULL for system)       |
| `metadata`     | `JSONB`                    | `NULL`                                | Additional context (e.g., winner_id, bid_count)          |
| `created_at`   | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | Transition timestamp                                     |

**Indexes:**

```sql
CREATE INDEX idx_auction_status_history_auction_id ON auction_status_history(auction_id);
CREATE INDEX idx_auction_status_history_created_at ON auction_status_history(created_at DESC);
```

**Constraints:**

```sql
ALTER TABLE auction_status_history ADD CONSTRAINT fk_auction_status_history_auction
    FOREIGN KEY (auction_id) REFERENCES auction_items(id) ON DELETE CASCADE;
```

---

### 5. `auction_extensions`

**Purpose:** Track anti-sniping extensions for transparency and analytics.

| Column                       | Type                       | Constraints                           | Description                                            |
| ---------------------------- | -------------------------- | ------------------------------------- | ------------------------------------------------------ |
| `id`                         | `BIGSERIAL`                | `PRIMARY KEY`                         | Unique identifier                                      |
| `auction_id`                 | `BIGINT`                   | `NOT NULL, FOREIGN KEY`               | References `auction_items.id`                          |
| `previous_end_time`          | `TIMESTAMP WITH TIME ZONE` | `NOT NULL`                            | End time before extension                              |
| `new_end_time`               | `TIMESTAMP WITH TIME ZONE` | `NOT NULL`                            | End time after extension                               |
| `extension_duration_seconds` | `INTEGER`                  | `NOT NULL`                            | Duration added (e.g., 300 for 5 minutes)               |
| `triggered_by_bid_id`        | `BIGINT`                   | `NULL`                                | Bid ID that triggered extension (from Bidding Service) |
| `triggered_by_user_id`       | `BIGINT`                   | `NOT NULL`                            | User who placed the triggering bid                     |
| `created_at`                 | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | Extension timestamp                                    |

**Indexes:**

```sql
CREATE INDEX idx_auction_extensions_auction_id ON auction_extensions(auction_id);
CREATE INDEX idx_auction_extensions_created_at ON auction_extensions(created_at DESC);
```

**Constraints:**

```sql
ALTER TABLE auction_extensions ADD CONSTRAINT fk_auction_extensions_auction
    FOREIGN KEY (auction_id) REFERENCES auction_items(id) ON DELETE CASCADE;
```

---

## Relationships Diagram

```
auction_categories
    ↓ (1:N)
auction_items ←──┐
    ↓ (1:N)      │
    ├─→ auction_images
    ├─→ auction_status_history
    └─→ auction_extensions
```

---

## Key Design Decisions

### 1. **Caching Strategy**

- `current_price`, `current_winner_id`, `total_bids` are cached in `auction_items` from Bidding Service
- This denormalization reduces cross-service queries for public browsing
- Updated via event listeners when BID_PLACED events arrive

### 2. **Soft Deletes**

- `deleted_at` in `auction_items` for audit compliance
- Allows recovery and historical analysis

### 3. **UTC Timestamps**

- All times stored in UTC (`TIMESTAMP WITH TIME ZONE`)
- Frontend converts to user's local timezone

### 4. **Anti-Sniping Tracking**

- `original_end_time` preserves initial schedule
- `extension_count` caps maximum extensions (prevent infinite loops)
- `auction_extensions` table provides full audit trail

### 5. **Status Enum Values**

- `DRAFT`: Created but not yet published
- `ACTIVE`: Live and accepting bids
- `COMPLETED`: Ended successfully with a winner
- `FAILED`: Ended with no bids or winner didn't pay
- `CANCELLED`: Manually terminated by admin

### 6. **Deposit Amount**

- Stored in `auction_items.deposit_amount` (set by seller)
- Wallet Service validates bidder has sufficient balance
- Auction Service doesn't store individual deposits (that's Wallet Service's job)

---

## Migration Considerations

### Initial Seed Data

```sql
-- Seed basic categories
INSERT INTO auction_categories (name, slug, description, display_order) VALUES
    ('Electronics', 'electronics', 'Phones, laptops, gadgets', 1),
    ('Fashion', 'fashion', 'Clothing, accessories', 2),
    ('Home & Garden', 'home-garden', 'Furniture, decor', 3),
    ('Collectibles', 'collectibles', 'Art, vintage items', 4),
    ('Sports', 'sports', 'Equipment, memorabilia', 5);
```

### Triggers

```sql
-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_auction_items_updated_at BEFORE UPDATE ON auction_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auction_categories_updated_at BEFORE UPDATE ON auction_categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

---

## Notes

- **Foreign Keys to User Service:** `seller_id`, `current_winner_id`, `winner_id`, `triggered_by`, `cancelled_by` reference the User Service database. These are stored as `BIGINT` but not enforced with FK constraints due to microservice boundaries. Validation happens at application level.
- **Image Storage:** Actual files stored in Cloudinary; only URLs stored in database.
- **Search Optimization:** For full-text search (Issue 5.3), consider adding `tsvector` column or using PostgreSQL full-text search on `title` and `description`.

---

_This schema supports all core features defined in Epic 1 and Epic 5, with extensibility for future enhancements._
