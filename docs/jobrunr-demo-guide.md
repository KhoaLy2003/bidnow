# JobRunr Demo Guide

A hands-on walkthrough for the team to understand how JobRunr works before it is wired into the real auction/wallet/media flows.

---

## What is JobRunr?

JobRunr is a background job processing library for Java. It lets you:

- **Enqueue** a job to run immediately in the background
- **Schedule** a job to run at a specific time in the future
- **Retry** failed jobs automatically with exponential backoff
- **Monitor** all jobs through a built-in dashboard UI

In BidNow, JobRunr will be used to:
- Transition auction status SCHEDULED → ACTIVE → ENDED at the right times
- Process payments after an auction ends
- Send emails and notifications reliably with retries

---

## Prerequisites

- PostgreSQL running and accessible (same DB used by all services)
- Environment variables set: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

---

## Start the Demo

```bash
# From the backend/ root
mvn spring-boot:run -pl auction-service
```

JobRunr auto-creates its tables in PostgreSQL on the first run (`jobrunr_jobs`, `jobrunr_recurring_jobs`, etc.).

---

## Open the Dashboard

```
http://localhost:8090
```

This is the JobRunr dashboard. Keep it open while you call the demo endpoints — you will see jobs appear and change state in real time.

---

## Demo Endpoints

Base URL: `http://localhost:8083/demo/jobs`

### 1. Fire-and-Forget Job

Enqueues a job that runs **immediately** in the background.

```bash
POST /demo/jobs/enqueue?message=Hello from JobRunr!
```

**What to observe in the dashboard:**
- Job appears briefly in **Enqueued** state
- Moves to **Processing**, then **Succeeded**
- Click the job to see the log output

---

### 2. Scheduled (Delayed) Job

Schedules a job to run **after N seconds**.

```bash
POST /demo/jobs/schedule?message=I am delayed&seconds=15
```

**What to observe in the dashboard:**
- Job appears in **Scheduled** state with a countdown
- After 15 seconds it moves to **Enqueued** → **Processing** → **Succeeded**

Try different values of `seconds` to see how delayed jobs work.

---

### 3. Failing Job (Retry Demo)

Enqueues a job that **always throws an exception**, triggering JobRunr's automatic retry mechanism.

```bash
POST /demo/jobs/failing?message=This will fail
```

**What to observe in the dashboard:**
- Job appears in **Enqueued** → **Processing** → **Failed**
- JobRunr retries it automatically (up to 3 times) with exponential backoff
- After all retries are exhausted the job moves to **Failed** permanently
- Click the job to see the full exception stack trace per attempt

---

## Key Things to Understand

| Concept | Where to see it |
|---------|----------------|
| Job states (Enqueued → Processing → Succeeded) | Dashboard > Jobs > Succeeded |
| Retry behaviour + stack traces | Dashboard > Jobs > Failed |
| Scheduled jobs with countdown | Dashboard > Jobs > Scheduled |
| Background worker heartbeat | Dashboard > Servers |

---

## How It Will Be Used in Production

Once the demo phase is complete, JobRunr will replace inline Kafka consumer processing:

```
Auction created
  └─► schedule StartAuctionJob at startTime   (SCHEDULED → ACTIVE)
  └─► schedule EndAuctionJob   at endTime     (ACTIVE → ENDED)

AuctionEndedEvent consumed by wallet-service
  └─► enqueue ProcessAuctionPaymentJob

AuctionEndedEvent consumed by media-service
  └─► enqueue SendNotificationJob
  └─► enqueue SendEmailJob
```

All of these jobs — from all three services — will appear in the **same dashboard** at `localhost:8090`.
