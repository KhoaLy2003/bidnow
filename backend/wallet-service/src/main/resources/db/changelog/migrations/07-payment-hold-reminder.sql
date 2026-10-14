-- liquibase formatted sql

-- changeset bidnow:wallet_007
-- comment: Payment reminder #2 is sent once per hold (NOTIF-106)
ALTER TABLE payment_holds ADD COLUMN reminder_sent_at TIMESTAMP;
