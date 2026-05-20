-- liquibase formatted sql
-- changeset khoa:1779160313456
CREATE TABLE media_audit_logs
(
    id             UUID PRIMARY KEY,
    correlation_id UUID,
    entity_type    VARCHAR(100)             NOT NULL,
    entity_id      VARCHAR(255)             NOT NULL,
    action         VARCHAR(50)              NOT NULL,
    actor_id       VARCHAR(255),
    actor_type     VARCHAR(50),
    ip_address     VARCHAR(45),
    user_agent     TEXT,
    delta          JSONB,
    reason         TEXT,
    metadata       JSONB,
    timestamp      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP
);

-- Index for efficient searching
CREATE INDEX idx_media_audit_logs_actor_id ON media_audit_logs (actor_id);
CREATE INDEX idx_media_audit_logs_entity ON media_audit_logs (entity_type, entity_id);
CREATE INDEX idx_media_audit_logs_correlation_id ON media_audit_logs (correlation_id);
CREATE INDEX idx_media_audit_logs_created_at ON media_audit_logs (timestamp DESC);
