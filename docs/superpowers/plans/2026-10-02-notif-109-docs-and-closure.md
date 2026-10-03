# NOTIF-109: Notification Epic Documentation & Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring every project document in line with what the notification epic (#14, NOTIF-101 to NOTIF-108) actually built, and refresh the API artifacts. (No SVG regeneration and no GitHub changes — user decision.)

**Architecture:** This is documentation-only work, with no production code changes.
- Each task rewrites one group of documents from the source of truth: the code, the Liquibase migrations and the committed per-story plans. It then proves consistency with grep checks against that source.
- Rendered artifacts depend on tools or a running stack:
  - the diagram SVGs (Mermaid CLI)
  - `api-docs.json` (the running media-service)

  Their tasks try the automated path and hand a precise manual step to the user if the environment can't do it.

**Tech Stack:** Markdown, Mermaid (`sequenceDiagram`), springdoc-openapi 2.3.0 (`/api/v1/media/v3/api-docs`), Postman collection v2.1 JSON.

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`, specifically **Story 9**, the **Decisions** section (the binding behaviour to document), and the Story 1–8 "Refinements" paragraphs (what was actually built where it differs from the original plan).

## Global Constraints

- **Source of truth order:** code and migrations, then each story's "Refinements" paragraph in the roadmap, then the roadmap Decisions, then the original epic docs (`docs/epics/notification/*`). When documents disagree, the higher source wins. Never document behaviour that is not in the code.
- **No production code or migration changes.** Only `docs/**`, `backend/*/api-docs.json`, `backend/media-service/media-service-api.postman_collection.json`, the roadmap, and the rendered `docs/diagrams/*.svg`.
- **Out-of-scope items stay out:** NOTIF-103 (email retry, admin retry, stats), seller-configurable ending-soon thresholds, SMS, push, sound and desktop notifications. Mark them as out of scope wherever older docs promise them; do not delete the history.
- **Table names** are `media_*` (renamed from `notif_*` in migration 04). Every schema doc uses the current names and lists the old name once as "formerly".
- **Email failures** are logged as `FAILED` in `media_email_logs` with **no retry** (Decision 11). No doc may claim a retry or cron.
- **Window lengths and thresholds** must match config: the outbid/new-bid batch window is 300 s (`notification.outbid.batch-window-seconds`), ending-soon thresholds are 60 and 15 minutes (`auction.ending-soon.thresholds-minutes`), and payment reminder #2 is sent when 24 h are left (`wallet.payment.reminder-before-deadline-hours`).
- **Do not touch GitHub and do not regenerate SVGs** (user decision). Do not download tools.
- **Agents do not commit.** Skip every commit step. The user commits. Conventional commit (the user runs it): `docs(notification): document the notification epic and close it out (NOTIF-109)`.

## Rulings (decisions this plan makes beyond the roadmap)

1. **Schema docs are regenerated from the migrations,** not edited line by line. `docs/database/notification-service.schema.md` still shows the pre-rename `notif_*` DDL from migration 01. Rewriting it from migrations 01–08 is less error-prone than patching.
2. **`docs/database.md` gets a `payment_holds` entry,** because the roadmap's "wallet `reminder_sent_at`" has no table to attach to. The doc's wallet section predates the payment hold and lists only `wallet_wallets`, `wallet_transactions` and `wallet_deposits`. The new entry mirrors migrations 04 and 07 of wallet-service. Other stale wallet tables (`deposit_locks`, `auction_cancellations`) are noted as "see wallet-service migrations" rather than fully documented, since that belongs to the wallet epic.
3. **No SVG regeneration and no GitHub changes (user decision, 2026-10-02).** Diagram 07 is updated as Mermaid source only; the committed `.svg` files are left as they are. GitHub issues are not touched.
4. **The Postman collection gains a "Notifications" folder** for the 9 inbox endpoints (the NOTIF-102 plan moved it here). `api-docs.json` is regenerated from the running service. If the stack cannot start in the agent's environment, the command is handed to the user.
5. **The MVP doc is annotated, not rewritten.** A "Superseded" banner and inline `> **Superseded:**` notes go where it differs from what was built (window, seller settings, endpoints, retry, the won email). The original text stays for history.

## File Structure

| File | Change |
|---|---|
| `docs/database/notification-service.schema.md` | Rewrite: current `media_*` tables, new columns and indexes, projection tables, which tables are unused |
| `docs/database.md` | §4 wallet: add `payment_holds` (incl. `reminder_sent_at`); §5 media: replace the stale `notification_notifications` stub |
| `docs/diagrams/07-notification-delivery-flow.md` (+ `.svg`) | Redraw the real pipeline; regenerate the SVG (and `03-…svg`) |
| `docs/architecture.md` | Media-service responsibilities, notification topics, the projection, schedulers as event sources, Redis use |
| `docs/functional.md` §6, `docs/business-clarifications.md` §5 | Notification rules (Decisions 1–3, 5, 7, 11) |
| `docs/epics/notification/notification-service-mvp.md` | Superseded banner and notes |
| `backend/media-service/media-service-api.postman_collection.json`, `backend/media-service/api-docs.json` | Inbox API |
| `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md` | Delivery links, Story 9 ticks, epic status |

---

### Task 1: Schema documentation

**Files:**
- Rewrite: `docs/database/notification-service.schema.md`
- Modify: `docs/database.md` (§4 Wallet Service, §5 Media Service)

**Interfaces:**
- Consumes (read-only sources):
  - `backend/media-service/src/main/resources/db/changelog/migrations/01-…08-*.sql` and `db.changelog-master.xml`
  - `backend/media-service/src/main/java/com/bidnow/media/domain/entity/*.java` and `domain/enums/*.java`
  - `backend/wallet-service/src/main/resources/db/changelog/migrations/04-init-payment-holds.sql` and `07-payment-hold-reminder.sql`
- Produces: schema docs that later tasks link to as `docs/database/notification-service.schema.md`.

- [ ] **Step 1: Collect the facts**

Read every media-service migration, in master-changelog order, and the entities. Write down the final shape of each table after all migrations: columns, types, nullability, defaults, constraints and indexes. Migration 04 renames `notif_*` to `media_*`. Migration 06 adds `dedup_key` (NOT NULL), `deleted_at`, the unique constraint `uq_media_notifications_user_dedup`, two indexes, and the tables `media_auctions` and `media_auction_participants`. Also record:
- whether `media_email_logs` has a `notification_id` column (check 01 and 06; do not assume);
- the `NotificationType` enum values (21, from `NotificationType.java`). The DB column is `VARCHAR`, not a Postgres enum; check migration 01 and document what is actually there.

- [ ] **Step 2: Rewrite `docs/database/notification-service.schema.md`**

Structure it as follows.
- Title: `## 6. Media Service Tables (notifications)`.
- Under it, one line: "Tables are prefixed `media_` (renamed from `notif_` in migration 04). Source of truth: `backend/media-service/src/main/resources/db/changelog/migrations/`."
- One `###` section per table, each with a column table (`| Column | Type | Constraints | Description |`) and an **Indexes** list:
  - `media_notifications`: say what `dedup_key` is for (one notification per user and key; Kafka redelivery is a no-op) and that `deleted_at` is a soft delete. Name the unique constraint. Document `read_at` as the read flag (no `status = READ` semantics). Do not document columns that don't exist.
  - `media_email_logs`
  - `media_notification_templates`: note the name convention `{BASE}_{EN|VI}`, and list the seeded and added template names from migrations 02, 03 and 07.
  - `media_user_preferences`: mark it **unused**. Preferences live in user-service (Decision 5). The table is kept because there are no destructive migrations.
  - `media_auctions` and `media_auction_participants`, under a sub-heading "Auction projection (media-owned, fed by `auction-created-topic`, `bid-placed-topic` and `auction-extended-topic` on `media-projection-group`)". Explain the order-tolerant upserts: COALESCE title and seller, `GREATEST(end_time)`, and the newer bid wins per participant.
  - Also list `media_assets` and `media_audit_logs` briefly, one line each with a pointer to their migration. They live in the same DB but are not part of the notification epic.
- A closing "Notification types" list: the 21 enum names, grouped as account, bidding, auction, payment and system.

- [ ] **Step 3: Update `docs/database.md`**

- §5 Media Service: replace the stale `notification_notifications` table with a short summary. Give one line per `media_*` table and a link: "Full schema: [notification-service.schema.md](database/notification-service.schema.md)".
- §4 Wallet Service: add `### payment_holds`, with the column table from wallet migration 04 plus `reminder_sent_at TIMESTAMP NULL — set when payment reminder #2 is sent (NOTIF-106); NULL = not yet reminded` from migration 07. Add one sentence: "Other wallet tables (`deposit_locks`, `auction_cancellations`) are defined in `backend/wallet-service/src/main/resources/db/changelog/migrations/`."

- [ ] **Step 4: Verify consistency**

Run (from the repo root):

```bash
grep -n "notif_" docs/database/notification-service.schema.md docs/database.md
```
Expected: only the "formerly `notif_`" mention(s).

```bash
grep -c "dedup_key\|deleted_at\|media_auctions\|media_auction_participants\|reminder_sent_at" docs/database/notification-service.schema.md docs/database.md
```
Expected: non-zero for both files.

Cross-check every column name you documented against the migrations, for example `grep -n "<column>" backend/media-service/src/main/resources/db/changelog/migrations/*.sql`, and list the check in the report.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 2: Notification delivery diagram (07) and SVG regeneration

**Files:**
- Rewrite: `docs/diagrams/07-notification-delivery-flow.md`

**Interfaces:**
- Consumes: `NotificationKafkaConsumer`, `NotificationDispatcher`, `BidBatcher` / `BatchFlushScheduler`, `UserNotificationPushPublisher` / `UserNotificationPushConsumer`, `EmailServiceImpl`, the wallet `PaymentReminderScheduler` and the auction `AuctionEndingSoonService` (read to confirm names and order).

- [ ] **Step 1: Replace the Mermaid diagram**

Rewrite `docs/diagrams/07-notification-delivery-flow.md`.
- Title: `# Notification Delivery Flow`.
- One paragraph under the title: which sources emit which topics, and that media-service is the only notification owner.
- The diagram:

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

    Note over Src,K: Events are published after commit. Schedulers are sources too:<br/>wallet PaymentReminderScheduler (REMINDER_24H), auction ending-soon JobRunr jobs.
    Src->>K: user-registered, auction-created/ended/cancelled/extended/ending-soon,<br/>bid-placed, payment-event, deposit-refunded
    K-->>MS: consume (shared group; projection runs on media-projection-group)
    MS->>MS: handler builds NotificationIntent(s) with a dedup key

    alt Outbid / new-bid alert
        MS->>R: record bid (Lua: open window or count)
        alt First alert in a quiet 5-minute window
            R-->>MS: IMMEDIATE
        else Window already open
            R-->>MS: BATCHED (flushed later as one "N more" alert)
        end
    end

    MS->>DB: INSERT … ON CONFLICT (user_id, dedup_key) DO NOTHING
    alt Duplicate (Kafka redelivery)
        DB-->>MS: 0 rows → stop (no push, no email)
    else New row
        DB-->>MS: inserted
        Note over MS: after commit
        MS->>K: user-notification-push-topic {userId, NOTIFICATION, unreadCount}
        K-->>PI: per-instance consumer group (latest offsets)
        PI->>U: STOMP /user/queue/notifications
        U->>U: bell badge, list, toast (outbid/won/payment/ending-soon)
        opt Email intent (transactional ignores opt-out)
            MS->>US: resolve email, language, preferences (chunks of 100)
            MS->>DB: load template {BASE}_{EN|VI} (VI falls back to EN)
            MS->>SMTP: send
            alt Sent
                MS->>DB: media_email_logs SENT (notification_id)
            else Failed
                MS->>DB: media_email_logs FAILED (no retry)
            end
        end
    end

    loop every 15 s on every instance
        MS->>R: claim due windows (Lua: ZSCORE≤now, ZREM, HGETALL, DEL)
        MS->>DB: insert batched alert (same dedup + push path)
    end

    Note over U,DB: Inbox: GET/PUT/DELETE /api/v1/notifications (soft delete), unread-count, mark-all-read
```

Before finalising, confirm each claim against the code:
- the consumer group names;
- the Feign chunk size;
- that email logs carry `notification_id`. If they don't, say "media_email_logs SENT/FAILED" without it.

Adjust the diagram text to match. Below the diagram, add a short "Failure handling" list:
- Redis down → alerts are sent immediately.
- SMTP failure → logged FAILED, no retry.
- Kafka publish failure after commit → the push is lost, and clients resync the count on focus/reconnect.
- Payment and ending-soon events are at-most-once (see the roadmap risks).

- [ ] **Step 2: Verify**

```bash
grep -n -i "rabbitmq\|socket.io\|sendgrid\|retry logic" docs/diagrams/07-notification-delivery-flow.md
```
Expected: no matches.

- [ ] **Step 3: Commit**: skipped (the user commits).

---

### Task 3: Architecture and requirement docs

**Files:**
- Modify: `docs/architecture.md`, `docs/functional.md` (§6 Notifications), `docs/business-clarifications.md` (§5 Notifications)

**Interfaces:**
- Consumes: the roadmap Decisions 1–3, 5, 7 and 11, and the Story 1–8 Refinements; the current `docs/architecture.md`, which already documents the STOMP destinations, the inbox REST and the wallet/bidding topics.

- [ ] **Step 1: `docs/architecture.md`**

- **Core Services item 7 (Media Service):** replace the one-liner with a short paragraph. Cover:
  - It is the only notification owner.
  - Kafka handlers turn domain events into idempotent `media_notifications` rows (dedup key).
  - It pushes live through `user-notification-push-topic` to STOMP `/user/queue/notifications`.
  - It sends EN/VI template emails, with transactional types ignoring opt-out, logged in `media_email_logs`, and no retry.
  - It keeps its own auction/bidder projection (`media_auctions`, `media_auction_participants`) so recipients never need a cross-service call.
  - It batches outbid/new-bid alerts in Redis (5-minute window).
  - Keep the existing media-assets and audit-log mention.
- **New subsection** `### Notification Events (Kafka)`, placed after "Wallet Events (Kafka)". Add a table (topic | producer | consumer group(s) | purpose) covering:
  - `user-registered-topic`, `auction-created-topic`, `auction-ended-topic`, `auction-cancelled-topic`, `auction-extended-topic`, `bid-placed-topic`, `payment-event-topic` and `deposit-refunded-topic`, each consumed by `media-service-group` → notifications;
  - `auction-ending-soon-topic` (auction-service JobRunr, 60 and 15 min before the current end);
  - `user-notification-push-topic` (media → per-instance `media-push-<uuid>`, latest offsets);
  - the projection consumer on `media-projection-group`.

  Confirm the group names in `NotificationKafkaConsumer`, `AuctionProjectionConsumer` and `UserNotificationPushConsumer` before writing. Close the subsection with one sentence on scheduled sources: wallet `PaymentReminderScheduler` (every 5 min → `REMINDER_24H`), wallet `ForfeitScheduler` (→ `FAILED`), and the auction ending-soon jobs.
- **Database / caching (Tech Stack or Performance section):** add that media-service uses Redis for bid batching windows. That fact is already in the line-17 caching bullet, so only check it is there.
- **References:** add links to `database/notification-service.schema.md`, `diagrams/07-notification-delivery-flow.md` and the notification roadmap.

- [ ] **Step 2: `docs/functional.md` §6 Notifications**

Rewrite the section as a short list:
- **In-app (real-time):**
  - outbid (batched: the first immediately, then one "outbid N more times" per 5 minutes)
  - new bid on your auction (seller, batched the same way; the first bid is immediate)
  - auction ending soon (60 and 15 min before the current end, to bidders; platform default, not seller-configurable)
  - anti-sniping extension
  - auction won, lost, unsold and cancelled
  - payment required, reminder, completed and failed
  - deposit refunded
- **Email:**
  - welcome
  - auction live
  - won / pay within 48 h (sent once, with payment-required)
  - payment reminder #2 (24 h left)
  - payment successful
  - sale payment received (seller)
  - payment failed (forfeit)
  - auction lost
  - auction cancelled
  - deposit refunded after a loss

  Payment emails are transactional. Engagement emails respect the user's email opt-out.
- **Inbox:** list with filters, search and paging; read/unread; mark all; delete (soft) and bulk.
- **Out of scope:** SMS, push, sound and desktop notifications, email retry, seller-configurable thresholds, per-user notification settings UI.

- [ ] **Step 3: `docs/business-clarifications.md` §5 Notifications**

Keep the existing two channel bullets and add these decision bullets:
1. The outbid/new-bid batch window is 5 minutes. The first alert is immediate, and later ones are summed.
2. The first bid on an auction is sent immediately; later seller alerts are batched.
3. The winner gets one email, at payment-required. The auction-ended event is in-app only.
4. Language and email opt-out come from user-service preferences, and payment emails ignore the opt-out.
5. The ending-soon thresholds are a platform default (60 and 15 min), with no per-seller setting.
6. Emails are sent once. A failure is logged with no retry, and the in-app notification is still created.

Remove the "outbid" email from the existing channel bullet. Outbid is in-app only, so "critical updates (outbid, winning, payment reminders)" becomes "critical updates (winning, payment reminders, payment results)".

- [ ] **Step 4: Verify**

```bash
grep -n -i "retry" docs/functional.md docs/business-clarifications.md docs/architecture.md
```
Expected: matches only in "no retry" or out-of-scope statements.

```bash
grep -n "auction-ending-soon-topic\|user-notification-push-topic\|media-projection-group" docs/architecture.md
```
Expected: each one present.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 4: Superseded notes, Postman collection and API docs

**Files:**
- Modify: `docs/epics/notification/notification-service-mvp.md`, `backend/media-service/media-service-api.postman_collection.json`
- Regenerate (conditional): `backend/media-service/api-docs.json`

**Interfaces:**
- Consumes: `NotificationController` (the 9 endpoints, their paths, query params and bodies), `dto/request/NotificationQuery.java`, and the existing Postman collection's variables (`base_url`, auth header pattern).

- [ ] **Step 1: Annotate the MVP doc**

At the very top of `docs/epics/notification/notification-service-mvp.md`, insert:

```markdown
> **Superseded where it differs from what was built.** The implemented design is in
> [the notification epic roadmap](../../superpowers/plans/2026-09-30-notification-epic-roadmap.md) (Decisions and
> per-story Refinements). Notes marked **Superseded** below point out each difference; the original text is kept
> for history.
```

Then add one `> **Superseded:** …` line directly under each section that differs:
- "Auction Ending Soon - Seller Configurable": a platform default (60 and 15 min) with no seller setting (Decision 7).
- Payment reminder timeline: reminder #1 is the won/payment-required email; reminder #2 is sent when 24 h are left; FAILED arrives at 48 h. This matches the doc. Only add a note where wording differs, for example a separate "Auction won" email (Decision 3).
- Email retry or "retry logic", if present: no retry (Decision 11).
- Any endpoint list that differs from `/api/v1/notifications`: point to `docs/architecture.md` "Inbox REST".
- Storage "Delete notifications": soft delete.
- The `notifications` table sketch: point to `docs/database/notification-service.schema.md`.

Read the whole document and only annotate real differences.

- [ ] **Step 2: Add a "Notifications" folder to the Postman collection**

In `backend/media-service/media-service-api.postman_collection.json`, append an item named `Notifications` with nine requests, using the collection's existing `{{base_url}}` and the same auth header or collection auth style as the existing items:
- List: `GET {{base_url}}/api/v1/notifications?page=0&size=20&read=false&types=BID_OUTBID&search=watch`
- Unread count: `GET {{base_url}}/api/v1/notifications/unread-count`
- Get (marks read): `GET {{base_url}}/api/v1/notifications/{{notification_id}}`
- Mark read: `PUT {{base_url}}/api/v1/notifications/{{notification_id}}/read`
- Mark unread: `PUT {{base_url}}/api/v1/notifications/{{notification_id}}/unread`
- Mark all read: `PUT {{base_url}}/api/v1/notifications/mark-all-read`
- Delete: `DELETE {{base_url}}/api/v1/notifications/{{notification_id}}`
- Delete all: `DELETE {{base_url}}/api/v1/notifications/delete-all`
- Delete read: `DELETE {{base_url}}/api/v1/notifications/delete-read`

Add a collection variable `notification_id` (empty). Update the collection description to mention notifications. Validate the JSON with `node -e "JSON.parse(require('fs').readFileSync('backend/media-service/media-service-api.postman_collection.json','utf8')); console.log('ok')"`, which should print `ok`. Then check that the paths match `NotificationController`'s mappings with `grep -n "Mapping" backend/media-service/src/main/java/com/bidnow/media/controller/NotificationController.java`.

- [ ] **Step 3: Regenerate `api-docs.json` (environment-dependent)**

This needs media-service running with its DB, Kafka and Redis. Try this:
1. From `backend/`, run `docker compose up -d`. This starts the infrastructure; check that `backend/docker-compose.yml` covers Postgres, Kafka and Redis.
2. Start media-service with its required environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, the mail variables, the AWS variables). Look for a `backend/.env` or the root `docker-compose.yml`; never invent credentials. If they are unavailable, stop and hand the step to the user.
3. Run `curl -s http://localhost:8086/api/v1/media/v3/api-docs -o media-service/api-docs.json`.
4. Check with `grep -c '"/api/v1/notifications' backend/media-service/api-docs.json`, which should give 6 or more distinct path entries.

If the service can't be started in the agent's environment, do not fake the file. Record the exact commands for the user in the report, and leave `api-docs.json` unchanged.

- [ ] **Step 4: Commit**: skipped (the user commits).

---

### Task 5: Roadmap close-out

**Files:**
- Modify: `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`

**Interfaces:**
- Consumes: the per-story plans in `docs/superpowers/plans/`:
  - `2026-09-30-notif-101-pipeline-foundation.md`
  - `2026-09-30-notif-102-user-notification-apis.md`
  - `2026-10-01-notif-104-event-notifications.md`
  - `2026-10-01-notif-105-bid-batching.md`
  - `2026-10-02-notif-106-payment-reminder.md`
  - `2026-10-02-notif-107-ending-soon.md`
  - `2026-10-02-notif-108-notification-center.md`
  - this plan

  It also consumes the `git log --oneline` commits on `feature/notification`.

- [ ] **Step 1: Delivery links in the roadmap**

Add a `## Delivery` section after "Story map & sequencing". Make it a table with the columns story | plan | commit | status. Give one row per story (1, 2, 4–9, plus 3 marked "removed — out of scope"):
- Link each plan file.
- Take each commit's short SHA and subject from `git log --oneline --grep "NOTIF-10"` (for this story, write "this commit" until the user commits).
- Use "done (manual smoke pending)" as the status where the story's manual smoke step is unticked.

Tick the Story 9 checkboxes that Tasks 1–4 completed. Leave the `api-docs.json` box unticked if it was handed to the user. Mark the diagram box done with "(Mermaid source; SVG not regenerated)". Mark the "Close #17–#19 and the epic" item "(not done — user decision)" and leave it unticked.

- [ ] **Step 2: Commit**: skipped. The user commits `docs(notification): document the notification epic and close it out (NOTIF-109)`.
