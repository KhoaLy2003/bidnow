-- liquibase formatted sql

--changeset bidnow:auction-purchases-index
--comment: Serve the buyer's purchase history (GET /auctions/me/purchases), newest win first

CREATE INDEX idx_auction_items_winner_completed_at
    ON auction_items (winner_id, completed_at DESC)
    WHERE winner_id IS NOT NULL AND deleted_at IS NULL;
