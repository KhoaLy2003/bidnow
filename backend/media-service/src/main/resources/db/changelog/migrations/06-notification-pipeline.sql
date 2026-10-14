-- liquibase formatted sql

-- changeset hiep.nguyen:notification-pipeline-dedup-and-soft-delete
-- comment: Idempotent notification inserts keyed by (user_id, dedup_key) and soft delete for the user inbox
ALTER TABLE media_notifications ADD COLUMN dedup_key VARCHAR(200);
UPDATE media_notifications SET dedup_key = 'LEGACY:' || id::text WHERE dedup_key IS NULL;
ALTER TABLE media_notifications ALTER COLUMN dedup_key SET NOT NULL;
ALTER TABLE media_notifications ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE media_notifications
    ADD CONSTRAINT uq_media_notifications_user_dedup UNIQUE (user_id, dedup_key);
CREATE INDEX idx_media_notifications_user_feed
    ON media_notifications (user_id, deleted_at, created_at DESC);
CREATE INDEX idx_media_notifications_user_unread
    ON media_notifications (user_id) WHERE read_at IS NULL AND deleted_at IS NULL;

-- changeset hiep.nguyen:notification-pipeline-auction-projection
-- comment: media-service's own view of auctions and their bidders, fed by Kafka events
CREATE TABLE media_auctions
(
    auction_id UUID PRIMARY KEY,
    title      VARCHAR(255),
    seller_id  UUID,
    end_time   TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE media_auction_participants
(
    auction_id      UUID           NOT NULL,
    user_id         UUID           NOT NULL,
    last_bid_amount NUMERIC(19, 2) NOT NULL,
    last_bid_at     TIMESTAMP      NOT NULL,
    PRIMARY KEY (auction_id, user_id)
);
