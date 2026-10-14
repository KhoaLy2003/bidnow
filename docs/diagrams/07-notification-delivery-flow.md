# Notification Delivery Flow

Domain services publish events to Kafka: identity-service (`user-registered-topic`), auction-service (`auction-created-topic`, `auction-ended-topic`, `auction-cancelled-topic`, `auction-extended-topic`, `auction-ending-soon-topic`), bidding-service (`bid-placed-topic`) and wallet-service (`payment-event-topic`, `deposit-refunded-topic`). media-service is the only notification owner: it turns those events into persisted in-app notifications, a live push and optional emails. Scheduled sources stay in their own domain and emit an event: the wallet `PaymentReminderScheduler` (`REMINDER_24H`) and the auction ending-soon JobRunr jobs.

```mermaid
sequenceDiagram
    autonumber
    participant Src as Domain services<br/>(identity, auction, bidding, wallet)
    participant K as Kafka
    participant MS as media-service<br/>(media-service-group)
    participant R as Redis<br/>(bid batching)
    participant DB as media DB
    participant US as identity / user-service<br/>(Feign: email, language, opt-out)
    participant SMTP as SMTP (Mailpit in dev)
    participant PI as media instance holding<br/>the user's socket
    actor U as Browser (Next.js)

    Note over Src,K: Wallet and auction events are published after commit. Schedulers are sources too:<br/>wallet PaymentReminderScheduler (REMINDER_24H), auction ending-soon JobRunr jobs.
    Src->>K: user-registered, auction-created/ended/cancelled/extended/ending-soon,<br/>bid-placed, payment-event, deposit-refunded
    K-->>MS: consume (media-service-group; the auction/bidder projection runs on media-projection-group)
    MS->>MS: handler builds NotificationIntent(s) with a dedup key

    alt Outbid / new-bid alert
        MS->>R: record bid (Lua: open window or count)
        alt First alert in a quiet 5-minute window
            R-->>MS: IMMEDIATE
        else Window already open
            R-->>MS: BATCHED (flushed later as one "N more" alert)
            Note over MS,R: BATCHED → stop; no row until the flush loop below
        end
    end

    opt Not batched (immediate or non-bid intent)
        MS->>DB: INSERT … ON CONFLICT (user_id, dedup_key) DO NOTHING
        alt Duplicate (Kafka redelivery)
            DB-->>MS: 0 rows → stop (no push, no email)
        else New row
            DB-->>MS: inserted
            Note over MS: after commit
            MS->>K: user-notification-push-topic {userId, NOTIFICATION, unreadCount}
            K-->>PI: per-instance consumer group media-push-UUID (latest offsets)
            PI->>U: STOMP /user/queue/notifications
            U->>U: bell badge, list, toast (outbid/won/payment/ending-soon)
            opt Email intent (transactional ignores opt-out)
                MS->>US: resolve email, language, preferences (chunks of 100)
                MS->>DB: load template {BASE}_{EN|VI} (VI falls back to EN)
                MS->>SMTP: send
                alt Sent
                    MS->>DB: media_email_logs SENT (notification_id)
                else Failed
                    MS->>DB: media_email_logs FAILED (notification_id, no retry)
                end
            end
        end
    end

    loop every 15 s on every instance
        MS->>R: claim due windows (Lua: ZSCORE≤now, ZREM, HGETALL, DEL)
        MS->>DB: insert batched alert (same dedup + push path)
    end

    Note over U,DB: Inbox: GET/PUT/DELETE /api/v1/notifications (soft delete), unread-count, mark-all-read
```

## Failure handling

- Redis down: outbid and new-bid alerts are sent immediately (noisy, never silent), with a warning logged.
- SMTP failure: logged as `FAILED` in `media_email_logs`, no retry. The in-app notification already exists.
- Feign lookup failure (identity or user-service): the email is skipped when no address is found; language falls back to EN and opt-in to true. The push still goes out.
- Kafka publish failure after commit: the push is lost but the row is kept. Clients resync the unread count on window focus and on STOMP reconnect.
- Payment and ending-soon events are at-most-once: a failed publish after commit is logged and not retried (see the risks in the [notification roadmap](../superpowers/plans/2026-09-30-notification-epic-roadmap.md)).
