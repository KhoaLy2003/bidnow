-- liquibase formatted sql

--changeset bidnow:initial-schema
--comment: Initialize database schema

-- User profiles table
CREATE TABLE user_profiles
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID UNIQUE NOT NULL,
    display_name VARCHAR(100),
    avatar_url   VARCHAR(500),
    phone_number VARCHAR(20),
    address      TEXT,
    city         VARCHAR(100),
    country      VARCHAR(100),
    postal_code  VARCHAR(20),
    bio          TEXT,
    created_at   TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles (user_id);

-- User roles table
CREATE TABLE user_roles
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'USER',
    granted_at TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID,
    UNIQUE (user_id, role)
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);

-- User preferences table
CREATE TABLE user_preferences
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID UNIQUE NOT NULL,
    language            VARCHAR(10)      DEFAULT 'en',
    timezone            VARCHAR(50)      DEFAULT 'UTC',
    currency            VARCHAR(3)       DEFAULT 'USD',
    email_notifications BOOLEAN          DEFAULT TRUE,
    push_notifications  BOOLEAN          DEFAULT TRUE,
    sms_notifications   BOOLEAN          DEFAULT FALSE,
    created_at          TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences (user_id);
