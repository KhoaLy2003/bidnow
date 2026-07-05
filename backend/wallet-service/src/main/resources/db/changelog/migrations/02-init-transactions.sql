-- liquibase formatted sql

-- changeset bidnow:wallet_002
CREATE TABLE transactions
(
    id                       UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    wallet_id                UUID           NOT NULL REFERENCES wallets (id),
    type                     VARCHAR(20)    NOT NULL,
    amount                   DECIMAL(19, 4) NOT NULL,
    available_balance_before DECIMAL(19, 4) NOT NULL,
    available_balance_after  DECIMAL(19, 4) NOT NULL,
    reference_id             UUID,
    description              TEXT,
    status                   VARCHAR(20)    NOT NULL,
    payment_gateway_tx_id    VARCHAR(100),
    metadata                 JSONB,
    created_at               TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_wallet_id ON transactions (wallet_id);
CREATE INDEX idx_transactions_type ON transactions (type);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
