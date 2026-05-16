-- liquibase formatted sql

-- changeset khoa.ly:1778336795664
ALTER TABLE identity_users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN status_reason TEXT;

CREATE INDEX idx_identity_users_role ON identity_users (role);

-- Seed admin account
INSERT INTO identity_users (email, password_hash, role, account_status, is_email_verified, is_active)
VALUES ('admin@bidnow.com', '$2a$12$I5YkQyHmZGzUHRzctrZrwewSheywyHVKWYzc7bOuddk9HuUtRY1ju', 'ADMIN', 'ACTIVE', TRUE,
        TRUE);
