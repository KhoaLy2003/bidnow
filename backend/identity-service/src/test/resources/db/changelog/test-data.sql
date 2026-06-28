-- liquibase formatted sql

-- changeset bidnow:test-enable-pgcrypto
CREATE
EXTENSION IF NOT EXISTS pgcrypto;

-- changeset bidnow:test-seed-identity-users
-- comment: Fixed BDD test users. @bddtest.local domain is excluded from @Before cleanup (which targets @example.com only).
INSERT INTO identity_users (email, password_hash, display_name,
                            is_email_verified, is_active, account_status, role,
                            otp_failed_attempts, failed_login_attempts,
                            created_at, updated_at)
VALUES ('seed-verified@bddtest.local',
        crypt('P@ssw0rd1', gen_salt('bf', 4)),
        'Seed Verified User',
        true, true, 'ACTIVE', 'USER', 0, 0, NOW(), NOW()),
       ('seed-unverified@bddtest.local',
        crypt('P@ssw0rd1', gen_salt('bf', 4)),
        'Seed Unverified User',
        false, false, 'PENDING_VERIFICATION', 'USER', 0, 0, NOW(), NOW()) ON CONFLICT (email) DO NOTHING;

-- changeset bidnow:test-seed-duplicate-email-user
-- comment: Pre-existing user for register.feature "duplicate email" scenario. Uses @bddtest.local to survive @Before cleanup.
INSERT INTO identity_users (email, password_hash, display_name,
                            is_email_verified, is_active, account_status, role,
                            otp_failed_attempts, failed_login_attempts,
                            created_at, updated_at)
VALUES ('alice@bddtest.local',
        crypt('P@ssw0rd1', gen_salt('bf', 4)),
        'Alice Seed', false, false, 'PENDING_VERIFICATION', 'USER', 0, 0, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
