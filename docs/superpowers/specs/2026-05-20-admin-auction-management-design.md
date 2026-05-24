# Design Spec: Admin Auction Management (Issue #30)

**Date:** 2026-05-20
**Epic:** EPIC-001 — Auction Management & Lifecycle
**Story ID:** `AUCTION-130`
**Priority:** P0 — Critical
**Status:** Approved for implementation

---

## Overview

This issue delivers the admin moderation layer for the Auction Service. Admins can view all auctions across all sellers, reject scheduled auctions before they go live, cancel active auctions (with full deposit refunds), and force-close active auctions early (crowning the current highest bidder as winner).

---

## User Story

> As an admin, I can view, reject, cancel, and force-close auctions so that I can moderate the platform and protect buyers and sellers.

---

## Scope

### In Scope

- `GET /api/v1/admin/auctions` — browse all auctions with filters
- `GET /api/v1/admin/auctions/{id}` — full detail + audit history
- `POST /api/v1/admin/auctions/{id}/reject` — block a SCHEDULED auction
- `POST /api/v1/admin/auctions/{id}/cancel` — terminate an ACTIVE auction, trigger refunds
- `POST /api/v1/admin/auctions/{id}/force-close` — end an ACTIVE auction early, determine winner

### Out of Scope

- Seller re-submission of REJECTED auctions (Issue #29)
- SCHEDULED → ACTIVE cron activation (Issue #54)
- Admin editing of auction content
- Bulk admin actions

---

## Status Machine

### New Statuses

| Status | Description |
|--------|-------------|
| `SCHEDULED` | Added by issue #54. Auction created by seller, waiting for `start_time`. |
| `REJECTED` | Added by this issue. Admin blocked the auction before it went live. |

### Full Transition Table

| From | To | Triggered by |
|------|----|--------------|
| `DRAFT` | `SCHEDULED` | Seller submits (Issue #54) |
| `SCHEDULED` | `ACTIVE` | Cron job when `start_time` is reached (Issue #54) |
| `SCHEDULED` | `REJECTED` | **Admin rejects** ← this issue |
| `REJECTED` | `SCHEDULED` | Seller edits and re-submits (Issue #29) |
| `ACTIVE` | `COMPLETED` | Cron (normal close) OR **admin force-close** ← this issue |
| `ACTIVE` | `FAILED` | Cron when no bids at end time |
| `ACTIVE` | `CANCELLED` | **Admin cancel** ← this issue |

---

## API Design

All endpoints require `ROLE_ADMIN`. Base path: `/api/v1/admin/auctions`.

### 1. List All Auctions

```
GET /api/v1/admin/auctions
```

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `status` | String | null | Filter by status. Supports comma-separated multi-value: `SCHEDULED,ACTIVE` |
| `categoryId` | UUID | null | Filter by category |
| `sellerId` | UUID | null | Filter by specific seller |
| `q` | String | null | Search by title (case-insensitive, partial match) |
| `page` | int | 0 | Page index (0-based) |
| `size` | int | 20 | Items per page (max 100) |
| `sort` | String | `createdAt,desc` | Sort field + direction |

**Response:** `PageResponse<AdminAuctionSummaryResponse>`

```json
{
  "content": [
    {
      "id": "uuid",
      "title": "string",
      "status": "SCHEDULED",
      "sellerId": "uuid",
      "sellerName": "string",
      "category": { "id": "uuid", "name": "string" },
      "currentPrice": 500.00,
      "startTime": "ISO-8601",
      "endTime": "ISO-8601",
      "totalBids": 0,
      "createdAt": "ISO-8601"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 87,
  "totalPages": 5
}
```

> **Note:** `sellerName` requires a call to User Service (via Feign client or internal cache) to resolve the display name from `sellerId`. If User Service is unavailable, fall back to returning `sellerId` only.

---

### 2. Get Auction Detail

```
GET /api/v1/admin/auctions/{id}
```

**Response:** `AdminAuctionDetailResponse` — full auction fields plus the complete `auction_status_history` list.

```json
{
  "id": "uuid",
  "title": "string",
  "status": "REJECTED",
  "sellerId": "uuid",
  "sellerName": "string",
  "rejectionReason": "string",
  "rejectedBy": "uuid",
  "rejectedAt": "ISO-8601",
  "statusHistory": [
    {
      "fromStatus": "SCHEDULED",
      "toStatus": "REJECTED",
      "reason": "Prohibited item description",
      "triggeredBy": "uuid",
      "createdAt": "ISO-8601"
    }
  ]
}
```

---

### 3. Reject a SCHEDULED Auction

```
POST /api/v1/admin/auctions/{id}/reject
```

**Request Body:**
```json
{ "reason": "string (required, non-blank)" }
```

**Success:** `200 OK` with updated `AuctionResponse`

**Error cases:**
- Auction not in `SCHEDULED` → `400 Bad Request`: `"Auction must be in SCHEDULED status to be rejected"`
- `reason` is blank → `400 Bad Request`: `"Rejection reason is required"`
- Auction not found → `404 Not Found`
- Caller not admin → `403 Forbidden`

**Side effects:**
- Populates `rejection_reason`, `rejected_by`, `rejected_at` on `auction_items`
- Writes to `auction_status_history` (from: `SCHEDULED`, to: `REJECTED`, triggeredBy: admin userId, reason)
- Publishes `AUCTION_REJECTED` Kafka event

---

### 4. Cancel an ACTIVE Auction

```
POST /api/v1/admin/auctions/{id}/cancel
```

**Request Body:**
```json
{ "reason": "string (required, non-blank)" }
```

**Success:** `200 OK` with updated `AuctionResponse`

**Error cases:**
- Auction not in `ACTIVE` → `400 Bad Request`: `"Auction must be ACTIVE to be cancelled"`
- `reason` is blank → `400 Bad Request`: `"Cancellation reason is required"`
- Auction not found → `404 Not Found`
- Caller not admin → `403 Forbidden`

**Side effects:**
- Populates `cancellation_reason`, `cancelled_by`, `cancelled_at` on `auction_items`
- Writes to `auction_status_history`
- Publishes `AUCTION_CANCELLED` Kafka event

---

### 5. Force-Close an ACTIVE Auction

```
POST /api/v1/admin/auctions/{id}/force-close
```

**Request Body:**
```json
{ "reason": "string (optional)" }
```

**Success:** `200 OK` with updated `AuctionResponse`

**Error cases:**
- Auction not in `ACTIVE` → `400 Bad Request`: `"Auction must be ACTIVE to be force-closed"`
- Auction has zero bids → `400 Bad Request`: `"Cannot force-close an auction with no bids"`
- Auction not found → `404 Not Found`
- Caller not admin → `403 Forbidden`

**Side effects:**
- Sets `winner_id` = current highest bidder, `completed_at` = now
- Writes to `auction_status_history` (from: `ACTIVE`, to: `COMPLETED`)
- Publishes `AUCTION_ENDED` Kafka event with `triggeredBy: "ADMIN"`

---

## Schema Changes

### 1. `AuctionStatus` Enum

Add two new values:

```java
SCHEDULED,  // from issue #54
REJECTED    // from this issue
```

### 2. New Columns on `auction_items`

```sql
ALTER TABLE auction_items
    ADD COLUMN rejection_reason TEXT         NULL,
    ADD COLUMN rejected_by      UUID         NULL,
    ADD COLUMN rejected_at      TIMESTAMPTZ  NULL;
```

These mirror the existing `cancellation_reason / cancelled_by / cancelled_at` columns. No new indexes required.

---

## Event Publishing

All events produced by `AuctionKafkaProducer`.

### `AUCTION_REJECTED`

```json
{
  "auctionId": "uuid",
  "sellerId": "uuid",
  "title": "string",
  "rejectedBy": "uuid",
  "reason": "string",
  "rejectedAt": "ISO-8601"
}
```

**Consumer:** Notification Service → email + in-app alert to seller.

---

### `AUCTION_CANCELLED`

```json
{
  "auctionId": "uuid",
  "sellerId": "uuid",
  "title": "string",
  "cancelledBy": "uuid",
  "reason": "string",
  "cancelledAt": "ISO-8601"
}
```

**Consumers:**
- Wallet Service → refund all locked deposits for this auction
- Notification Service → notify seller + all registered bidders

---

### `AUCTION_ENDED` (force-close)

Reuses the existing event shape with a `closureSource` enum field to distinguish how the auction ended:

```json
{
  "auctionId": "uuid",
  "sellerId": "uuid",
  "winnerId": "uuid",
  "finalPrice": 850.00,
  "totalBids": 12,
  "endedAt": "ISO-8601",
  "closureSource": "ADMIN"
}
```

`closureSource` values: `SCHEDULER` (normal expiry), `ADMIN` (force-close), `BUY_NOW` (instant purchase). All three closure paths emit this same event shape. Consumers that don't care about the source can ignore the field.

**Consumers:** unchanged — payment window opens for winner, deposits refunded to losers.

---

## Acceptance Criteria

### Scenario 1 — Browse all auctions (default)
- Given I am an admin
- When I `GET /api/v1/admin/auctions` with no filters
- Then I receive all auctions (all statuses, all sellers) paginated, sorted by `createdAt DESC`

### Scenario 2 — Filter by status and seller
- When I apply `?status=SCHEDULED&sellerId=<uuid>`
- Then only auctions matching both filters are returned

### Scenario 3 — Reject a SCHEDULED auction
- Given auction status is `SCHEDULED`
- When I `POST /api/v1/admin/auctions/{id}/reject` with a non-blank reason
- Then status becomes `REJECTED`, rejection fields are populated
- And `AUCTION_REJECTED` event is published
- And `auction_status_history` records the transition with admin userId and reason
- If status is not `SCHEDULED` → `400`
- If reason is blank → `400`

### Scenario 4 — Cancel an ACTIVE auction
- Given auction status is `ACTIVE`
- When I `POST /api/v1/admin/auctions/{id}/cancel` with a non-blank reason
- Then status becomes `CANCELLED`, cancellation fields are populated
- And `AUCTION_CANCELLED` event is published
- And `auction_status_history` records the transition
- If status is not `ACTIVE` → `400`
- If reason is blank → `400`

### Scenario 5 — Force-close an ACTIVE auction with bids
- Given auction status is `ACTIVE` with at least one bid
- When I `POST /api/v1/admin/auctions/{id}/force-close`
- Then status becomes `COMPLETED`, `winner_id` and `completed_at` are set from current highest bidder
- And `AUCTION_ENDED` event is published with `triggeredBy: "ADMIN"`
- And `auction_status_history` records the transition
- If status is not `ACTIVE` → `400`
- If zero bids → `400` with message "Cannot force-close an auction with no bids"

### Scenario 6 — Get auction detail
- When I `GET /api/v1/admin/auctions/{id}`
- Then I receive full auction fields plus complete `auction_status_history` list

### Scenario 7 — Unauthorized access
- If caller does not have `ROLE_ADMIN` → `403 Forbidden` on all five endpoints

---

## Definition of Done

- [ ] `SCHEDULED` and `REJECTED` added to `AuctionStatus` enum
- [ ] DB migration adds `rejection_reason`, `rejected_by`, `rejected_at` columns
- [ ] `AdminAuctionController` implemented with all 5 endpoints
- [ ] `AdminAuctionService` with reject, cancel, force-close business logic
- [ ] `AdminAuctionSummaryResponse` and `AdminAuctionDetailResponse` DTOs
- [ ] `AdminAuctionReasonRequest` DTO with `@NotBlank` on reason
- [ ] `AUCTION_REJECTED`, `AUCTION_CANCELLED`, `AUCTION_ENDED` (with triggeredBy) events published
- [ ] All status-transition errors return `400` with descriptive message
- [ ] All admin endpoints return `403` for non-admin callers
- [ ] `auction_status_history` written for every admin action
- [ ] Unit tests: each action, each error case
- [ ] Integration tests: full request/response with test DB
- [ ] Swagger annotations on all endpoints
- [ ] Code reviewed and approved

---

## Dependencies

- `SCHEDULED` status (Issue #54) — enum value must exist before this ships
- `ROLE_ADMIN` security context available in JWT (Identity Service)
- Wallet Service consumer for `AUCTION_CANCELLED` event
- Notification Service consumers for all three events
