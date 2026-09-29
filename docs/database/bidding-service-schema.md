# Bidding Service Schema (`bidding_db`)

Owned exclusively by bidding-service. Migrations: `backend/bidding-service/src/main/resources/db/changelog/`.

## bids

```sql
CREATE TABLE bids (
    id                        UUID PRIMARY KEY,               -- assigned by bidding-service (the bidId sent to auction-service)
    auction_id                UUID           NOT NULL,        -- auction-service auction id (no FK: separate database)
    bidder_id                 UUID           NOT NULL,        -- identity user id
    amount                    DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    is_auto_bid               BOOLEAN        NOT NULL DEFAULT FALSE,
    is_anti_sniping_triggered BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bids_auction_created        ON bids (auction_id, created_at DESC);          -- auction bid history
CREATE INDEX idx_bids_auction_amount         ON bids (auction_id, amount DESC);              -- highest bid lookups
CREATE INDEX idx_bids_bidder_auction_created ON bids (bidder_id, auction_id, created_at DESC); -- "my bids"
```

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | App-assigned bid id; auction-service stores it as `last_bid_id` |
| auction_id | UUID | NOT NULL | Auction the bid was placed on |
| bidder_id | UUID | NOT NULL | Bidder (from gateway `X-User-Id`) |
| amount | DECIMAL(15,2) | NOT NULL, > 0 | Bid amount |
| is_auto_bid | BOOLEAN | NOT NULL | Placed by auto-bid (Phase 2; always false for now) |
| is_anti_sniping_triggered | BOOLEAN | NOT NULL | This bid extended the auction end time (BID-104) |
| created_at / updated_at | TIMESTAMP | NOT NULL | Managed by `BaseEntity` |

## Redis keys

| Key | Value | TTL |
| :--- | :--- | :--- |
| `bidding:auction:{auctionId}:context` | JSON bid context from auction-service | `bidding.cache.context-ttl-seconds` (600) |
