-- liquibase formatted sql

-- changeset bidnow:bidding_001
-- comment: Bid ledger for bidding-service (Epic #120, BID-101)
CREATE TABLE bids
(
    id                        UUID PRIMARY KEY,
    auction_id                UUID           NOT NULL,
    bidder_id                 UUID           NOT NULL,
    amount                    DECIMAL(15, 2) NOT NULL,
    is_auto_bid               BOOLEAN        NOT NULL DEFAULT FALSE,
    is_anti_sniping_triggered BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bids_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_bids_auction_created ON bids (auction_id, created_at DESC);
CREATE INDEX idx_bids_auction_amount ON bids (auction_id, amount DESC);
CREATE INDEX idx_bids_bidder_auction_created ON bids (bidder_id, auction_id, created_at DESC);
