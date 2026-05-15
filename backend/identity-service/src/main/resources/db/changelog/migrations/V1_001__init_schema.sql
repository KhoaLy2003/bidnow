-- liquibase formatted sql

-- changeset bidnow:V1_001
CREATE TABLE identity_users
(
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                     VARCHAR(255) UNIQUE NOT NULL,
    password_hash             VARCHAR(255)        NOT NULL,
    is_email_verified         BOOLEAN          DEFAULT FALSE,
    email_verification_token  VARCHAR(255),
    password_reset_token      VARCHAR(255),
    password_reset_expires_at TIMESTAMP,
    last_login_at             TIMESTAMP,
    failed_login_attempts     INTEGER          DEFAULT 0,
    locked_until              TIMESTAMP,
    is_active                 BOOLEAN          DEFAULT TRUE,
    created_at                TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_identity_users_email ON identity_users (email);
CREATE INDEX idx_identity_users_email_verified ON identity_users (is_email_verified);

-- changeset bidnow:V1_002
CREATE TABLE identity_refresh_tokens
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID                NOT NULL REFERENCES identity_users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,
    device_info VARCHAR(500),
    ip_address  VARCHAR(45),
    expires_at  TIMESTAMP           NOT NULL,
    is_revoked  BOOLEAN          DEFAULT FALSE,
    created_at  TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_identity_refresh_tokens_user_id ON identity_refresh_tokens (user_id);
CREATE INDEX idx_identity_refresh_tokens_expires_at ON identity_refresh_tokens (expires_at);
