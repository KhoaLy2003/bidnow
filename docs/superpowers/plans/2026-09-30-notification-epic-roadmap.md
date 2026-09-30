# Epic #14 — Notification Service MVP: Implementation Plan (Roadmap)

> **For agentic workers:** This is the **epic-level** plan. It fixes the story order, the contracts between stories, and the task breakdown of each story. Before a story starts, expand it into its own detailed TDD plan at `docs/superpowers/plans/YYYY-MM-DD-<story>.md`, following the BID-10x plans (full code, one checkbox per step). Then execute that plan with superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Every user-relevant auction, bid and payment event becomes a persisted in-app notification pushed live to the user, and/or a templated EN/VI email. Users manage their notifications from a bell and a full page.

**Architecture:** media-service stays the only notification owner. Kafka listeners in the shared `media-service-group` turn domain events into notification *intents*. `NotificationDispatcher` then does three things: (a) persists an idempotent `media_notifications` row keyed by `(user_id, dedup_key)`, (b) renders and sends email through the existing `EmailService` (single attempt, logged in `media_email_logs`), and (c) after commit, publishes a `user-notification-push-topic` message. A per-instance consumer (same pattern as `AuctionRealtimeConsumer`) pushes that message to `/user/queue/notifications`, so a push reaches the user whichever media instance holds their socket. media-service builds its own projection of auctions and bidders from `auction-created-topic` and `bid-placed-topic`, so "active bidders", "losers" and auction titles never need a synchronous call to another service. Redis holds outbid batching windows. Scheduled work that belongs to another domain stays in that domain and emits an event: payment reminder #2 in wallet-service, ending-soon jobs in auction-service.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.0 (OpenFeign, Gateway, Eureka), PostgreSQL + Liquibase, Kafka, Redis (`spring-boot-starter-data-redis`, new to media-service), Spring `@Scheduled` (media, wallet), JobRunr (auction-service), STOMP/SockJS, JavaMail + commons-text `StringSubstitutor`. Frontend: Next.js 14 + TypeScript, Zustand, `@stomp/stompjs`, `sockjs-client`. Tests: JUnit 5, Mockito, AssertJ, standalone MockMvc, Cucumber (auction-service BDD).

**Spec / sources:** `docs/epics/notification/notification-service-mvp.md` (scope), `docs/epics/notification/issue-9..13.md` (= GitHub #15–#19), `docs/diagrams/07-notification-delivery-flow.md`. No separate design spec exists. The **Decisions** section below records how this roadmap resolves conflicts between those sources, and serves as the spec for the stories.

## Current state (2026-09-30, branch `feature/bidding`)

| Area | State |
|---|---|
| #15 Infrastructure (closed) | Mostly done. Tables `media_notifications`, `media_email_logs`, `media_notification_templates`, `media_user_preferences` (migrations 01, 04). Kafka consumer for 6 topics. STOMP `/ws-notifications` + `GatewayUserHandshakeHandler` + `StompInboundGuard` (allows `/user/queue/notifications`). **Missing:** soft delete, idempotency key, no row is ever written to `media_notifications`. |
| #16 Email + templates (closed) | **Partial.** Send + `media_email_logs` logging works. Admin template CRUD, test and group send exist. Templates exist for WELCOME, AUCTION_CREATED, AUCTION_WON, AUCTION_LOST, PAYMENT_SUCCESSFUL, DEPOSIT_REFUNDED, OTP (EN/VI). **Missing:** retry (`retryEmail` throws `UnsupportedOperationException`, no rendered body is persisted), delivery stats, payment reminder #1/#2 templates, language resolution (`resolveLanguage()` hard-codes EN). |
| #17 User APIs (open, assigned to you) | Not started. No gateway route for `/api/v1/notifications/**`. |
| #18 Real-time + UI (open) | Backend push mechanism exists (RT-101), but only for ephemeral `OUTBID`. Frontend `NotificationBell/Panel/Toast` + `notificationStore` are local-only (no API, no socket). `socket.io-client` is still installed, and the bidding epic's FE-101 has not yet moved to STOMP. |
| #19 Smart features (open) | Not started. `NotificationServiceImpl.handleBidPlaced/AuctionEnded/PaymentEvent/AuctionCreated` are TODO stubs. `AuctionEndedEvent.loserIds` is always empty. wallet emits `PaymentEvent` REQUIRED/COMPLETED/FAILED but no REMINDER_24H. No ending-soon anywhere. |

## Decisions (conflict resolution; treat as spec)

1. **Outbid batch window = 5 minutes (300 s), configurable** (`notification.outbid.batch-window-seconds`). This follows the MVP doc; #19 says 2 min and must be amended. The first outbid in a quiet window is sent immediately. Later outbids in the window are summed and flushed as one "outbid N times" notification when the window closes.
2. **Seller "new bid" alerts are batched by the same mechanism** (key `NEW_BID`), and the first bid on an auction is sent immediately as `FIRST_BID`. Otherwise a busy auction spams the seller.
3. **One winner email, not two.** At T0 wallet's `PaymentEvent REQUIRED` carries the amount, remaining amount and deadline. That event drives the "You won, pay within 48h" email (= payment reminder #1). `AuctionEndedEvent` drives only the in-app `AUCTION_WON` for the winner, plus in-app + email `AUCTION_LOST` for losers.
4. **Losers and active bidders come from media's own projection** (`media_auction_participants`, fed by `bid-placed-topic`), not from `AuctionEndedEvent.loserIds` (empty) and not from a Feign call to bidding-service. wallet-service already derives losers locally in the same way.
5. **user-service owns preferences.** `user_preferences.language` ("en"/"vi") and `emailNotifications` are read through user-service's internal profile batch endpoint. `media_user_preferences` is left unused, and is not dropped (no destructive migration). Transactional emails (OTP, payment reminders, payment result, won) ignore `emailNotifications=false`. Engagement emails (auction created, lost, refund) respect it.
6. **Idempotency by unique key, not a 1-minute hash window.** `UNIQUE (user_id, dedup_key)` on `media_notifications`, and email logs link to `notification_id`. Kafka redelivery is a no-op. Key formats are fixed in Story 1.
7. **Ending-soon thresholds are a platform default, not set by the seller.** Every auction uses the same thresholds from config (`auction.ending-soon.thresholds-minutes: [60, 15]`). There is no DB column, no create/edit form field and no per-user settings page. JobRunr jobs in auction-service fire them and reschedule themselves on extension (the closure-job pattern). Seller-configurable thresholds and per-user defaults from #19 are **out of scope**.
8. **Recipients of ending-soon, extension and cancellation notifications** are the bidders in `media_auction_participants` plus the seller for extension/cancellation. Extension notifications are in-app only, coalesced to at most one per user per auction per 5 minutes (`dedup_key` includes a 5-minute bucket).
9. **No `POST /api/v1/internal/notifications/send`.** All triggers are events (AGENTS.md event-driven rule, YAGNI).
10. **The ephemeral per-bid `OUTBID` user-queue push is removed** once Story 5 lands. The auction page derives "you were outbid" from `BID_PLACED` on the auction topic. The notification center receives only `NOTIFICATION` messages, so no double toasts. The bidding roadmap's FE-101 task 7.5 must subscribe to `/user/queue/notifications` for `NOTIFICATION` only.
11. **Out of scope:** SMS/push channels, rate limiting, desktop browser notifications, sounds, per-connection delivery logging, template versioning, seller-configurable ending-soon thresholds, and **all of NOTIF-103** (the #16 gaps): automatic email retry (+5 min/+15 min), admin manual/bulk retry, delivery stats and the admin email-log UI changes. An email is sent once. If it fails, it is logged as `FAILED` in `media_email_logs` (current behaviour), and the in-app notification is still created.
12. **Template language resolution stays in scope.** It moves into Story 1 as a small `TemplateResolver`. Each new email template ships in the story that first uses it (Story 4, Story 6).

## Global Constraints

- media-service keeps its own DB (`media_*` tables). There are no cross-DB queries. Cross-service data comes from events or internal Feign endpoints only.
- New/changed event DTOs go in `common/dto/event`. Fields are only added, never removed or renamed. `user-notification-push-topic`'s payload is media-internal and lives in `media/realtime`.
- Kafka publishes happen in `afterCommit()` hooks (the `AuctionClosureService` pattern). Domain consumers use the shared `${spring.kafka.consumer.group-id}`. Push consumers use a per-instance group with `auto.offset.reset=latest`.
- `userId` on user endpoints always comes from `@AuthenticatedUserId` (`X-User-Id`). A user can only read or modify their own rows. Anything else returns 404, not 403 (so notification IDs cannot be probed).
- Internal endpoints stay under `/api/v1/**/internal/**` and are never added to gateway routes. `AuthenticationFilter` already blocks them.
- Responses are `ResponseEntity<BaseResponse<T>>`, pages are `PageResponse<T>`, and errors are `BaseException` subclasses.
- Every tunable is config, never hard-coded: batch window (300 s), reminder offset (24h) and ending-soon thresholds (`[60, 15]` minutes).
- Multi-instance safety: schedulers claim work atomically (the `ZREM` result in Redis, the row lock in wallet). There is no ShedLock.
- Send-side failures never break the consumer. Email failure → email log `FAILED`, with no retry. Push failure → WARN log, and the row still exists for the next REST fetch.
- Conventional commits (`feat(notification): …`, `feat(wallet): …`, `feat(auction): …`). **Agents do not commit.** The user commits.
- Maven from `backend/`: `mvn -q -pl <service> -am test`. Frontend: `npm run lint && npm run build`.
- Update `/docs` whenever an API, event or table changes (AGENTS.md directive 4). That work is in Story 9.

---

## Story map & sequencing

| # | Story | GitHub | Service(s) | Depends on |
|---|---|---|---|---|
| 1 | NOTIF-101: dispatcher, idempotent persistence, push topic, auction/participant projection, recipient directory | #15 gap (new sub-issue) | media-service, user-service (read-only check) | — |
| 2 | NOTIF-102: user notification APIs + gateway route | #17 | media-service, api-gateway | 1 |
| ~~3~~ | ~~NOTIF-103: email reliability~~ **Removed, out of scope** (Decision 11) | #16 | — | — |
| 4 | NOTIF-104: event → notification matrix (welcome, created, won/lost, payment, refund, cancelled, extended, first bid) + its templates | #19 §4 + #15 routing | media-service | 1 |
| 5 | NOTIF-105: outbid + seller new-bid batching (Redis, 5 min window) and removal of the ephemeral OUTBID push | #19 §1, §5 | media-service | 4 |
| 6 | NOTIF-106: payment reminder #2 (wallet scheduler → `REMINDER_24H`) + its template | #19 §3 | wallet-service, media-service | 4 |
| 7 | NOTIF-107: ending-soon (fixed default thresholds, JobRunr jobs, event, handler) | #19 §2, §6 | auction-service, media-service | 4 |
| 8 | NOTIF-108: frontend notification center (STOMP client, store, bell, panel, page, toasts) | #18 | frontend | 2 (API), 1 (push) |
| 9 | DOC: architecture, diagram 07, schema doc, API docs, epic housekeeping | epic DoD | docs | all |

```
               ┌─► [2 NOTIF-102] ──────────────────────► [8 NOTIF-108] ─┐
 [1 NOTIF-101]─┤                                                         ├─► [9 DOC]
               └─► [4 NOTIF-104] ─┬─► [5 NOTIF-105] ─────────────────────┤
                                  ├─► [6 NOTIF-106] ─────────────────────┤
                                  └─► [7 NOTIF-107] ─────────────────────┘
```

Story numbers and NOTIF IDs are kept stable after removing Story 3, so they still match GitHub. Stories 2 and 4 can run in parallel after 1, and so can 5, 6 and 7 after 4. Story 8 can start once 2 lands, using REST only, and gains live data as 4–7 land.

**Branching:** `feature/notification`, cut from `feature/bidding`, because RT-101's STOMP infrastructure and the bid events are not yet on `main`. Commit per story with conventional commits. If FE-101 (bidding Story 7) is still open when Story 8 starts, Story 8 owns the shared STOMP client (task 8.1) and FE-101 reuses it.

**GitHub housekeeping (the user does this, or asks me to):**
- Reopen epic #14. It is closed but #17–#19 are open.
- Leave #16 closed, and add a comment that retry, admin retry and stats are out of scope for the MVP (Decision 11). Optionally file a post-MVP backlog issue for them.
- File NOTIF-101 as a sub-issue of #14.
- Amend #19 with Decisions 1–3 and 7–8: 5-minute window, and fixed ending-soon thresholds with no seller settings.
- Amend #18 with Decision 10.

---

## Story 1 — NOTIF-101: notification pipeline foundation

**Deliverable:** Any handler can call `dispatcher.dispatch(intent)`. The result is a persisted row (exactly once per `(userId, dedupKey)`), an optional email, and a live push on `/user/queue/notifications` from whichever instance owns the socket. media-service records auctions and their bidders from events. Nothing user-visible changes yet except that welcome emails go through the dispatcher.

**Files** (under `backend/media-service/`):
- Create `src/main/resources/db/changelog/migrations/06-notification-pipeline.sql`:
  - `media_notifications`: add `dedup_key VARCHAR(200) NOT NULL DEFAULT ''`, `deleted_at TIMESTAMP`, `UNIQUE (user_id, dedup_key)`, and index `(user_id, deleted_at, created_at DESC)` plus partial index `WHERE read_at IS NULL AND deleted_at IS NULL`
  - new `media_auctions (auction_id UUID PK, title, seller_id UUID, end_time TIMESTAMPTZ, updated_at)`
  - new `media_auction_participants (auction_id, user_id, last_bid_amount NUMERIC(19,2), last_bid_at, PK(auction_id,user_id))`
  - Register it in `db.changelog-master.xml`.
- Modify `domain/entity/Notification.java` (`dedupKey`, `deletedAt`).
- Create `notification/TemplateResolver.java`: `NotificationTemplate resolve(String baseName, NotificationLanguage)` looks up `{baseName}_{lang}`, falls back to EN when the VI template is missing or inactive, and returns empty (with an ERROR log) when neither exists. It replaces the private `resolveLanguage()` in `NotificationServiceImpl`.
- Modify `EmailService`/`EmailServiceImpl`: add the overload `sendTemplateEmail(UUID notificationId, String to, NotificationTemplate t, Map vars)`, which sets `EmailLog.notificationId`. The existing single-attempt send and FAILED logging are unchanged.
- Create `repository/NotificationInboxRepository.java` and `repository/AuctionProjectionRepository.java` (JDBC; upserts are order-tolerant) and the projection/AuctionRef record.
- Modify `domain/enums/NotificationType.java`, adding `FIRST_BID`, `NEW_BID`, `AUCTION_EXTENDED`, `AUCTION_CREATED`, `PAYMENT_REQUIRED`, `PAYMENT_FAILED`. Only add values, never remove.
- Create `notification/NotificationIntent.java`: `record NotificationIntent(UUID userId, NotificationType type, String dedupKey, UUID auctionId, String title, String message, String actionUrl, Map<String,Object> metadata, EmailSpec email)`, where `record EmailSpec(String templateBaseName, Map<String,Object> variables, boolean transactional)` may be null.
- Create `notification/DedupKeys.java`, static builders with fixed formats:
  - `welcome(userId)` → `WELCOME`
  - `auctionCreated(a)` → `AUCTION_CREATED:{a}`
  - `won(a)` → `AUCTION_WON:{a}`
  - `lost(a)` → `AUCTION_LOST:{a}`
  - `payment(type,a)` → `PAYMENT_{type}:{a}`
  - `refund(a)` → `DEPOSIT_REFUNDED:{a}`
  - `cancelled(a)` → `AUCTION_CANCELLED:{a}`
  - `extended(a, bucket)` → `AUCTION_EXTENDED:{a}:{epochMin/5}`
  - `firstBid(a)` → `FIRST_BID:{a}`
  - `batch(type,a,windowStartEpochMs)` → `{type}:{a}:{windowStart}`
  - `endingSoon(a,minutes)` → `ENDING_SOON:{a}:{minutes}`
- Create `notification/RecipientDirectory.java`: `Map<UUID, Recipient> resolve(Collection<UUID>)`, where `record Recipient(UUID userId, String email, NotificationLanguage language, boolean emailOptIn)`. Emails come from `IdentityServiceClient.getEmailsByUserIds`, and language + opt-in from a new `feign/UserServiceClient.getNotificationPreferences` (new POST /api/v1/users/internal/notification-preferences, max 100 IDs per call, chunked). If either service fails, the fallback is EN with opt-in true and a WARN log. A missing email skips the email part only.
- Create `notification/NotificationDispatcher.java`, `@Transactional`:
  - `insert … on conflict (user_id, dedup_key) do nothing returning id`. A conflict means a redelivery, so return immediately.
  - Inside the transaction: idempotent insert (`ON CONFLICT DO NOTHING`) plus the unread count.
  - After commit (AfterCommit), per delivery: push first (`UserNotificationPushPublisher.publish(userId, NotificationResponse, unreadCount)`), then email via `EmailService.sendTemplateEmail(notificationId, …)` in `REQUIRES_NEW`, with the template resolved via `TemplateResolver` for the recipient's language.
  - Each delivery is isolated in its own try/catch, so a failed push or email never affects the committed row or other deliveries. A recipient lookup failure falls back to no email (pushes continue).
  - Batch helper `dispatchAll(List<NotificationIntent>)` resolves recipients once.
- Create `realtime/UserNotificationMessage.java` (`record(String type="NOTIFICATION", NotificationResponse notification, long unreadCount)`), `kafka/UserNotificationPushPublisher.java` (topic `user-notification-push-topic`, key userId), and `kafka/UserNotificationPushConsumer.java` (per-instance group `media-push-${random.uuid}`, `latest`, → `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", msg)`, catching and WARN-logging failures).
- Create `dto/response/NotificationResponse.java` (`id, type, title, message, actionUrl, auctionId, metadata, read, createdAt`) and a mapper. Story 2 reuses both.
- Create `kafka/AuctionProjectionConsumer.java` (own stable group media-projection-group: sharing media-service-group on bid-placed-topic would split partitions with NotificationKafkaConsumer): `auction-created-topic` → upsert `media_auctions`; `bid-placed-topic` → upsert `media_auctions.title` + participant; `auction-extended-topic` → update `end_time`. Keep it separate from `NotificationKafkaConsumer` so the projection runs in its own listener container.
- Modify `service/impl/NotificationServiceImpl.java`: `handleUserRegistered` builds a `NotificationIntent` (in-app "Welcome" + `WELCOME_EMAIL` transactional=false) and calls the dispatcher. OTP stays a direct email (no in-app row, which is correct because the user does not exist yet).
- Modify `pom.xml`: nothing yet (Redis arrives in Story 5).

**Tasks:**
- [x] **1.1 Migration + entities + repositories.** `mvn -q -pl media-service -am test` stays green. Start against docker-compose Postgres: the changelog applies cleanly on a DB that already has 01–05.
- [x] **1.2 `DedupKeys`.** Table-driven unit test for every format, including the 5-minute bucket boundary (`12:04:59` and `12:05:00` fall in different buckets).
- [x] **1.3 `RecipientDirectory`.** Tests:
  - one batch call per service per `resolve`
  - `"vi"` → VI, unknown/null → EN
  - identity down → recipients without email, no exception
  - user-service down → EN/opt-in true
- [x] **1.4 `NotificationDispatcher`.** Tests (repository mocked, plus one `@DataJpaTest`-style test against the BDD Postgres if available):
  - first dispatch inserts, sends email and registers afterCommit push
  - same `(userId, dedupKey)` again → no second row, no email, no push
  - `email == null` → no email
  - `transactional=false && !emailOptIn` → no email but the row is still created
  - recipient without an email address → row created, email skipped with a WARN
  - `EmailService` throws → row is still committed and the push is still sent
- [x] **1.4a `TemplateResolver`.** Tests: VI present → VI; VI inactive → EN; neither → empty + ERROR log.
- [x] **1.5 Push publisher + consumer.** Tests: the publisher is called only after commit (simulate with `TransactionSynchronizationManager`). The consumer calls `convertAndSendToUser(userId, "/queue/notifications", msg)`, and a `MessagingException` is swallowed. Assert the listener's group ID and offset property the same way `AuctionRealtimeConsumerTest` does.
- [x] **1.6 `AuctionProjectionConsumer`.** Tests: created → auction upserted; two bids by the same user → one participant row with the latest amount; extended → `end_time` updated; out-of-order bid before created → auction row still created with title/seller null-safe.
- [x] **1.7 Welcome through the dispatcher.** Update `NotificationServiceImpl` tests: register event → one intent with `DedupKeys.welcome`, and the email uses `WELCOME_EMAIL_{lang}`.

---

## Story 2 — NOTIF-102: user notification APIs (#17)

**Deliverable:** The #17 endpoints are live through the gateway, scoped to the caller, with pagination, filtering and search.

**Files:**
- media-service:
  - `controller/NotificationController.java` at `/api/v1/notifications`
  - `service/UserNotificationService.java` + `impl`
  - `dto/request/NotificationQuery.java` (`page`, `size` 1–100, `read: Boolean`, `types: List<NotificationType>`, `from/to: OffsetDateTime`, `search` ≤ 100 chars)
  - `repository/NotificationRepository.java`, adding:
    - `countByUserIdAndReadAtIsNullAndDeletedAtIsNull`
    - `@Modifying` bulk updates `markAllRead(userId, now)`, `softDeleteAll(userId, now)`, `softDeleteRead(userId, now)`
    - `findByIdAndUserIdAndDeletedAtIsNull`
  - `SecurityConfig`: no change (`anyRequest().authenticated()` already covers it); `exception/MediaExceptionHandler.java` maps invalid path/query values to 400
- api-gateway: `application.yml` route `media-service` gains `/api/v1/notifications/**`.

**Endpoints** (all use the caller's `X-User-Id`):

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/notifications` | `page` (0-based), `size` default 20, max 100, sort `createdAt,desc`; filters `read`, `types`, `from`, `to`, `search` (ILIKE on title/message) |
| GET | `/api/v1/notifications/unread-count` | `{ "count": n }` |
| GET | `/api/v1/notifications/{id}` | Marks the notification as read as a side effect (#17 "auto-mark as read") |
| PUT | `/api/v1/notifications/{id}/read` · `/{id}/unread` | Idempotent |
| PUT | `/api/v1/notifications/mark-all-read` | Returns the updated count |
| DELETE | `/api/v1/notifications/{id}` · `/delete-all` · `/delete-read` | Soft delete (`deleted_at`) |

Inbox edits do not push anything (decided 2026-10-01: multi-tab badge sync is not needed for the MVP). The badge stays correct because every `NOTIFICATION` push carries a fresh `unreadCount`, and the frontend refetches `/unread-count` when a tab regains focus (Story 8).

**Tasks:**
- [x] **2.1 Repository queries.** Tests against Postgres (BDD support or Testcontainers):
  - the bulk update touches only the caller's non-deleted rows
  - unread count ignores deleted rows
  - the specification filters combine (read + type + date + search)
- [x] **2.2 `UserNotificationService`.** Tests:
  - another user's ID → `NotFoundException`
  - `get` marks the notification read once, and `read_at` is unchanged on the second call
  - unread → sets `read_at=null`
  - mark-all/delete-all/delete-read return the changed-row counts
  - size 101 → 400
- [x] **2.3 Controller.** Standalone MockMvc: every endpoint with the happy path; missing `X-User-Id` → 401 (BDD/security slice as in bidding); invalid UUID → 400; `PageResponse` shape.
- [x] **2.4 Gateway route.** Unit/route test or smoke test: `GET localhost:8080/api/v1/notifications` with a JWT reaches media-service, and `/api/v1/internal/**` is still blocked.
- [ ] **2.5 API docs.** Regenerate media-service/api-docs.json from the running service (manual, see the NOTIF-102 plan Task 6). The Postman collection moves to Story 9.

---

## Story 3 — NOTIF-103: removed (out of scope)

Email retry, admin manual/bulk retry, delivery stats and the admin email-log UI are out of scope (Decision 11). Template language resolution moved to Story 1 (`TemplateResolver`). The new templates moved to Story 4 (`PAYMENT_REQUIRED`, `PAYMENT_FAILED`, `AUCTION_CANCELLED`) and Story 6 (`PAYMENT_REMINDER_24H`). `EmailServiceImpl.retryEmail` keeps throwing `UnsupportedOperationException`.

---

## Story 4 — NOTIF-104: event → notification matrix

**Deliverable:** Every row below produces the listed notifications exactly once per event (redelivery-safe).

| Event (topic) | Recipient(s) | Type | In-app | Email (template) |
|---|---|---|---|---|
| `user-registered-topic` | user | `USER_REGISTERED` | ✅ | `WELCOME_EMAIL` (Story 1) |
| `auction-created-topic` | seller | `AUCTION_CREATED` | ✅ | `AUCTION_CREATED` |
| `bid-placed-topic` (`totalBids==1`) | seller | `FIRST_BID` | ✅ | — |
| `auction-extended-topic` | participants + seller | `AUCTION_EXTENDED` | ✅ (coalesced 5 min) | — |
| `auction-ended-topic` (winner) | winner | `AUCTION_WON` | ✅ | — (email comes from PAYMENT REQUIRED, Decision 3) |
| `auction-ended-topic` | participants − winner | `AUCTION_LOST` | ✅ | `AUCTION_LOST` |
| `auction-ended-topic` (no winner) | seller | `AUCTION_UNSOLD` (new) | ✅ | — |
| `auction-cancelled-topic` | participants | `AUCTION_CANCELLED` | ✅ | `AUCTION_CANCELLED` |
| `payment-event-topic` REQUIRED | winner | `PAYMENT_REQUIRED` | ✅ | `PAYMENT_REQUIRED` (transactional) |
| `payment-event-topic` COMPLETED | winner, seller | `PAYMENT_RECEIVED` | ✅ | `PAYMENT_SUCCESSFUL` (winner) |
| `payment-event-topic` FAILED | winner | `PAYMENT_FAILED` | ✅ | `PAYMENT_FAILED` (transactional) |
| `deposit-refunded-topic` | user | `DEPOSIT_REFUNDED` | ✅ | `DEPOSIT_REFUNDED` |

**Files:**
- media-service:
  - `notification/handler/` (one class per source domain; each builds `List<NotificationIntent>` and calls `dispatcher.dispatchAll`):
    - `AuctionNotificationHandler` (created, ended, cancelled, extended)
    - `BidNotificationHandler` (first bid; batching comes in Story 5)
    - `PaymentNotificationHandler` (payment + refund)
  - `NotificationKafkaConsumer`: add `deposit-refunded-topic`, `auction-cancelled-topic` and `auction-extended-topic` listeners (shared group), and delegate to handlers. `NotificationServiceImpl` stubs are deleted, and the interface shrinks to OTP + welcome.
  - `NotificationType`: add `AUCTION_UNSOLD`
  - `src/main/resources/db/changelog/migrations/07-event-templates.sql` (+ master changelog): `PAYMENT_REQUIRED_{EN,VI}` (= reminder #1 / won; variables `userName, auctionTitle, amount, remaining, paymentDeadline, actionUrl, insufficientFundsNote`), `PAYMENT_FAILED_{EN,VI}` (deposit forfeited), `AUCTION_CANCELLED_{EN,VI}`. List each template's variables in a comment at the top of the file, because task 4.4 checks them.
  - `notification/Messages.java`: in-app title/message builders, one per type, EN only, code-side (not templated) for MVP. VI in-app copy waits for `IN_APP` templates (post-MVP).
  - Action URLs are built from `app.frontend.base-url`: `/auctions/{id}`, payments → `/wallet`, refund → `/wallet`.
  - Titles come from `media_auctions` when the event lacks them (`PaymentEvent.auctionTitle` is null today, and `DepositRefundedEvent` has no title).

**Tasks:**
- [ ] **4.1 `AuctionNotificationHandler`.** Tests:
  - ended with a winner and 3 participants → 1 WON + 2 LOST intents with the correct dedup keys
  - ended without a winner → 1 UNSOLD to the seller
  - cancelled with 0 participants → no intents
  - extended twice within 5 minutes → both intents share a dedup key
  - created → seller intent with email
- [ ] **4.2 `BidNotificationHandler` (first bid only).** `totalBids==1` → FIRST_BID to the seller; `totalBids>1` → nothing (until Story 5).
- [ ] **4.3 `PaymentNotificationHandler`.** Tests:
  - REQUIRED with `insufficientFunds=true` adds the top-up note variable
  - COMPLETED → winner email + seller in-app
  - FAILED → transactional email
  - an unknown `paymentType` → WARN, no intent
  - refund → user intent with the title looked up from the projection (a missing title falls back to "your auction")
- [ ] **4.4 Template-variable coverage test.** For each (handler, template) pair, render against the seeded template and assert that no `{…}` placeholder is left.
- [ ] **4.5 Consumer wiring.** One test per new listener verifying the delegation. Manual smoke with docker-compose: close an auction with 2 bidders → rows appear for both, and Mailtrap receives LOST + PAYMENT_REQUIRED.

---

## Story 5 — NOTIF-105: outbid & new-bid batching (#19 §1)

**Deliverable:** A user outbid 3 times in 5 minutes receives 1 immediate notification plus 1 "outbid 2 more times, current price $Y" at window close. The seller's per-bid alerts are batched the same way. Duplicate toasts are gone.

**Algorithm** (Redis, per `(kind, userId, auctionId)`, where kind ∈ {BID_OUTBID, NEW_BID}):
- Key `notif:batch:{kind}:{userId}:{auctionId}`, a hash `{windowStart, count, latestAmount, latestBidder, auctionTitle}`, TTL = window + 60 s.
- On event: `HSETNX windowStart now`.
  - If it was set (no open window), **dispatch immediately** (count 1), leave `count=0`, and `ZADD notif:batch:due (now+window) key`.
  - Otherwise `HINCRBY count 1` + `HSET latest…`.
  - All of this runs in one Lua script so it is atomic.
- `BatchFlushScheduler` (`fixedDelay 15s`): `ZRANGEBYSCORE due -inf now LIMIT 100`. For each key, `ZREM` (only the instance that removes the key proceeds), read the hash, `DEL` it, and if `count>0` dispatch one batched intent with `DedupKeys.batch(kind, auctionId, windowStart)`.
- If Redis is down, dispatch every event immediately (degrade to noisy, never silent), with a WARN.

**Files:**
- media-service:
  - `pom.xml` + `spring-boot-starter-data-redis`, and `application.yml` `spring.data.redis.host: ${REDIS_HOST:localhost}` + `notification.outbid.batch-window-seconds: 300`
  - `config/SchedulingConfig.java` (`@EnableScheduling`; first scheduler in media-service)
  - `notification/batch/BidBatcher.java` + `src/main/resources/redis/batch-open-or-increment.lua`
  - `scheduler/BatchFlushScheduler.java`
  - `BidNotificationHandler`: previous winner → `BID_OUTBID` batch; seller → `NEW_BID` batch (skipped when `totalBids==1`, because that is FIRST_BID)
  - `realtime/AuctionRealtimeBroadcaster.bidPlaced`: remove the `sendToUser(previous, OUTBID…)` branch and the `Outbid` payload. Update `AuctionRealtimeBroadcasterTest` and `RealtimeMessageJsonTest`.
- repo root `docker-compose.yml`: `REDIS_HOST` for media-service.
- docs: note Decision 10 in the bidding roadmap, FE-101 task 7.5.

**Tasks:**
- [ ] **5.1 Lua + `BidBatcher`** (Testcontainers Redis or embedded):
  - first event → returns `IMMEDIATE`
  - 2nd/3rd within the window → `BATCHED`, `count=2`
  - an event after the key is flushed → `IMMEDIATE` again
  - separate auctions/users are independent
- [ ] **5.2 Flush scheduler.** Tests:
  - due key with `count=2` → one intent whose message says 2 more times and carries the latest price
  - `count=0` → no intent
  - two schedulers racing → exactly one dispatch (the `ZREM` result gates it)
- [ ] **5.3 Redis-down fallback.** `RedisConnectionFailureException` → immediate dispatch plus a WARN.
- [ ] **5.4 Handler + broadcaster change.** The bidder never gets BID_OUTBID for outbidding themselves (`previous == bidderId`). The broadcaster sends no user-queue message on BID_PLACED any more.
- [ ] **5.5 Manual smoke:** three quick bids by B, C and D over A → A's socket receives 1 NOTIFICATION immediately and 1 about 5 minutes later. For a faster smoke, set `batch-window-seconds: 30` locally.

---

## Story 6 — NOTIF-106: payment reminder #2 (#19 §3)

**Deliverable:** 24 h after the payment hold is created, an unpaid winner receives reminder #2 exactly once. After 48 h the existing forfeit emits FAILED, and Story 4 already handles that.

**Files:**
- wallet-service:
  - `07-payment-hold-reminder.sql`: `payment_holds.reminder_sent_at TIMESTAMP`
  - `PaymentHold.reminderSentAt`
  - `PaymentHoldRepository.findDueForReminder(status, cutoff, pageable)` (`created_at <= cutoff AND reminder_sent_at IS NULL AND status='PENDING_PAYMENT'`)
  - `scheduler/PaymentReminderScheduler.java` (mirrors `ForfeitScheduler`: `fixedDelay` 5 min, batch 100)
  - `PaymentService.sendPaymentReminder(UUID auctionId)` (`@Transactional`, re-checks the status under `findByAuctionIdForUpdate`, sets `reminder_sent_at`, and publishes `PaymentEvent{paymentType="REMINDER_24H", deadline, remaining}` afterCommit)
  - `application.yml` `wallet.payment.reminder-after-hours: 24`
- media-service:
  - `PaymentNotificationHandler` maps `REMINDER_24H` → `PAYMENT_REMINDER` type + `PAYMENT_REMINDER_24H` template, transactional, with `DedupKeys.payment("REMINDER_24H", a)`
  - `08-payment-reminder-template.sql` (+ master changelog): `PAYMENT_REMINDER_24H_{EN,VI}` (variables `userName, auctionTitle, remaining, paymentDeadline, actionUrl`)

**Tasks:**
- [ ] **6.1 Migration + repository query.** Test: a hold at 23h59m is not due; one at 24h00m is due; one already reminded is not due; COMPLETED/FORFEITED holds are not due.
- [ ] **6.2 `sendPaymentReminder`.** Tests: the reminder is sent once (a second call is a no-op); a hold that became COMPLETED between the query and the lock → no event; the event is published only after commit.
- [ ] **6.3 Scheduler.** One failure does not stop the batch (same test shape as `ForfeitSchedulerTest`).
- [ ] **6.4 media handler + template.** REMINDER_24H → intent with the `paymentDeadline` variable formatted in the recipient's language. Rendering the seeded template leaves no `{…}` placeholder (same check as 4.4).

---

## Story 7 — NOTIF-107: auction ending soon (#19 §2)

**Deliverable:** For every auction, at each default threshold (60 and 15 minutes) before the *current* end time, every bidder gets an in-app "Auction X ends in 15 minutes". Extensions are handled. Sellers configure nothing, and nothing changes in the frontend or the auction schema.

**Files:**
- common: `dto/event/AuctionEndingSoonEvent.java` (`auctionId, auctionTitle, sellerId, endTime (OffsetDateTime), thresholdMinutes`).
- auction-service:
  - `config/EndingSoonProperties.java` (`@ConfigurationProperties("auction.ending-soon")`: `thresholds-minutes: [60, 15]`, validated to be positive and distinct) + `application.yml`
  - `service/EndingSoonScheduler.java`:
    - `scheduleAll(auction)` on activation, edit (end time changed) and startup recovery: one JobRunr job per configured threshold with deterministic ID `nameUUID(auctionId+":"+minutes+":"+endTime.toEpochMilli())`, skipping thresholds already in the past
    - `job/EndingSoonJob.fire(auctionId, minutes, expectedEndTime)`, which runs in its own transaction and loads the auction
      - if the status is not ACTIVE → no-op
      - if `endTime != expectedEndTime` (extended) → schedule for the new end time and return
      - otherwise publish `AuctionEndingSoonEvent` afterCommit
  - `AuctionBidService.applyBid` extension branch: call `endingSoonScheduler.scheduleAll` for the new end time. Old jobs self-cancel via the `expectedEndTime` check.
  - `AuctionKafkaProducer.publishEndingSoon` (`auction-ending-soon-topic`)
- media-service:
  - `NotificationKafkaConsumer` + `auction-ending-soon-topic`
  - `AuctionNotificationHandler.endingSoon` → participants, type `AUCTION_ENDING_SOON`, in-app only, `DedupKeys.endingSoon(a, minutes)`, message with a humanised threshold (e.g. "1 hour", "15 minutes", derived from the minutes)
  - optionally also push `ENDING_SOON` on `/topic/auctions/{id}` via `AuctionRealtimeConsumer`, for the countdown highlight (cheap: one listener + one broadcaster method)

**Tasks:**
- [ ] **7.1 `EndingSoonProperties`.** Tests: binds `[60, 15]` by default; an empty list disables scheduling platform-wide; duplicate or non-positive values fail startup validation.
- [ ] **7.2 `EndingSoonScheduler.scheduleAll`.** Tests with a fixed `Clock`: `endTime = now+50m` with thresholds `[60,15]` → only the 15-minute job (the 60-minute threshold is already past); job IDs are deterministic; rescheduling for the same end time does not duplicate (same ID).
- [ ] **7.3 `EndingSoonJob`.** Tests: ACTIVE with matching end → event afterCommit; extended → rescheduled for the new end and no event; CANCELLED/COMPLETED → no-op.
- [ ] **7.4 Extension hook.** `applyBid` extended → `scheduleAll` called with the new end time (extend the existing anti-snipe tests).
- [ ] **7.5 media handler.** Participants only (not the seller); the same event redelivered → one row per user.
- [ ] **7.6 Manual smoke.** Create an auction ending in 20 minutes and bid as another user. About 5 minutes later the bidder gets the 15-minute notification, and the 60-minute one is never sent because it was already past.

---

## Story 8 — NOTIF-108: frontend notification center (#18)

**Deliverable:** The bell shows the live unread count. The dropdown shows the last 10 notifications. `/notifications` lists everything with filters, search and bulk actions. New notifications arrive live with a toast. State stays in sync across tabs.

**Files** (under `frontend/`):
- `package.json`: add `@stomp/stompjs` and `sockjs-client` (+ `@types/sockjs-client`), and remove `socket.io-client` if FE-101 has not already.
- `lib/realtime/stompClient.ts`: a singleton `getStompClient()`. `webSocketFactory` builds `new SockJS(\`${NEXT_PUBLIC_WS_URL}/ws-notifications?access_token=${freshToken}\`)` on each (re)connect, and uses `reconnectDelay` backoff. `subscribe(dest, cb) → unsubscribe` is ref-counted, and `deactivate()` runs on logout. FE-101's `useAuctionSocket` must use this client.
- `types/api/notification.api.ts`: `NotificationDto`, `NotificationType` (a union mirroring the backend enum), `NotificationListParams`, `UnreadCountDto`, and `UserQueueMessage = { type: 'NOTIFICATION'; notification: NotificationDto; unreadCount: number }`.
- `types/mappers/notification.mapper.ts`: DTO → `types/ui/notification.ui.ts` `Notification`. Extend the UI `NotificationType` to cover the backend types, with an icon/colour map.
- `services/notification.service.ts`: list, unreadCount, get, markRead, markUnread, markAllRead, delete, deleteAll, deleteRead (via `lib/apiClient.ts`).
- `store/notificationStore.ts`: rewrite to be server-backed:
  - `recent` (10), `unreadCount`, `loadRecent()`, `refreshCount()`
  - optimistic `markRead`/`markAllRead`/`remove` that roll back on error
  - `applyPush(msg)`, which dedupes by `id`
- `hooks/useUserNotifications.ts`: when authenticated, `loadRecent` + `refreshCount` (again on window focus, to resync the badge after changes made in another tab), subscribe to `/user/queue/notifications` → `applyPush`, and show a toast for `NOTIFICATION` of types BID_OUTBID/AUCTION_WON/PAYMENT_REQUIRED/AUCTION_ENDING_SOON. Mounted once in the dashboard layout.
- `components/notification/NotificationBell.tsx`, `NotificationPanel.tsx`, `NotificationToast.tsx`: wire to the store; add item click → markRead + `router.push(actionUrl)`; add "Mark all as read" and "View all".
- `components/notification/NotificationItem.tsx` (new, shared by panel and page) and `lib/formatTimeAgo.ts` (just now / 5 min ago / 2 hours ago / yesterday / date), with a unit test.
- `app/(dashboard)/notifications/page.tsx`:
  - tabs All/Unread, type filter, debounced search, pagination
  - per-row read/unread/delete
  - bulk select → mark read / delete (confirm dialog)
  - empty and loading states, responsive layout
- `.env.example`: `NEXT_PUBLIC_WS_URL`.

**Tasks:**
- [ ] **8.1 STOMP client.** Unit test with a mocked `Client`: two subscribers to the same destination → one STOMP subscription; the last unsubscribe → STOMP unsubscribe; a fresh token is read on every reconnect.
- [ ] **8.2 Types + service + mapper.** Mapper tests for every backend type → icon/colour, and unknown → `system`.
- [ ] **8.3 Store.** Tests: `applyPush` NOTIFICATION prepends and sets the count from the server value (not +1); duplicate ID ignored; failed `markRead` rolls back.
- [ ] **8.4 Bell + panel + toast.** Follow the frontend-ui-engineering skill and `docs/design-system.md` tokens. Keyboard accessible dropdown (Esc closes, focus returns to the bell).
- [ ] **8.5 Full page.** Filters live in URL search params so the page can be shared and restored.
- [ ] **8.6** `npm run lint && npm run build`. Manual E2E: two browsers; A is outbid → the bell increments live and a toast shows; marking read in tab 1 updates the count in tab 2 once tab 2 regains focus; a 375 px viewport renders correctly.

---

## Story 9 — Documentation & closure (epic DoD)

- [ ] `docs/architecture.md`: the media-service notification pipeline (dispatcher, projection, push topic), new topics (`user-notification-push-topic`, `auction-ending-soon-topic`), and wallet/auction schedulers as event sources.
- [ ] `docs/diagrams/07-notification-delivery-flow.md` (+ `.svg`): Kafka instead of RabbitMQ, STOMP instead of Socket.io, Mailtrap/SMTP, the dispatcher + dedup step, and the Redis 5-minute batch window. Email failure is logged only; there is no retry.
- [ ] `docs/database/notification-service.schema.md`: `media_` table names, new columns, projection tables. `docs/database.md`: wallet `reminder_sent_at`.
- [ ] `docs/functional.md` / `docs/business-clarifications.md` §5: Decisions 1–3, 5, 7 and 11.
- [ ] `docs/epics/notification/notification-service-mvp.md`: mark as superseded by this roadmap where they differ (window, endpoints, won email).
- [ ] Link the per-story plans from this roadmap. Close #17–#19 and the epic.

---

## Risks carried forward

- **Projection lag vs. handlers.** The projection and notification handlers consume bid-placed-topic in different groups, so an auction-ended handler (Story 4) could read participants before the final bid is projected. Anti-sniping keeps the last bid ≥ minutes before the end, so this is unlikely. If it shows up, have the ended handler also include AuctionEndedEvent.winnerId explicitly.
- **`user-notification-push-topic` adds one Kafka hop.** The expected latency is tens of milliseconds, within the epic's <200 ms target, but measure it during the Story 5 smoke test. Per-instance `media-push-<uuid>` groups accumulate like the `media-realtime-*` ones (see the bidding roadmap).
- **No email retry (NOTIF-103 out of scope).** A transient SMTP failure permanently loses that email, including transactional ones (payment required, reminder #2, forfeit). The in-app notification still exists, and the failure is visible in the existing admin email-log list as `FAILED`. Revisit before production.
- **Synchronous SMTP on the consumer thread.** A slow SMTP server slows every listener in the shared group.
- **Projection gaps.** Bids placed before Story 1 is deployed are not in `media_auction_participants`, so auctions that are live during the rollout may miss LOST notifications. Acceptable in dev. For a real rollout, backfill once from bidding-service (`GET /api/v1/bids/auction/{id}`) with a one-off admin job, or accept the gap.
- **Kafka outage after commit** loses pushes but not rows. Clients resync the unread count on focus/reconnect (Story 8 calls `refreshCount` on STOMP reconnect).
- **Feign fan-out on large auctions.** `RecipientDirectory` makes one identity-service call and one user-service `POST /api/v1/users/internal/notification-preferences` call per 100 recipients (chunked). Large fan-outs mean many sequential Feign calls on the consumer thread (see the next risk).
- **Connections held during delivery (Story 4 prerequisite).** afterCommit runs before the outer connection is released, so `deliver()` holds it across Feign lookups and every SMTP send, and each REQUIRES_NEW email borrows a second pooled connection for the SMTP duration. Harmless for single-recipient welcome; before Story 4's `dispatchAll` over many participants: wrap only `emailLogRepository.save` in a `TransactionTemplate` (send mail outside any transaction) and hand `deliver()` to a bounded async executor so the commit hook only enqueues.
- **Welcome email depends on identity-service.** The dispatcher resolves the address via RecipientDirectory even though `UserRegisteredEvent` carries it; during an identity outage the welcome email is skipped. Fix when convenient: optional fallback email on `EmailSpec`, used when the lookup returns none.
- **Metadata serialisation failure fails a whole batch.** `NotificationInboxRepository` throws on unserialisable metadata, rolling back the entire `dispatchAll`. Before Story 4 batch handlers: validate metadata when building intents, or skip only the offending intent.
- **Smoke run is a merge gate for NOTIF-101.** No automated test exercises the REQUIRES_NEW proxy from afterCommit, the @Transactional boundary on replay, or STOMP delivery. Before merging verify: (a) a `media_email_logs` row with `notification_id` set after a welcome; (b) replaying the same `user-registered-topic` record creates no second row, email or push; (c) a frame arrives on `/user/queue/notifications` for a connected user.
- **JobRunr job growth.** One ending-soon job per configured threshold (2 by default) per auction per extension. Stale jobs are no-ops but remain in JobRunr's succeeded list until its retention cleanup runs.
- **In-app copy is EN only** (Decision in Story 4). VI in-app copy needs `IN_APP` templates, which are post-MVP.
