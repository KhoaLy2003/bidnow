# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build all modules (must run from backend/ root)
mvn clean install

# Build a specific service without running tests
mvn clean install -pl common -DskipTests
mvn clean install -pl identity-service -DskipTests

# Run a specific service
mvn spring-boot:run -pl discovery-service
mvn spring-boot:run -pl identity-service

# Run a single test class
mvn test -pl identity-service -Dtest=AuthServiceImplTest

# Start Kafka via Docker (required before running services)
docker compose up -d
```

**Startup order**: `discovery-service` → `api-gateway` → any other service. All services register with Eureka on start.

## Required Environment Variables

Each service reads these from the environment (no `.env` file in repo):

| Variable | Used by |
|---|---|
| `JWT_SECRET` | api-gateway, identity-service |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | identity-service, user-service, media-service (and others with a DB) |
| `KAFKA_BOOTSTRAP_SERVERS` | all services (default: `localhost:9092`) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | media-service |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_REGION`, `AWS_BUCKET_NAME`, `AWS_ENDPOINT` | media-service |

## Architecture Overview

Spring Boot 3.2.4 + Spring Cloud 2023.0.0 multi-module Maven project. Services communicate via **Eureka service discovery** (load-balanced `lb://` URIs) for async and **OpenFeign** for synchronous calls. Async messaging uses **Kafka**.

### Request flow

```
Client → API Gateway (8080)
  → validates JWT, injects X-User-Id + X-User-Roles headers
  → routes to downstream service via Eureka
    → downstream service reads headers via RoleHeaderFilter (from common)
```

The gateway is the only service that touches the JWT. All downstream services trust the injected headers and use them to populate the Spring `SecurityContext` via `RoleHeaderFilter`.

### Service responsibilities

| Service | Port | Key responsibility |
|---|---|---|
| `discovery-service` | 8761 | Eureka server |
| `api-gateway` | 8080 | JWT validation, routing, CORS, aggregated Swagger UI |
| `identity-service` | 8081 | Auth (register/login/OTP/refresh), JWT issuance, user credentials |
| `user-service` | 8082 | User profiles and preferences |
| `auction-service` | 8083 | Auction lifecycle (creation, updates, closure) |
| `bidding-service` | 8084 | Bid placement and auto-bidding |
| `wallet-service` | 8085 | User wallets, escrow, transaction ledger |
| `media-service` | 8086 | Email notifications, S3 media uploads, audit log storage |

### `common` module

Shared library (`com.bidnow.common`) used by all services. Do not add service-specific logic here. Key contents:

- **`BaseEntity`** – JPA `@MappedSuperclass` with `createdAt`/`updatedAt` (managed via JPA lifecycle hooks). All entities extend this.
- **`BaseResponse<T>`** – wrapper for all API responses with `status`, `message`, `data`. Use `BaseResponse.success(data)`.
- **`PageResponse<T>` / `PaginationMeta`** – standard paginated response wrappers.
- **`GlobalExceptionHandler`** – maps all `BaseException` subclasses to HTTP responses. Add new exception types by extending `BaseException`.
- **`@AuthenticatedUserId`** – controller parameter annotation; resolved by `UserIdArgumentResolver` from the `X-User-Id` header.
- **`SpecificationBuilder<T>`** – fluent JPA `Specification` builder for dynamic queries. Use `withIfPresent`/`withLikeIfPresent` variants to skip null values automatically.
- **Kafka event DTOs** in `com.bidnow.common.dto.event` – all inter-service events go here.

### Audit system

The `@Audit` annotation (AOP-driven via `AuditAspect`) captures before/after entity state and publishes an `AuditLogEvent` to the `audit-events` Kafka topic, consumed by `media-service` which persists it to `AuditLog`.

Usage pattern in service methods:
```java
@Audit(action = AuditAction.UPDATE, entityType = "User", reason = "User updated profile")
public UserProfile updateProfile(...) {
    AuditContextHolder.setOldState(existingEntity);   // snapshot before
    // ... make changes ...
    AuditContextHolder.setNewState(updatedEntity);    // snapshot after
    return updatedEntity;
}
```
For events where delta is not needed (e.g., login events), use `captureDelta = false`.

### Database & migrations

- PostgreSQL with `ddl-auto: none` — schema is never auto-generated.
- Schema managed by **Liquibase** (`classpath:db/changelog/db.changelog-master.xml`).
- Migration files live under `src/main/resources/db/changelog/migrations/` per service.
- When adding a new column or table, create a new numbered SQL migration file and include it in the master changelog.

### OpenAPI / Swagger

Individual services expose only `/v3/api-docs` (Swagger UI disabled). The **api-gateway** aggregates all docs at `http://localhost:8080/swagger-ui.html`, pulling from each service's api-docs endpoint.

### Inter-service communication patterns

- **Synchronous (Feign)**: Used when a response is needed inline (e.g., identity-service calls user-service to create a profile after OTP verification). Feign clients are in a `feign/` package per service.
- **Asynchronous (Kafka)**: Used for decoupled events. Producers are in `kafka/` packages. Topic names are constants defined in the producer class.

### Security inside downstream services

Each service has its own `SecurityConfig` that installs `RoleHeaderFilter` (from `common`). Role-based access is enforced via `@PreAuthorize("hasRole('ADMIN')")` etc. Internal endpoints (called service-to-service) live under `/internal/` paths and are typically permit-all in `SecurityConfig` since they are not exposed through the gateway.
