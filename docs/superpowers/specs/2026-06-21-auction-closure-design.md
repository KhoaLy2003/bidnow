# Auction Closure Design

**Date:** 2026-06-21
**Branch:** fix/auction-closure
**Status:** Approved

## Problem

When the server goes offline and comes back up after an auction's `endTime` has passed, the auction remains stuck in `ACTIVE` status indefinitely. There is no mechanism to transition `ACTIVE` auctions to `COMPLETED` or `FAILED` when `endTime` is reached.

The existing activation pattern (`AuctionActivationJob` + `AuctionActivationService`) handles `SCHEDULED → ACTIVE` correctly via JobRunr's persistent job queue, but no equivalent exists for closure.

## Scope

- Add per-auction closure job scheduled at `endTime` when an auction becomes `ACTIVE`
- Add startup recovery to close overdue `ACTIVE` auctions and activate/close overdue `SCHEDULED` auctions on server restart
- Publish `AuctionEndedEvent` on closure (`loserIds = []` until bidding-service is built)
- Out of scope: end-time extension rescheduling (handled when bid placement is built)

## Components

### `AuctionClosureJob`

Thin JobRunr job class in `auction-service/job/`. Delegates entirely to `AuctionClosureService.close(auctionId)`.

```
@Job(name = "Close auction %0", retries = 3)
public void closeAuction(UUID auctionId)
```

Job ID is deterministic: `UUID.nameUUIDFromBytes("auction-closure:<auctionId>")` — prevents duplicate jobs.

### `AuctionClosureService`

Owns all closure logic. Transaction boundary is here.

**Logic:**
1. Load auction; if not found or `deletedAt` is set → warn and return
2. If `status != ACTIVE` → info log and return (idempotency guard)
3. Determine outcome:
   - `totalBids > 0` → `COMPLETED`: set `winnerId = currentWinnerId`, `completedAt = now()`
   - `totalBids == 0` → `FAILED`
4. Save auction
5. Record `AuctionStatusHistory` (ACTIVE → COMPLETED/FAILED, reason = "Auction end time reached")
6. Publish `AuctionEndedEvent` to `auction-ended-topic`

### `AuctionStartupRecoveryService`

Runs once on `ApplicationReadyEvent`. Each auction processed in its own transaction via `AuctionClosureService` / `AuctionActivationService` — a failure on one auction logs a warning and continues.

**Recovery cases:**

| Auction state | Condition | Action |
|---|---|---|
| `ACTIVE` | `endTime < now` | `AuctionClosureService.close()` immediately |
| `SCHEDULED` | `startTime < now`, `endTime > now` | `AuctionActivationService.activate()` + schedule closure job at `endTime` |
| `SCHEDULED` | `startTime < now`, `endTime < now` | `AuctionActivationService.activate()` + `AuctionClosureService.close()` immediately |

### Scheduling hook

Wherever an auction transitions to `ACTIVE`, schedule a closure job after the transaction commits (mirroring `scheduleActivationJob`):

- `AuctionServiceImpl.publishAuction()` — when `newStatus == ACTIVE`
- `AuctionServiceImpl.createAuction()` — when `status == ACTIVE`
- `AuctionActivationService.activate()` — after setting status to ACTIVE

### Kafka

New topic `auction-ended-topic` added to `AuctionKafkaProducer`. `AuctionEndedEvent` published with:
- `auctionId`, `auctionTitle`, `sellerId`
- `winnerId` (null if FAILED)
- `winningBidAmount` = `currentPrice` (null if FAILED)
- `loserIds = []` — populated by bidding-service when built

## Data Flow

### Normal path
```
publishAuction / createAuction
  → status = ACTIVE
  → scheduleClosureJob at endTime
  → JobRunr fires at endTime
  → AuctionClosureService.close()
  → status = COMPLETED or FAILED
  → AuctionEndedEvent published
```

### SCHEDULED path
```
publishAuction → status = SCHEDULED → scheduleActivationJob
  → JobRunr fires at startTime
  → AuctionActivationService.activate() → status = ACTIVE
  → scheduleClosureJob at endTime
  → (same as normal path)
```

### Downtime recovery path
```
ApplicationReadyEvent → AuctionStartupRecoveryService
  → ACTIVE, endTime < now → close immediately
  → SCHEDULED, startTime < now, endTime > now → activate + scheduleClosureJob
  → SCHEDULED, startTime < now, endTime < now → activate + close immediately
```

## Error Handling

- **JobRunr retries:** `@Job(retries = 3)`. Idempotency guard makes retries safe.
- **Startup recovery failures:** Per-auction transactions — one failure does not abort the recovery loop.
- **Kafka publish failure:** Auction DB status is committed before Kafka publish. A Kafka failure logs an error but does not roll back the closure. The status is authoritative; notification delivery is best-effort.
- **Missing closure job:** The startup recovery scan is the catch-all for auctions created before this fix.

## Testing

### `AuctionClosureServiceTest`
- ACTIVE + bids → COMPLETED, `winnerId` set, event published with correct fields
- ACTIVE + no bids → FAILED, `winnerId` null, event published
- Non-ACTIVE status → no-op (idempotency)
- Kafka publish called with `loserIds = []`

### `AuctionStartupRecoveryServiceTest`
- ACTIVE past `endTime` → `close()` called
- SCHEDULED past `startTime`, future `endTime` → `activate()` + closure job scheduled
- SCHEDULED past both times → `activate()` + `close()` called
- No overdue auctions → nothing called

### `AuctionClosureJobTest`
- Delegates to service
- `@Job(retries = 3)` annotation present
