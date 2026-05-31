# JobRunr Integration — Implementation Plan

**Spec**: `docs/superpowers/specs/2026-05-22-jobrunr-integration-design.md`  
**Issue**: https://github.com/KhoaLy2003/bidnow/issues/70  
**Branch**: `feature/jobrunr-integration`

---

## Phase 0 — Demo setup in auction-service (team onboarding)

A minimal demo wired into `auction-service` so the team can observe JobRunr behaviour in the dashboard before the real jobs are built. Guarded by `@Profile("local")` so it never runs in production.

### Task 0.1 — Add JobRunr dependency + config (auction-service only)
- Add `jobrunr-spring-boot-3-starter` to `backend/auction-service/pom.xml`
- Add to `application.yml`:
  ```yaml
  org.jobrunr:
    background-job-server.enabled: true
    dashboard.enabled: true
    dashboard.port: 8083
    database.skip-create: false
  ```

### Task 0.2 — Create `DemoJob`
- File: `com.bidnow.auction.job.DemoJob`
- A `@Component` with one method `execute(String message)` that logs the message
- A second method `executeFailing(String message)` that throws a `RuntimeException` to demonstrate retry behaviour

### Task 0.3 — Create `JobRunrDemoController`
- File: `com.bidnow.auction.controller.JobRunrDemoController`
- Annotate with `@Profile("local")` — excluded from production
- Three endpoints:

| Method | Path | What it shows |
|--------|------|---------------|
| `POST` | `/demo/jobs/enqueue` | Fire-and-forget: job runs immediately |
| `POST` | `/demo/jobs/schedule?seconds=N` | Delayed: job fires after N seconds |
| `POST` | `/demo/jobs/failing` | Enqueues a job that throws → shows retries in dashboard |

### Task 0.4 — Run and verify
- Start `auction-service` with `--spring.profiles.active=local`
- Open `http://localhost:8083/dashboard`
- Call each demo endpoint and observe jobs appearing, running, retrying in the dashboard
- Once the team is comfortable, Phase 0 code can be removed or left as-is under `@Profile("local")`

---

## Phase 1 — Foundation (root pom + common module)

### Task 1.1 — Add JobRunr to root `pom.xml`
- Add `jobrunr-spring-boot-3-starter` version `7.3.1` to `<dependencyManagement>` in `backend/pom.xml`

### Task 1.2 — Add `AuctionStartedEvent` DTO to `common`
- Create `backend/common/src/main/java/com/bidnow/common/dto/event/AuctionStartedEvent.java`
- Fields: `auctionId: UUID`, `sellerId: UUID`, `title: String`, `startTime: LocalDateTime`

---

## Phase 2 — auction-service

### Task 2.1 — Add JobRunr dependency
- Add `jobrunr-spring-boot-3-starter` (no version) to `backend/auction-service/pom.xml`

### Task 2.2 — Configure JobRunr in `application.yml`
```yaml
org.jobrunr:
  background-job-server.enabled: true
  dashboard.enabled: true
  dashboard.port: 8083
  database.skip-create: false
```

### Task 2.3 — Create `StartAuctionJob`
- Package: `com.bidnow.auction.job`
- Constructor-inject `AuctionRepository` and `KafkaTemplate` (or Kafka producer)
- Idempotency: load auction by ID, return early if status != `SCHEDULED`
- On proceed: update status to `ACTIVE`, publish `AuctionStartedEvent` to Kafka

### Task 2.4 — Create `EndAuctionJob`
- Package: `com.bidnow.auction.job`
- Constructor-inject `AuctionRepository`, Feign client to `bidding-service`, `KafkaTemplate`
- Idempotency: load auction by ID, return early if status != `ACTIVE`
- On proceed: update status to `ENDED`, determine winner via Feign call, publish `AuctionEndedEvent`

### Task 2.5 — Update `Auction` entity
- Add fields `startJobId: UUID` and `endJobId: UUID` to store scheduled job IDs
- Add Liquibase migration to add these two columns to the auction table

### Task 2.6 — Schedule jobs on auction creation
- In the create-auction service method, after saving the entity:
  ```java
  JobId startJobId = BackgroundJob.schedule(startTime, () -> startAuctionJob.execute(auctionId));
  JobId endJobId   = BackgroundJob.schedule(endTime,   () -> endAuctionJob.execute(auctionId));
  auction.setStartJobId(startJobId.asUUID());
  auction.setEndJobId(endJobId.asUUID());
  auctionRepository.save(auction);
  ```

### Task 2.7 — Cancel jobs on auction cancellation
- In the cancel-auction service method, before persisting:
  ```java
  BackgroundJob.delete(auction.getStartJobId());
  BackgroundJob.delete(auction.getEndJobId());
  ```

### Task 2.8 — First-run: capture JobRunr schema as Liquibase migration
- Start `auction-service` once against the real shared DB (tables auto-created by JobRunr)
- Run: `pg_dump --schema-only --table 'jobrunr_*' -d <db> > V_jobrunr_tables.sql`
- Save output to `auction-service/src/main/resources/db/changelog/migrations/V_jobrunr_tables.sql`
- Register in `db.changelog-master.xml`
- Switch `skip-create: false` → `true` after migration is in place (JobRunr won't try to recreate)

> **Note**: `skip-create` should remain `false` for the very first run, then switch to `true` once the migration file exists and Liquibase manages the schema.

---

## Phase 3 — wallet-service

### Task 3.1 — Add JobRunr dependency
- Add `jobrunr-spring-boot-3-starter` to `backend/wallet-service/pom.xml`

### Task 3.2 — Configure JobRunr in `application.yml`
```yaml
org.jobrunr:
  background-job-server.enabled: true
  dashboard.enabled: false
  database.skip-create: true
```

### Task 3.3 — Create `ProcessAuctionPaymentJob`
- Package: `com.bidnow.wallet.job`
- Parameters: `auctionId: UUID`, `winnerId: UUID`, `amount: BigDecimal`
- Logic: charge winner's wallet, release escrow, record transaction ledger entry
- Retry policy: 3 attempts (configure via `@Job(retries = 3)`)

### Task 3.4 — Update `AuctionEndedEvent` Kafka consumer
- Existing consumer becomes a one-liner:
  ```java
  BackgroundJob.enqueue(() -> processAuctionPaymentJob.execute(event.getAuctionId(), event.getWinnerId(), event.getWinningBidAmount()));
  ```

---

## Phase 4 — media-service

### Task 4.1 — Add JobRunr dependency
- Add `jobrunr-spring-boot-3-starter` to `backend/media-service/pom.xml`

### Task 4.2 — Configure JobRunr in `application.yml`
```yaml
org.jobrunr:
  background-job-server.enabled: true
  dashboard.enabled: false
  database.skip-create: true
```

### Task 4.3 — Create `SendNotificationJob`
- Package: `com.bidnow.media.job`
- Parameters: `userId: UUID`, `type: NotificationType`, `payload: Map<String, Object>`
- Logic: extract existing notification-persist-and-send logic from current inline consumer code
- Retry policy: 3 attempts (`@Job(retries = 3)`)

### Task 4.4 — Create `SendEmailJob`
- Package: `com.bidnow.media.job`
- Parameters: `recipientId: UUID`, `templateType: String`, `variables: Map<String, Object>`
- Logic: extract existing email-send logic from current inline consumer code
- Retry policy: 5 attempts (`@Job(retries = 5)`)

### Task 4.5 — Refactor Kafka consumers to dispatch jobs
- Each existing Kafka consumer that processes notifications/emails becomes a dispatcher:
  ```java
  BackgroundJob.enqueue(() -> sendNotificationJob.execute(...));
  BackgroundJob.enqueue(() -> sendEmailJob.execute(...));
  ```

---

## Phase 5 — Verification

### Task 5.1 — Verify dashboard
- Start all services; open `http://localhost:8083/dashboard`
- Confirm jobs from all three services are visible

### Task 5.2 — Verify auction lifecycle
- Create an auction with a near-future `startTime` and `endTime`
- Confirm status transitions SCHEDULED → ACTIVE → ENDED at the correct times

### Task 5.3 — Verify job cancellation
- Create an auction, then cancel it before `startTime`
- Confirm no status transition fires

### Task 5.4 — Verify retry behaviour
- Simulate a failure in `SendEmailJob` (e.g. invalid SMTP config)
- Confirm job appears as FAILED in dashboard with retry count visible

---

## Execution Order

```
Phase 0 (demo) → Phase 1 (foundation) → Phase 2 (auction-service) → Phase 3 (wallet-service) → Phase 4 (media-service) → Phase 5 (verify)
```

- Phase 0 is standalone — only touches `auction-service`, can be done and validated before anything else.
- Phases 3 and 4 are independent of each other and can be done in parallel after Phase 2.
