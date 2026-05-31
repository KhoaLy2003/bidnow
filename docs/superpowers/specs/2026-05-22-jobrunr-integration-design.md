# JobRunr Background Job Processing — Design Spec

**Date**: 2026-05-22  
**Status**: Approved

## Overview

Integrate [JobRunr 7.x](https://www.jobrunr.io/en/) as the background job processing framework across `auction-service`, `wallet-service`, and `media-service`. JobRunr replaces ad-hoc inline processing inside Kafka consumers with persistent, retryable, observable background jobs backed by the existing shared PostgreSQL database.

## Architecture

### Storage

All three services share a **single set of JobRunr tables** in the existing shared PostgreSQL database:

- `jobrunr_jobs`
- `jobrunr_recurring_jobs`
- `jobrunr_background_job_servers`
- `jobrunr_metadata`

Tables are created once via a **Liquibase migration** in `auction-service`. `wallet-service` and `media-service` set `org.jobrunr.database.skip-create: true` to avoid duplicate table creation attempts.

### Background Job Servers

Each service runs its **own background job server** (worker thread pool). Workers only execute jobs whose classes exist on their classpath, so:

- `auction-service` workers execute `StartAuctionJob` and `EndAuctionJob`
- `wallet-service` workers execute `ProcessAuctionPaymentJob`
- `media-service` workers execute `SendNotificationJob` and `SendEmailJob`

### Dashboard

The JobRunr dashboard is exposed **only on `auction-service`** at `localhost:8083/dashboard`. Since all services write to the same job tables, the dashboard provides a unified view of all jobs across all three services.

### Kafka Role

Kafka remains the **inter-service event bus** — unchanged. Kafka consumers become thin dispatchers: receive event → enqueue JobRunr job. All actual processing happens inside the job.

## Dependencies

Add to root `pom.xml` under `<dependencyManagement>`:

```xml
<dependency>
    <groupId>org.jobrunr</groupId>
    <artifactId>jobrunr-spring-boot-3-starter</artifactId>
    <version>7.3.1</version>
</dependency>
```

Declare `jobrunr-spring-boot-3-starter` (no version) in `auction-service/pom.xml`, `wallet-service/pom.xml`, and `media-service/pom.xml`.

## Configuration Per Service

### auction-service (`application.yml`)

```yaml
org.jobrunr:
  background-job-server:
    enabled: true
  dashboard:
    enabled: true
    port: 8083
  database:
    skip-create: false
```

### wallet-service & media-service (`application.yml`)

```yaml
org.jobrunr:
  background-job-server:
    enabled: true
  dashboard:
    enabled: false
  database:
    skip-create: true
```

## Jobs

### auction-service

| Job | Type | Parameter | Action |
|-----|------|-----------|--------|
| `StartAuctionJob` | Delayed (fires at `startTime`) | `auctionId: UUID` | SCHEDULED → ACTIVE; publishes `AuctionStartedEvent` to Kafka (new event DTO to be added to `common`) |
| `EndAuctionJob` | Delayed (fires at `endTime`) | `auctionId: UUID` | ACTIVE → ENDED; determines winner via Feign call to `bidding-service`; publishes `AuctionEndedEvent` to Kafka |

Both job IDs are stored on the `Auction` entity so they can be cancelled if the auction is cancelled before firing.

**Idempotency guard**: each job checks current auction status before acting. `StartAuctionJob` proceeds only if status is `SCHEDULED`; `EndAuctionJob` proceeds only if status is `ACTIVE`. No-op otherwise.

**Job scheduling** — triggered inside the create-auction service method:

```
save auction (status = SCHEDULED)
BackgroundJob.schedule(StartAuctionJob(auctionId), startTime)
BackgroundJob.schedule(EndAuctionJob(auctionId), endTime)
```

**Job cancellation** — if auction is cancelled before `startTime`, delete both scheduled jobs by stored job ID.

### wallet-service

| Job | Type | Parameter | Action |
|-----|------|-----------|--------|
| `ProcessAuctionPaymentJob` | Fire-and-forget (enqueued by Kafka consumer) | `auctionId: UUID`, `winnerId: UUID`, `amount: BigDecimal` | Charges winner's wallet, releases escrow, records transaction ledger entry |

Retry policy: **3 attempts** with exponential backoff.

### media-service

| Job | Type | Parameter | Action |
|-----|------|-----------|--------|
| `SendNotificationJob` | Fire-and-forget (enqueued by Kafka consumer) | `userId: UUID`, `type: NotificationType`, `payload: Map` | Persists and sends in-app notification |
| `SendEmailJob` | Fire-and-forget (enqueued by Kafka consumer) | `recipientId: UUID`, `templateType: String`, `variables: Map` | Renders template and sends email via SMTP |

Retry policy: **5 attempts** with exponential backoff for `SendEmailJob`; **3 attempts** for `SendNotificationJob`.

Existing inline email/notification logic inside Kafka consumers is moved into these job classes. Consumers become one-liners.

## End-to-End Flow

```
POST /auctions (auction-service)
  └─► save auction (status=SCHEDULED)
  └─► schedule StartAuctionJob at startTime
  └─► schedule EndAuctionJob at endTime

[at startTime] StartAuctionJob runs (auction-service worker)
  └─► auction status: SCHEDULED → ACTIVE
  └─► publish AuctionStartedEvent → Kafka

[at endTime] EndAuctionJob runs (auction-service worker)
  └─► auction status: ACTIVE → ENDED
  └─► determine winner (Feign → bidding-service)
  └─► publish AuctionEndedEvent → Kafka
        ├─► wallet-service Kafka consumer
        │     └─► enqueue ProcessAuctionPaymentJob
        │           └─► charge winner, release escrow, record transaction
        └─► media-service Kafka consumer
              └─► enqueue SendNotificationJob (winner + losers)
              └─► enqueue SendEmailJob (winner + losers)
```

## Database Migration

JobRunr auto-creates its tables on first startup (`skip-create: false` on `auction-service`). After the first run, the created schema is exported via `pg_dump --schema-only --table 'jobrunr_*'` and saved as a Liquibase SQL migration so the schema is tracked going forward.

Migration file location: `auction-service/src/main/resources/db/changelog/migrations/V_jobrunr_tables.sql`  
Registered in: `auction-service/src/main/resources/db/changelog/db.changelog-master.xml`

## Out of Scope

- JobRunr Pro features (batch jobs, priority queues)
- Recurring/cron jobs (not needed in this design)
- Centralised job service (rejected — breaks microservice boundaries)
- Per-service table prefixes (rejected — breaks unified dashboard)
