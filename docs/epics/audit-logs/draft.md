# Epic 3: Comprehensive Audit Logging System

**Epic ID:** `EPIC-003`  
**Epic Title:** Comprehensive Audit Logging & Admin Dashboard  
**Priority:** P1 (High - Compliance & Observability)  
**Target Version:** MVP v1.0

---

## Epic Overview

This epic establishes a centralized, event-driven audit logging system that captures all critical business actions across the BidNow platform. The system will track **who** performed **what action**, **when**, and record **complete before/after snapshots** of entity state changes with full user context (IP, device, user-agent). Audit logs are **immutable**, **queryable**, and accessible via an admin dashboard for compliance, debugging, and forensic analysis.

---

## Business Value

- **For Compliance:** Provides tamper-proof audit trail for regulatory requirements and internal audits
- **For Operations:** Enables root cause analysis when investigating user complaints or system anomalies
- **For Security:** Tracks admin actions, suspicious activities, and policy violations
- **For Product:** Data-driven insights into user behavior patterns and system health
- **For Legal:** Evidence for dispute resolution (e.g., "who cancelled my auction?", "why was my bid rejected?")

---

## Success Metrics

- **Coverage:** 100% of critical entity state changes captured (User, Auction, Bid, Wallet)
- **Accuracy:** Zero audit events lost or corrupted (eventual consistency with retry)
- **Performance:** <50ms P99 latency added to business operations (async write)
- **Queryability:** Admin can find specific audit record within 5 seconds
- **Reliability:** 99.9% successful async audit writes within 10 seconds
- **Adoption:** Admins use audit logs for 100% of escalated support cases

---

## Epic Scope

### In Scope ✅

**Services Covered:**

- ✅ **Identity Service**: Registration, login, password changes, role changes, account status transitions
- ✅ **Auction Service**: Create, update, delete, status transitions (ACTIVE→COMPLETED), admin cancellations
- ✅ **Bidding Service**: Bids placed (manual/auto), bid retractions, anti-sniping extensions
- ✅ **Wallet Service**: Deposits, withdrawals, refunds, locks, forfeitures, platform fees

**Audit Log Features:**

- ✅ **Full entity snapshots** (before/after state) stored as JSONB
- ✅ **User context tracking**: actor ID, actor type (USER/ADMIN/SYSTEM), IP address, user-agent, device fingerprint
- ✅ **Correlation tracking**: Group multiple entity changes from a single user action under one `correlation_id`
- ✅ **PII masking**: Automatic redaction of sensitive fields (passwords, payment tokens, SSNs)
- ✅ **Immutability**: Append-only writes, no updates/deletes permitted
- ✅ **Async event-driven**: Emit events to message broker, write after transaction commit

**Admin Dashboard:**

- ✅ **Search & Filter**: By user, entity type, action, date range, correlation ID
- ✅ **Detail View**: Full before/after diff visualization with syntax highlighting
- ✅ **Timeline View**: Chronological event stream for a specific entity or user
- ✅ **Export**: Not in MVP (noted for future: CSV/PDF export for compliance reports)
- ✅ **Access Control**: RBAC - only users with `ROLE_ADMIN` can view audit logs

**Data Management:**

- ✅ **Partitioning**: PostgreSQL table partitioning by month for query performance
- ✅ **Retention**: Indefinite storage (archival strategy post-MVP)
- ✅ **Separate Storage**: Dedicated `audit-service` with its own database

### Out of Scope ❌

- ❌ Real-time alerting on suspicious actions (post-MVP)
- ❌ Compliance export formats (CSV/PDF) - manual SQL exports for MVP
- ❌ Machine learning-based anomaly detection
- ❌ Cryptographic signing of audit records (no regulatory requirement)
- ❌ Meta-auditing (logging access to audit logs themselves)
- ❌ Public API for audit logs (admin dashboard only)
- ❌ Media Service auditing (low business risk)

---

## Technical Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Action                              │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│  Service (Identity/Auction/Bidding/Wallet)                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 1. Execute Business Logic in Transaction                 │   │
│  │ 2. Persist Entity Changes to Service DB                  │   │
│  │ 3. COMMIT TRANSACTION                                     │   │
│  └──────────────────────┬───────────────────────────────────┘   │
│                         │                                        │
│  ┌──────────────────────▼───────────────────────────────────┐   │
│  │ 4. @TransactionalEventListener(phase=AFTER_COMMIT)       │   │
│  │    → Emit AuditLogEvent to Message Broker                │   │
│  └──────────────────────┬───────────────────────────────────┘   │
└─────────────────────────┼──────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│              Message Broker (RabbitMQ/Kafka)                    │
│              Topic: audit.events.{service}.{entity}              │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Audit Service                                │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 1. Consume AuditLogEvent from Broker                     │   │
│  │ 2. Apply PII Masking (passwords, tokens)                 │   │
│  │ 3. Enrich with Metadata (timestamp, service version)     │   │
│  │ 4. Write to audit_logs table (PostgreSQL)                │   │
│  │ 5. Retry on Failure (Dead Letter Queue after 3 attempts) │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│              Audit Database (PostgreSQL)                         │
│  - audit_logs (partitioned by month)                            │
│  - audit_correlation_groups                                     │
│  - Indexes: actor_id, entity_type, entity_id, created_at        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Model

### Primary Table: `audit_logs`

```sql
CREATE TABLE audit_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Event Correlation
    correlation_id      UUID NOT NULL,                    -- Groups related changes
    event_id            UUID NOT NULL UNIQUE,             -- Unique event identifier
    sequence_number     INTEGER NOT NULL,                 -- Order within correlation group

    -- What Changed
    service_name        VARCHAR(50) NOT NULL,             -- 'identity', 'auction', 'bidding', 'wallet'
    entity_type         VARCHAR(100) NOT NULL,            -- 'User', 'Auction', 'Bid', 'WalletTransaction'
    entity_id           UUID NOT NULL,                    -- ID of the affected entity
    action              VARCHAR(50) NOT NULL,             -- 'CREATE', 'UPDATE', 'DELETE', 'STATE_CHANGE'

    -- Who Performed the Action
    actor_id            UUID,                             -- User ID (NULL for system actions)
    actor_type          VARCHAR(20) NOT NULL,             -- 'USER', 'ADMIN', 'SYSTEM'
    actor_email         VARCHAR(255),                     -- For quick reference (masked if PII)

    -- Context
    ip_address          VARCHAR(45),                      -- IPv4/IPv6
    user_agent          TEXT,                             -- Browser/device info
    device_fingerprint  VARCHAR(255),                     -- Optional device ID
    request_id          UUID,                             -- Correlation with application logs

    -- State Snapshots (Full Entity JSON)
    before_snapshot     JSONB,                            -- Entity state before change (NULL for CREATE)
    after_snapshot      JSONB NOT NULL,                   -- Entity state after change
    changed_fields      TEXT[],                           -- Array of field names that changed

    -- Metadata
    reason              TEXT,                             -- User-provided or system reason (e.g., "Cancelled by admin: policy violation")
    metadata            JSONB,                            -- Additional context (e.g., {"auction_category": "electronics"})

    -- Timestamps
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Immutability Enforcement
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE    -- Soft delete flag (never actually deleted)

) PARTITION BY RANGE (created_at);

-- Monthly Partitions (auto-created via pg_cron or application)
CREATE TABLE audit_logs_2026_05 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

-- Indexes
CREATE INDEX idx_audit_logs_actor_id ON audit_logs(actor_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_audit_logs_correlation ON audit_logs(correlation_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_service_action ON audit_logs(service_name, action);

-- GIN index for JSONB queries
CREATE INDEX idx_audit_logs_after_snapshot ON audit_logs USING GIN(after_snapshot);
CREATE INDEX idx_audit_logs_before_snapshot ON audit_logs USING GIN(before_snapshot);
```

### Supporting Table: `audit_correlation_groups`

```sql
CREATE TABLE audit_correlation_groups (
    correlation_id      UUID PRIMARY KEY,

    -- Group Metadata
    initiating_service  VARCHAR(50) NOT NULL,
    initiating_action   VARCHAR(100) NOT NULL,            -- e.g., "Create Auction with Deposit Lock"
    total_events        INTEGER NOT NULL DEFAULT 1,

    -- Context
    actor_id            UUID,
    actor_type          VARCHAR(20) NOT NULL,

    -- Timestamps
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_correlation_actor ON audit_correlation_groups(actor_id);
CREATE INDEX idx_correlation_started ON audit_correlation_groups(started_at DESC);
```

---

## Event Schema

### `AuditLogEvent` (Published to Message Broker)

```json
{
  "eventId": "uuid",
  "correlationId": "uuid",
  "sequenceNumber": 1,

  "serviceName": "auction",
  "entityType": "Auction",
  "entityId": "uuid",
  "action": "CREATE",

  "actorId": "uuid",
  "actorType": "USER",
  "actorEmail": "seller@example.com",

  "ipAddress": "192.168.1.1",
  "userAgent": "Mozilla/5.0...",
  "deviceFingerprint": "fp_abc123",
  "requestId": "uuid",

  "beforeSnapshot": null,
  "afterSnapshot": {
    "id": "uuid",
    "title": "iPhone 15 Pro",
    "startingPrice": 500.0,
    "status": "ACTIVE",
    "sellerId": "uuid",
    "createdAt": "2026-05-16T10:00:00Z"
  },
  "changedFields": ["*"],

  "reason": "User created new auction listing",
  "metadata": {
    "categoryId": "uuid",
    "categoryName": "Electronics",
    "depositAmount": 50.0
  },

  "timestamp": "2026-05-16T10:00:01Z"
}
```

---

## Correlation Tracking Example

### Scenario: User Creates Auction (Single Action → Multiple Entity Changes)

```
User clicks "Create Auction" with deposit requirement

┌─────────────────────────────────────────────────────────────┐
│ correlation_id: 12345678-abcd-...                           │
├─────────────────────────────────────────────────────────────┤
│ Event 1 (sequence: 1)                                       │
│   service: auction                                          │
│   entity: Auction                                           │
│   action: CREATE                                            │
│   after_snapshot: { "id": "...", "status": "ACTIVE", ... }  │
├─────────────────────────────────────────────────────────────┤
│ Event 2 (sequence: 2)                                       │
│   service: wallet                                           │
│   entity: DepositLock                                       │
│   action: CREATE                                            │
│   after_snapshot: { "auctionId": "...", "amount": 50 }      │
├─────────────────────────────────────────────────────────────┤
│ Event 3 (sequence: 3)                                       │
│   service: auction                                          │
│   entity: AuctionStatusHistory                              │
│   action: CREATE                                            │
│   after_snapshot: { "auctionId": "...", "status": "ACTIVE" }│
└─────────────────────────────────────────────────────────────┘
```

**How it works:**

1. Service generates `correlation_id` at start of transaction
2. All events emitted within that transaction share the same `correlation_id`
3. Events are numbered sequentially (`sequence_number`)
4. Admin dashboard groups events by `correlation_id` for timeline view

---

## PII Masking Strategy

### Fields to Mask (Pre-write to Audit DB)

| Entity                | Field                 | Masking Strategy                                    |
| --------------------- | --------------------- | --------------------------------------------------- |
| **User**              | `passwordHash`        | Replace with `"***REDACTED***"`                     |
| **User**              | `otpCode`             | Replace with `"***REDACTED***"`                     |
| **User**              | `ssn` / `nationalId`  | Replace with `"***REDACTED***"` (if added post-MVP) |
| **WalletTransaction** | `paymentGatewayToken` | Replace with `"***REDACTED***"`                     |
| **WalletTransaction** | `bankAccountNumber`   | Keep last 4 digits: `"****1234"`                    |
| **User**              | `phoneNumber`         | Keep last 4 digits: `"****5678"`                    |

### Implementation

```java
@Service
public class PiiMaskingService {

    private static final Set<String> REDACTED_FIELDS = Set.of(
        "passwordHash", "otpCode", "ssn", "nationalId", "paymentGatewayToken"
    );

    private static final Map<String, BiFunction<String, String, String>> PARTIAL_MASK_FIELDS = Map.of(
        "bankAccountNumber", (field, value) -> maskPartial(value, 4),
        "phoneNumber", (field, value) -> maskPartial(value, 4)
    );

    public JsonNode maskPii(JsonNode snapshot) {
        ObjectNode masked = snapshot.deepCopy();

        // Full redaction
        REDACTED_FIELDS.forEach(field -> {
            if (masked.has(field)) {
                masked.put(field, "***REDACTED***");
            }
        });

        // Partial masking
        PARTIAL_MASK_FIELDS.forEach((field, maskFn) -> {
            if (masked.has(field)) {
                String original = masked.get(field).asText();
                masked.put(field, maskFn.apply(field, original));
            }
        });

        return masked;
    }

    private static String maskPartial(String value, int lastDigits) {
        if (value.length() <= lastDigits) return value;
        return "*".repeat(value.length() - lastDigits) + value.substring(value.length() - lastDigits);
    }
}
```

---

## Issues Breakdown

### **Issue 3.1: Audit Service Infrastructure Setup**

**Story ID:** `AUDIT-301`  
**Priority:** P0 - Critical

**Tasks:**

- [ ] Create `audit-service` module in monorepo
- [ ] Set up PostgreSQL database with `audit_logs` table schema
- [ ] Configure table partitioning by month (manual creation for MVP)
- [ ] Create `audit_correlation_groups` table
- [ ] Set up all indexes
- [ ] Configure RabbitMQ/Kafka topic: `audit.events.#`
- [ ] Implement PII masking service
- [ ] Create base entity models (`AuditLog`, `CorrelationGroup`)
- [ ] Implement repository layer with JPA
- [ ] Health check endpoint: `GET /actuator/health`

**Acceptance Criteria:**

- ✅ Service starts successfully and connects to database
- ✅ Partitions created for current month + next 3 months
- ✅ Message broker connection established
- ✅ PII masking unit tests pass (100% coverage)

---

### **Issue 3.2: Event Publishing from Identity Service**

**Story ID:** `AUDIT-302`  
**Priority:** P1 - High

**Tasks:**

- [ ] Create `AuditLogEvent` DTO with all required fields
- [ ] Implement `@TransactionalEventListener(AFTER_COMMIT)` for event emission
- [ ] Create reusable `AuditEventPublisher` component
- [ ] Integrate with User registration flow (CREATE event)
- [ ] Integrate with Login flow (LOGIN event)
- [ ] Integrate with Password change (UPDATE event)
- [ ] Integrate with Role change (ROLE_CHANGE event)
- [ ] Integrate with Account status transitions (STATE_CHANGE event)
- [ ] Capture user context (IP, user-agent) from HTTP request
- [ ] Generate `correlation_id` per request
- [ ] Unit tests for event publishing logic

**Example Flow:**

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditContextHolder auditContextHolder;

    @Transactional
    public User registerUser(UserRegistrationRequest request) {
        User user = // ... create user entity
        userRepository.save(user);

        // Business logic committed here
        return user;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        AuditLogEvent auditEvent = AuditLogEvent.builder()
            .correlationId(auditContextHolder.getCorrelationId())
            .serviceName("identity")
            .entityType("User")
            .entityId(event.getUser().getId())
            .action("CREATE")
            .actorId(event.getUser().getId())
            .actorType("USER")
            .beforeSnapshot(null)
            .afterSnapshot(toJsonNode(event.getUser()))
            .ipAddress(auditContextHolder.getIpAddress())
            .userAgent(auditContextHolder.getUserAgent())
            .build();

        eventPublisher.publishEvent(auditEvent);
    }
}
```

**Acceptance Criteria:**

- ✅ User registration emits `CREATE` event with full entity snapshot
- ✅ Password change emits `UPDATE` event with before/after snapshots (password masked)
- ✅ Events only emitted after transaction commit (no events on rollback)
- ✅ `correlation_id` consistent across related events in same request

---

### **Issue 3.3: Event Publishing from Auction Service**

**Story ID:** `AUDIT-303`  
**Priority:** P1 - High

**Tasks:**

- [ ] Integrate with Auction CREATE flow
- [ ] Integrate with Auction UPDATE flow (before start time only)
- [ ] Integrate with Auction DELETE flow (soft delete)
- [ ] Integrate with Auction status transitions (ACTIVE→COMPLETED, ACTIVE→CANCELLED)
- [ ] Integrate with Admin cancellation (capture `reason` field)
- [ ] Integrate with Anti-sniping extension (STATE_CHANGE event)
- [ ] Capture correlated events (Auction CREATE + StatusHistory CREATE)
- [ ] Unit tests for all flows

**Acceptance Criteria:**

- ✅ Auction creation emits 2 events (Auction CREATE + StatusHistory CREATE) with same `correlation_id`
- ✅ Admin cancellation includes `reason` in metadata
- ✅ Anti-sniping extension captures `previous_end_time` and `new_end_time` in snapshots

---

### **Issue 3.4: Event Publishing from Bidding Service**

**Story ID:** `AUDIT-304`  
**Priority:** P1 - High

**Tasks:**

- [ ] Integrate with Bid placement (manual bid)
- [ ] Integrate with Auto-bid trigger (SYSTEM actor)
- [ ] Integrate with Bid retraction (if supported)
- [ ] Capture full Bid entity snapshot
- [ ] Unit tests

**Acceptance Criteria:**

- ✅ Manual bid emits `CREATE` event with `actor_type=USER`
- ✅ Auto-bid emits `CREATE` event with `actor_type=SYSTEM`
- ✅ Snapshots include `bidAmount`, `auctionId`, `bidderId`

---

### **Issue 3.5: Event Publishing from Wallet Service**

**Story ID:** `AUDIT-305`  
**Priority:** P1 - High

**Tasks:**

- [ ] Integrate with Deposit flow
- [ ] Integrate with Withdrawal flow
- [ ] Integrate with Refund flow
- [ ] Integrate with Deposit lock (CREATE)
- [ ] Integrate with Deposit release (UPDATE)
- [ ] Integrate with Forfeit flow
- [ ] Integrate with Platform fee deduction
- [ ] Mask payment gateway tokens in snapshots
- [ ] Unit tests

**Acceptance Criteria:**

- ✅ Deposit emits `CREATE` event for `WalletTransaction` with masked payment token
- ✅ Refund emits `CREATE` event linked to original auction via `metadata`
- ✅ Forfeit includes `reason` in metadata

---

### **Issue 3.6: Audit Service Event Consumer**

**Story ID:** `AUDIT-306`  
**Priority:** P0 - Critical

**Tasks:**

- [ ] Implement RabbitMQ/Kafka listener for `audit.events.#` topic
- [ ] Deserialize `AuditLogEvent` from message
- [ ] Apply PII masking before persistence
- [ ] Enrich event with service metadata (service version, environment)
- [ ] Write to `audit_logs` table
- [ ] Update `audit_correlation_groups` table
- [ ] Implement retry logic (3 attempts with exponential backoff)
- [ ] Dead Letter Queue for failed events
- [ ] Metrics: events processed, failures, latency
- [ ] Integration tests with test message broker

**Acceptance Criteria:**

- ✅ Events consumed and persisted within 10 seconds (P99)
- ✅ PII fields masked before write
- ✅ Failed events sent to DLQ after 3 retries
- ✅ No duplicate audit records (idempotency via `event_id`)

---

### **Issue 3.7: Admin Dashboard - Search & Filter API**

**Story ID:** `AUDIT-307`  
**Priority:** P1 - High

**Tasks:**

- [ ] Create REST API endpoints:
  - `GET /api/v1/admin/audit-logs` - List with pagination
  - `GET /api/v1/admin/audit-logs/{id}` - Single record detail
  - `GET /api/v1/admin/audit-logs/entity/{entityType}/{entityId}` - Entity timeline
  - `GET /api/v1/admin/audit-logs/user/{userId}` - User activity timeline
  - `GET /api/v1/admin/audit-logs/correlation/{correlationId}` - Grouped events
- [ ] Implement filters:
  - Actor ID
  - Entity type
  - Entity ID
  - Action type
  - Service name
  - Date range (from/to)
  - Correlation ID
- [ ] Implement sorting (by `created_at` DESC/ASC)
- [ ] Pagination (default page size: 50, max: 200)
- [ ] Response DTO with before/after diff highlighted
- [ ] RBAC: Require `ROLE_ADMIN`
- [ ] OpenAPI documentation

**Example Request:**

```
GET /api/v1/admin/audit-logs?actorId=user123&entityType=Auction&dateFrom=2026-05-01&dateTo=2026-05-16&page=1&size=50&sort=created_at,desc
```

**Response:**

```json
{
  "data": [
    {
      "id": "uuid",
      "correlationId": "uuid",
      "serviceName": "auction",
      "entityType": "Auction",
      "entityId": "uuid",
      "action": "CREATE",
      "actorId": "uuid",
      "actorType": "USER",
      "actorEmail": "seller@example.com",
      "ipAddress": "192.168.1.1",
      "beforeSnapshot": null,
      "afterSnapshot": { "title": "iPhone 15 Pro", ... },
      "changedFields": ["*"],
      "reason": "User created auction",
      "createdAt": "2026-05-16T10:00:01Z"
    }
  ],
  "pagination": {
    "currentPage": 1,
    "pageSize": 50,
    "totalPages": 5,
    "totalElements": 234
  }
}
```

**Acceptance Criteria:**

- ✅ All filters work correctly
- ✅ Pagination handles large result sets (100K+ records)
- ✅ Response time <500ms for typical queries
- ✅ Non-admin users receive `403 Forbidden`

---

### **Issue 3.8: Admin Dashboard - Frontend UI**

**Story ID:** `AUDIT-308`  
**Priority:** P1 - High

**Tasks:**

- [ ] Create `/admin/audit-logs` page (Next.js)
- [ ] Search form with all filters
- [ ] Results table with columns:
  - Timestamp
  - Actor (email + ID)
  - Action
  - Entity Type
  - Entity ID
  - Service
  - Status (expand icon)
- [ ] Expandable row showing before/after diff with JSON syntax highlighting
- [ ] Timeline view for entity/user (chronological list)
- [ ] Correlation group view (grouped by `correlation_id`)
- [ ] Date range picker
- [ ] Export button (disabled for MVP, show "Coming Soon")
- [ ] Responsive design (desktop only for MVP)
- [ ] Loading states, error handling
- [ ] Pagination controls

**UI/UX:**

- Use `react-json-view` or `react-diff-viewer` for before/after comparison
- Color-code actions: CREATE=green, UPDATE=blue, DELETE=red, STATE_CHANGE=yellow
- Highlight changed fields in diff view

**Acceptance Criteria:**

- ✅ Admin can filter logs by any combination of criteria
- ✅ Diff view clearly shows field changes
- ✅ Timeline view shows events in chronological order
- ✅ Correlation view groups related events together

---

## Dependencies

### Upstream (Must Complete First)

- ✅ Message broker (RabbitMQ/Kafka) configured
- ✅ Identity Service authentication (JWT validation)
- ✅ RBAC implementation for admin role check

### Downstream (Will Consume Audit Events)

- ⚠️ Admin dashboard frontend (needs audit API endpoints)

---

## Risks & Mitigation

| Risk                                       | Probability | Impact   | Mitigation                                      |
| ------------------------------------------ | ----------- | -------- | ----------------------------------------------- |
| Event loss due to broker outage            | Low         | Critical | Use persistent queues, DLQ, retry logic         |
| Audit DB grows too large (>1TB)            | Medium      | High     | Partition by month, archival strategy post-MVP  |
| Performance degradation on source services | Low         | Medium   | Async event emission after commit, no blocking  |
| PII leakage in audit logs                  | Low         | Critical | Pre-write masking, automated tests, code review |
| Admin abuse (viewing sensitive data)       | Low         | Medium   | Meta-audit post-MVP, RBAC, access logs          |

---

## Testing Strategy

### Unit Tests (>80% coverage)

- PII masking logic
- Event serialization/deserialization
- Correlation ID generation
- Before/after snapshot capture

### Integration Tests

- Event publishing → consumption → persistence flow
- Transaction rollback (no events emitted)
- Idempotency (duplicate events ignored)
- DLQ handling

### Performance Tests

- 10K events/day load test
- Query performance with 1M records
- Partition switch efficiency

### Security Tests

- Non-admin access blocked (403)
- PII fields masked in responses
- SQL injection in filters

---

## Success Criteria Review

At the end of this epic:

- ✅ 100% of critical entity changes logged (User, Auction, Bid, Wallet)
- ✅ Full before/after snapshots captured with PII masking
- ✅ Correlation tracking links related events
- ✅ Admin dashboard functional with search/filter/timeline views
- ✅ <50ms latency added to business operations
- ✅ 99.9% successful audit writes within 10 seconds
- ✅ Zero data loss (events persisted via retry + DLQ)
- ✅ RBAC enforced (admin-only access)
- ✅ API documentation complete

---

## Out of Scope (Future Enhancements)

- Real-time alerting on suspicious actions
- Compliance export (CSV/PDF)
- Machine learning anomaly detection
- Cryptographic signing
- Meta-auditing
- Long-term archival to S3/Glacier
- Read-only replica for audit queries

---

## Notes

- **Immutability:** The `is_deleted` flag allows soft delete for compliance, but records are never physically deleted
- **Partitioning:** Create partitions manually for MVP; automate via `pg_cron` post-MVP
- **Async Guarantees:** Events emitted via `@TransactionalEventListener(AFTER_COMMIT)` ensure no audit records for failed transactions
- **Correlation ID Lifecycle:** Generated at API Gateway level, propagated via MDC, injected into events by each service

---

**Questions or feedback?** This is your audit logging blueprint. Review and let me know if any section needs deeper detail! 🚀
