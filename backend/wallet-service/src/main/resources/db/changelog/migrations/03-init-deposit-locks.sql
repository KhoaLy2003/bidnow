-- liquibase formatted sql

-- changeset bidnow:wallet_003
CREATE TABLE deposit_locks
(
    id          UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    wallet_id   UUID           NOT NULL REFERENCES wallets (id),
    auction_id  UUID           NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    locked_at   TIMESTAMP      NOT NULL,
    released_at TIMESTAMP,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_deposit_locks_wallet_auction UNIQUE (wallet_id, auction_id),
    CONSTRAINT chk_deposit_locks_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX idx_deposit_locks_auction_status ON deposit_locks (auction_id, status);
