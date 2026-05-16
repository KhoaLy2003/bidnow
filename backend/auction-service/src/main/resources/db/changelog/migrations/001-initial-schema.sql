-- liquibase formatted sql

--changeset bidnow:auction-initial-schema
--comment: Initialize auction service database schema

-- Auction status enum type
CREATE TYPE auction_status AS ENUM (
    'DRAFT',
    'ACTIVE',
    'COMPLETED',
    'FAILED',
    'CANCELLED'
);

-- Categories table
CREATE TABLE auction_categories
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL UNIQUE,
    slug          VARCHAR(100) NOT NULL UNIQUE,
    description   TEXT,
    parent_id     UUID         REFERENCES auction_categories (id),
    icon_url      VARCHAR(500),
    display_order INTEGER      NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auction_categories_parent_id ON auction_categories (parent_id);
CREATE INDEX idx_auction_categories_slug ON auction_categories (slug);
CREATE INDEX idx_auction_categories_is_active ON auction_categories (is_active);

-- Auction items table
CREATE TABLE auction_items
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id           UUID                     NOT NULL,
    title               VARCHAR(255)             NOT NULL,
    description         TEXT                     NOT NULL,
    category_id         UUID                     NOT NULL REFERENCES auction_categories (id),
    starting_price      DECIMAL(15, 2)           NOT NULL CHECK (starting_price >= 0),
    bid_increment       DECIMAL(15, 2)           NOT NULL CHECK (bid_increment > 0),
    buy_now_price       DECIMAL(15, 2)           CHECK (buy_now_price > starting_price),
    deposit_amount      DECIMAL(15, 2)           NOT NULL CHECK (deposit_amount >= 0),
    current_price       DECIMAL(15, 2)           NOT NULL DEFAULT 0,
    current_winner_id   UUID,
    total_bids          INTEGER                  NOT NULL DEFAULT 0,
    status              auction_status           NOT NULL DEFAULT 'DRAFT',
    start_time          TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time            TIMESTAMP WITH TIME ZONE NOT NULL,
    original_end_time   TIMESTAMP WITH TIME ZONE NOT NULL,
    extension_count     INTEGER                  NOT NULL DEFAULT 0,
    completed_at        TIMESTAMP WITH TIME ZONE,
    winner_id           UUID,
    winner_paid_at      TIMESTAMP WITH TIME ZONE,
    payment_deadline    TIMESTAMP WITH TIME ZONE,
    cancellation_reason TEXT,
    cancelled_by        UUID,
    cancelled_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_end_time_after_start_time CHECK (end_time > start_time)
);

CREATE INDEX idx_auction_items_seller_id ON auction_items (seller_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_status ON auction_items (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_category_id ON auction_items (category_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_end_time ON auction_items (end_time) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_auction_items_created_at ON auction_items (created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_auction_items_winner_id ON auction_items (winner_id) WHERE winner_id IS NOT NULL AND deleted_at IS NULL;

-- Auction images table
CREATE TABLE auction_images
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id    UUID                     NOT NULL,
    image_url     VARCHAR(1000)            NOT NULL,
    thumbnail_url VARCHAR(1000),
    display_order INTEGER                  NOT NULL DEFAULT 0,
    is_primary    BOOLEAN                  NOT NULL DEFAULT FALSE,
    uploaded_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auction_images_auction FOREIGN KEY (auction_id) REFERENCES auction_items (id) ON DELETE CASCADE
);

CREATE INDEX idx_auction_images_auction_id ON auction_images (auction_id);
CREATE UNIQUE INDEX idx_auction_images_primary ON auction_images (auction_id) WHERE is_primary = TRUE;

-- Auction status history table
CREATE TABLE auction_status_history
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id  UUID                     NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)              NOT NULL,
    reason      TEXT,
    triggered_by UUID,
    metadata    JSONB,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auction_status_history_auction FOREIGN KEY (auction_id) REFERENCES auction_items (id) ON DELETE CASCADE
);

CREATE INDEX idx_auction_status_history_auction_id ON auction_status_history (auction_id);
CREATE INDEX idx_auction_status_history_created_at ON auction_status_history (created_at DESC);

-- Auction extensions table
CREATE TABLE auction_extensions
(
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id                 UUID                     NOT NULL,
    previous_end_time          TIMESTAMP WITH TIME ZONE NOT NULL,
    new_end_time               TIMESTAMP WITH TIME ZONE NOT NULL,
    extension_duration_seconds INTEGER                  NOT NULL,
    triggered_by_bid_id        UUID,
    triggered_by_user_id       UUID                     NOT NULL,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auction_extensions_auction FOREIGN KEY (auction_id) REFERENCES auction_items (id) ON DELETE CASCADE
);

CREATE INDEX idx_auction_extensions_auction_id ON auction_extensions (auction_id);
CREATE INDEX idx_auction_extensions_created_at ON auction_extensions (created_at DESC);
