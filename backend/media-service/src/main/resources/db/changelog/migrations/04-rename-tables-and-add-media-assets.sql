-- liquibase formatted sql

-- changeset khoa.ly:rename-notif-tables-to-media
-- comment: Rename all notif_ prefixed tables to media_ prefix to align with media-service rename

ALTER TABLE notif_notifications RENAME TO media_notifications;
ALTER TABLE notif_user_preferences RENAME TO media_user_preferences;
ALTER TABLE notif_email_logs RENAME TO media_email_logs;
ALTER TABLE notif_notification_templates RENAME TO media_notification_templates;

-- Rename indexes to match new table names
ALTER
INDEX idx_notif_notifications_user_id RENAME TO idx_media_notifications_user_id;
ALTER
INDEX idx_notif_notifications_status RENAME TO idx_media_notifications_status;
ALTER
INDEX idx_notif_notifications_type RENAME TO idx_media_notifications_type;
ALTER
INDEX idx_notif_notifications_created_at RENAME TO idx_media_notifications_created_at;
ALTER
INDEX idx_notif_user_preferences_user_id RENAME TO idx_media_user_preferences_user_id;

-- changeset khoa.ly:create-media-assets-table
-- comment: Create media_assets table to track uploaded files and their metadata

CREATE TABLE media_assets
(
    id            UUID PRIMARY KEY,
    owner_id      UUID         NOT NULL,
    entity_id     UUID,
    entity_type   VARCHAR(50),
    original_name VARCHAR(255) NOT NULL,
    s3_key        VARCHAR(500) NOT NULL UNIQUE,
    content_type  VARCHAR(100) NOT NULL,
    file_size     BIGINT       NOT NULL,
    width         INT,
    height        INT,
    is_processed  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE INDEX idx_media_assets_owner_id ON media_assets (owner_id);
CREATE INDEX idx_media_assets_entity_id ON media_assets (entity_id);
CREATE INDEX idx_media_assets_entity_type ON media_assets (entity_type);
CREATE INDEX idx_media_assets_created_at ON media_assets (created_at DESC);
