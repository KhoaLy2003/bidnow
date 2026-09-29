-- liquibase formatted sql

-- changeset bidnow:wallet_006
CREATE INDEX idx_transactions_refund_reference ON transactions (reference_id) WHERE type = 'REFUND';
