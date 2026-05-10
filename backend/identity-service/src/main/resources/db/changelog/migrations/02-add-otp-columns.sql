-- liquibase formatted sql

-- changeset khoa.ly:1778336795663
ALTER TABLE identity_users
    ADD COLUMN account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN verification_otp    VARCHAR(6),
    ADD COLUMN otp_expires_at      TIMESTAMP,
    ADD COLUMN otp_failed_attempts INTEGER         NOT NULL DEFAULT 0;

CREATE INDEX idx_identity_users_account_status ON identity_users (account_status);
