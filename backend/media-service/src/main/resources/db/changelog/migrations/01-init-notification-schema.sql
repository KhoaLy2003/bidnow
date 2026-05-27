-- liquibase formatted sql

-- changeset khoa.ly:1
CREATE TABLE notif_notifications
(
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL,
    type          VARCHAR(50)  NOT NULL,
    channel       VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    title         VARCHAR(255)         NOT NULL,
    message       TEXT                 NOT NULL,
    action_url    VARCHAR(500),
    auction_id    UUID,
    bid_id        UUID,
    metadata      JSONB,
    sent_at       TIMESTAMP,
    read_at       TIMESTAMP,
    failed_reason TEXT,
    retry_count   INTEGER                       DEFAULT 0,
    created_at    TIMESTAMP            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE INDEX idx_notif_notifications_user_id ON notif_notifications (user_id);
CREATE INDEX idx_notif_notifications_status ON notif_notifications (status);
CREATE INDEX idx_notif_notifications_type ON notif_notifications (type);
CREATE INDEX idx_notif_notifications_created_at ON notif_notifications (created_at DESC);

CREATE TABLE notif_user_preferences
(
    id                   UUID PRIMARY KEY,
    user_id              UUID UNIQUE NOT NULL,
    email_enabled        BOOLEAN              DEFAULT TRUE,
    push_enabled         BOOLEAN              DEFAULT TRUE,
    sms_enabled          BOOLEAN              DEFAULT FALSE,
    type_preferences     JSONB                DEFAULT '{
      "BID_OUTBID": {
        "email": true,
        "push": true,
        "sms": false
      },
      "AUCTION_WON": {
        "email": true,
        "push": true,
        "sms": true
      },
      "PAYMENT_REMINDER": {
        "email": true,
        "push": true,
        "sms": false
      }
    }',
    quiet_hours_start    TIME,
    quiet_hours_end      TIME,
    quiet_hours_timezone VARCHAR(50),
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP
);

CREATE INDEX idx_notif_user_preferences_user_id ON notif_user_preferences (user_id);

CREATE TABLE notif_email_logs
(
    id              UUID PRIMARY KEY,
    notification_id UUID,
    recipient_email VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    template_name   VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    failure_reason  TEXT,
    retry_count     INT          NOT NULL DEFAULT 0,
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE TABLE notif_notification_templates
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    type       VARCHAR(255) NOT NULL,
    language   VARCHAR(255) NOT NULL,
    subject    VARCHAR(255),
    body_html  TEXT,
    body_text  TEXT         NOT NULL,
    variables  JSONB,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
