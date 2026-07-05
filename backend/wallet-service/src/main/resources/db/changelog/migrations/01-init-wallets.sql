-- liquibase formatted sql

-- changeset bidnow:wallet_001
CREATE TABLE wallets
(
    id                UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id           UUID UNIQUE    NOT NULL,
    total_balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    locked_balance    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'USD',
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_balances_non_negative
        CHECK (total_balance >= 0 AND available_balance >= 0 AND locked_balance >= 0),
    CONSTRAINT chk_balance_invariant
        CHECK (total_balance = available_balance + locked_balance)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);
