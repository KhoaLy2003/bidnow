-- liquibase formatted sql

--changeset bidnow:auction-bid-tracking
--comment: Track the last applied bid for idempotent internal apply-bid (Epic #120, Story 1)

ALTER TABLE auction_items
    ADD COLUMN last_bid_id UUID NULL;
