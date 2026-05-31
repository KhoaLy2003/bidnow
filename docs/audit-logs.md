# Audit Logs - Team Documentation

## Overview

The audit logging system captures a complete trail of all significant actions across the BidNow platform. It uses an event-driven architecture with AOP-based declarative annotations for minimal boilerplate.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Service Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ @Audit       │  │ @Audit       │  │ @Audit               │  │
│  │ (login)      │  │ (update)     │  │ (createBid)          │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                 │                      │              │
│         ▼                 ▼                      ▼              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    AuditAspect                           │   │
│  │  - Captures old/new entity state                         │   │
│  │  - Computes delta via DiffUtils                          │   │
│  │  - Builds AuditLogEvent                                  │   │
│  │  - Publishes AuditApplicationEvent                       │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │           @TransactionalEventListener (AFTER_COMMIT)     │   │
│  │  - Listens to AuditApplicationEvent                      │   │
│  │  - Publishes to Kafka topic "audit-events"               │   │
│  └────────────────────────┬────────────────────────────────┘   │
└───────────────────────────┼────────────────────────────────────┘
                            │ Kafka
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      media-service                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditKafkaConsumer                                       │  │
│  │  - Consumes from "audit-events" topic                     │  │
│  │  - Saves to media_audit_logs table                        │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AdminAuditLogController (GET /api/v1/admin/audit-logs)   │  │
│  │  - Searchable by: actorEmail, action, date range          │  │
│  │  - Paginated results with actor email resolution          │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### Common Module (`backend/common/`)

| Component | Path | Purpose |
|-----------|------|---------|
| `@Audit` | `annotation/Audit.java` | Declarative annotation to mark auditable methods |
| `@AuditSnapshot` | `annotation/AuditSnapshot.java` | Marks method parameters for auto entity resolution |
| `AuditAspect` | `aop/AuditAspect.java` | AOP interceptor that captures audit data |
| `EntityResolver<T>` | `aop/EntityResolver.java` | Interface for resolving entities by ID |
| `AuditContextHolder` | `util/AuditContextHolder.java` | ThreadLocal storage for manual old/new state |
| `AuditApplicationEvent` | `dto/event/AuditApplicationEvent.java` | Generic Spring event wrapping AuditLogEvent |
| `DiffUtils` | `util/DiffUtils.java` | Computes field-level deltas, excludes `@Id` fields |
| `AuditContextUtils` | `util/AuditContextUtils.java` | Extracts actor info from SecurityContext |

### Audit Actions (`AuditAction` enum)

| Action | Usage |
|--------|-------|
| `CREATE` | New entity creation |
| `UPDATE` | Entity field modifications |
| `DELETE` | Entity deletion |
| `STATE_CHANGE` | Status transitions (verify OTP, resend OTP, refresh token) |
| `LOGIN` | User authentication |
| `LOGOUT` | User session termination |
| `ADMIN_ACTION` | Administrative operations |

## How to Add Audit Logging to a New Service

### Step 1: Create Entity Resolver

Create a resolver for each entity type you want to audit:

```java
@Component
@RequiredArgsConstructor
public class AuctionEntityResolver implements EntityResolver<Auction> {

    private final AuctionRepository auctionRepository;

    @Override
    public String entityType() {
        return "Auction";
    }

    @Override
    public Optional<Auction> resolve(String entityId) {
        return auctionRepository.findById(UUID.fromString(entityId));
    }
}
```

### Step 2: Create Audit Event Listener

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionAuditEventListener {

    private final AuctionKafkaProducer kafkaProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuditEvent(AuditApplicationEvent event) {
        log.info("Publishing audit event: {} for entity {}",
                event.getAuditLogEvent().getAction(),
                event.getAuditLogEvent().getEntityId());
        kafkaProducer.publishAuditLogEvent(event.getAuditLogEvent());
    }
}
```

### Step 3: Create Kafka Producer

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionKafkaProducer {

    private static final String AUDIT_EVENTS_TOPIC = "audit-events";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAuditLogEvent(AuditLogEvent event) {
        kafkaTemplate.send(AUDIT_EVENTS_TOPIC, event.getCorrelationId().toString(), event);
        log.info("Published AuditLogEvent: {} for entity {}", event.getAction(), event.getEntityId());
    }
}
```

### Step 4: Apply `@Audit` Annotation

#### Pattern A: Simple CREATE (returns entity or response with entity)

```java
@Audit(action = AuditAction.CREATE, entityType = "Auction", reason = "Auction created")
public AuctionResponse createAuction(CreateAuctionRequest request) {
    Auction auction = auctionRepository.save(...);
    AuditContextHolder.setNewState(auction);  // if return type is not the entity
    return mapper.toResponse(auction);
}
```

#### Pattern B: UPDATE with delta tracking

```java
@Audit(action = AuditAction.UPDATE, entityType = "Auction", reason = "Auction updated")
public AuctionResponse updateAuction(UUID id, UpdateAuctionRequest request) {
    Auction auction = auctionRepository.findById(id).orElseThrow(...);

    // Snapshot old state BEFORE modifications
    AuditContextHolder.setOldState(Auction.builder()
            .title(auction.getTitle())
            .startingPrice(auction.getStartingPrice())
            .endTime(auction.getEndTime())
            .build());

    // Apply changes
    auction.setTitle(request.getTitle());
    auction.setStartingPrice(request.getStartingPrice());
    auctionRepository.save(auction);

    // Set new state for delta comparison
    AuditContextHolder.setNewState(auction);

    return mapper.toResponse(auction);
}
```

#### Pattern C: Actions without delta (login, logout)

```java
@Audit(action = AuditAction.LOGIN, entityType = "User", reason = "User logged in", captureDelta = false)
public LoginResponse login(LoginRequest request) {
    // ... login logic
    AuditContextHolder.setNewState(user);
    return response;
}
```

#### Pattern D: Using EntityResolver for old state

```java
@Audit(action = AuditAction.UPDATE, entityType = "Auction")
public AuctionResponse updateAuction(
    @AuditSnapshot(entityType = "Auction") UUID auctionId,
    UpdateAuctionRequest request) {
    // Aspect auto-fetches old Auction via AuctionEntityResolver
    // ...
}
```

**Note:** Pattern D changes the method signature. If the method is part of an interface, use `AuditContextHolder` (Pattern B) instead.

## Delta Computation Rules

`DiffUtils.calculateDiff()` automatically excludes:
- Fields annotated with `@Id` (primary keys)
- Fields named `serialVersionUID`
- Fields named `userId` (foreign key references)

Only changed fields appear in the delta. Unchanged fields are omitted.

## Audit Log API

### Endpoint

```
GET /api/v1/admin/audit-logs
```

Requires `ROLE_ADMIN`.

### Query Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `actorEmail` | string | Filter by actor email (LIKE search) | `john@example.com` |
| `action` | AuditAction | Filter by action type | `LOGIN`, `CREATE`, `UPDATE` |
| `fromDate` | ISO datetime | Filter from date (inclusive) | `2024-01-01T00:00:00` |
| `toDate` | ISO datetime | Filter to date (inclusive) | `2024-12-31T23:59:59` |
| `page` | int | Page number (default: 0) | `0` |
| `size` | int | Page size (default: 10) | `20` |
| `sortBy` | string | Sort field (default: timestamp) | `timestamp` |
| `sortDir` | string | Sort direction (default: desc) | `desc` |

### Response

```json
{
  "status": "success",
  "data": {
    "data": [
      {
        "id": "uuid",
        "correlationId": "uuid",
        "entityType": "User",
        "entityId": "uuid",
        "action": "LOGIN",
        "actorId": "uuid",
        "actorType": "USER",
        "actorEmail": "user@example.com",
        "ipAddress": "192.168.1.1",
        "userAgent": "Mozilla/5.0...",
        "delta": null,
        "reason": "User logged in",
        "metadata": null,
        "timestamp": "2024-01-15T10:30:00"
      }
    ],
    "pagination": {
      "page": 0,
      "limit": 10,
      "total": 100,
      "totalPages": 10,
      "hasNext": true,
      "hasPrev": false
    }
  }
}
```

## Frontend

The audit logs admin page is at `/admin/audit-logs` with:
- **Actor Email** text input (LIKE search)
- **Action** dropdown (all AuditAction values)
- **Date Range** pickers (shadcn Calendar + Popover)
- Paginated table with action badges
- Detail dialog showing field-level delta via `DiffViewer`

## Best Practices

1. **Always set `AuditContextHolder.setNewState()`** when the method return type is a response DTO, not the entity itself
2. **Always set `AuditContextHolder.setOldState()` BEFORE modifying the entity** for UPDATE actions
3. **Use `captureDelta = false`** for actions where delta is meaningless (login, logout)
4. **Keep old state snapshots minimal** — only include fields relevant to the operation
5. **Audit failures are non-blocking** — the aspect catches exceptions and logs them, never breaking business logic
6. **Each service needs its own `EntityResolver` per entity type** and `AuditEventListener`
7. **Kafka topic is shared** — all services publish to `audit-events`, media-service consumes
