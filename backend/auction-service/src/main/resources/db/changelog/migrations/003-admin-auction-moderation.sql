-- liquibase formatted sql

--changeset bidnow:auction-admin-moderation
--comment: Add rejection columns to auction_items for admin moderation (Issue #30)

ALTER TABLE auction_items
    ADD COLUMN rejection_reason TEXT NULL,
    ADD COLUMN rejected_by      UUID NULL,
    ADD COLUMN rejected_at      TIMESTAMP WITH TIME ZONE NULL;
