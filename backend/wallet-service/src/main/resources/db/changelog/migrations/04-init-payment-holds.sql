-- liquibase formatted sql

-- changeset bidnow:wallet_004
CREATE TABLE payment_holds
(
    id               UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    auction_id       UUID           NOT NULL,
    winner_wallet_id UUID           NOT NULL REFERENCES wallets (id),
    winner_user_id   UUID           NOT NULL,
    seller_user_id   UUID           NOT NULL,
    total_amount     NUMERIC(19, 4) NOT NULL,
    deposit_applied  NUMERIC(19, 4) NOT NULL,
    deposit_lock_id  UUID REFERENCES deposit_locks (id),
    remaining_amount NUMERIC(19, 4) NOT NULL,
    funds_held       BOOLEAN        NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    deadline         TIMESTAMP      NOT NULL,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_holds_auction UNIQUE (auction_id),
    CONSTRAINT chk_payment_holds_amounts
        CHECK (total_amount >= 0 AND deposit_applied >= 0 AND remaining_amount >= 0
            AND deposit_applied + remaining_amount = total_amount)
);

CREATE INDEX idx_payment_holds_winner_status ON payment_holds (winner_user_id, status);
CREATE INDEX idx_payment_holds_status_deadline ON payment_holds (status, deadline);
