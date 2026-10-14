-- liquibase formatted sql

-- changeset bidnow:wallet_005
CREATE TABLE auction_cancellations
(
    auction_id   UUID PRIMARY KEY,
    cancelled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
