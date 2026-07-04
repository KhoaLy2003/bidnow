# BDD Cucumber Testing — Auction Service

**Date:** 2026-07-04
**Status:** Approved

## Goal

Add Behavior-Driven Development (BDD) testing with Cucumber to `auction-service`, following the identical infrastructure pattern already established in `user-service` and `identity-service`. Coverage spans all three controllers: `AuctionController`, `AdminAuctionController`, and `AuctionCategoryController`, organized by business flow rather than by endpoint.

## Context

The project has a shared `bdd-support` module providing:
- Cucumber 7.18.0 (cucumber-java, cucumber-spring, cucumber-junit-platform-engine)
- Testcontainers for PostgreSQL and Kafka
- WireMock standalone for stubbing downstream Feign clients
- `BddRestClient` (RestAssured wrapper) and `ScenarioContext` (response holder)
- `BddSupportAutoConfiguration` (auto-registers all support beans)

`user-service` and `identity-service` already consume this module. `auction-service` introduces two additional runtime dependencies not present in those services: **Redis** (caching) and **JobRunr** (scheduled job persistence), which require specific handling.

## Infrastructure Changes

### bdd-support — Add Redis container support

No new pom dependency needed. Testcontainers has no dedicated `redis` module (unlike `postgresql`/`kafka`); instead use `GenericContainer` from the base `testcontainers` artifact, which is already a transitive dependency via the existing `postgresql` and `kafka` modules.

Create `RedisContainerSupport` (parallel to `PostgresContainerSupport` / `KafkaContainerSupport`):
- Uses `new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)`
- Spins up a Redis container on a dynamic mapped port
- Exposes `spring.data.redis.host` and `spring.data.redis.port` as static properties for `DynamicPropertySource` consumption

### auction-service/pom.xml — Add bdd-support dependency

```xml
<dependency>
    <groupId>com.bidnow</groupId>
    <artifactId>bdd-support</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

### application-bdd.yml (test resources)

Overrides for the `bdd` Spring profile:

```yaml
spring:
  cloud:
    discovery:
      enabled: false
  jpa:
    show-sql: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-bdd.xml
  kafka:
    consumer:
      auto-offset-reset: earliest
  cache:
    type: redis  # real Redis via Testcontainers

eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false

management:
  tracing:
    enabled: false

org:
  jobrunr:
    background-job-server:
      enabled: false    # suppress job execution during tests
    dashboard:
      enabled: false
```

### db.changelog-bdd.xml (test resources)

```xml
<!-- 1. Production migrations -->
<include file="db/changelog/db.changelog-master.xml"/>

<!-- 2. JobRunr tables (production uses skip-create: true, so must be explicit here) -->
<include file="db/changelog/migrations/init_jobrunr_tables.sql"/>

<!-- 3. BDD seed data -->
<include file="db/changelog/test-data.sql"/>
```

### test-data.sql

Seeds fixed-UUID auction items in each status so step definitions can reference them by constant. Uses `ON CONFLICT DO NOTHING` for idempotency; mutable state is reset per-scenario via `@Before` JDBC updates rather than re-insert.

**Seed records:**

| Constant | Status | Owner |
|---|---|---|
| `AUCTION_DRAFT_ID` | DRAFT | `SELLER_A_ID` |
| `AUCTION_SCHEDULED_ID` | SCHEDULED | `SELLER_A_ID` |
| `AUCTION_ACTIVE_ID` | ACTIVE (total_bids ≥ 1) | `SELLER_A_ID` |
| `AUCTION_CANCELLED_ID` | CANCELLED | `SELLER_A_ID` |

Auction categories come from the production `002-seed-auction-categories.sql` (included via the master changelog); no additional category seeding is needed.

## Test Class Structure

```
auction-service/src/test/java/com/bidnow/auction/
└── bdd/
    ├── CucumberTest.java              ← JUnit Suite runner
    │                                     glue: com.bidnow.auction.bdd
    │                                     features: classpath:features
    │                                     plugins: pretty, html report
    ├── config/
    │   └── CucumberSpringConfig.java  ← @CucumberContextConfiguration
    │                                     @SpringBootTest(RANDOM_PORT)
    │                                     @ActiveProfiles("bdd")
    │                                     DynamicPropertySource: Postgres + Kafka + Redis + WireMock
    └── steps/
        ├── CommonSteps.java           ← assertStatus, assertFieldEquals, assertFieldPresent
        ├── PublicAuctionSteps.java    ← browse/filter, getById, category-counts
        ├── SellerAuctionSteps.java    ← create, update, delete, publish, cancel, getMyAuctions
        │                                @Before resets seeded auction rows to baseline state
        ├── AdminAuctionSteps.java     ← listAuctions, getDetail, reject, cancel, force-close
        └── AuctionCategorySteps.java  ← getCategories
```

`CucumberSpringConfig` wires four containers and one WireMock stub:
- `PostgresContainerSupport.properties()` → datasource URL/username/password
- `KafkaContainerSupport.properties()` → bootstrap servers
- `RedisContainerSupport.properties()` → redis host/port
- `WireMockSupport.baseUrl()` → `spring.cloud.openfeign.client.config.user-service.url`

A WireMock stub returns a fixed `UserSummaryResponse` for any seller UUID, satisfying the Feign call made by `getAuctionById`.

## Feature Files & Scenarios

All files under `src/test/resources/features/`.

### browse-auctions.feature

Scenarios covering `GET /api/v1/auctions/public` and `GET /api/v1/auctions/public/{id}`:

- Public user browses with no filters → 200, paginated results
- Public user browses with category, price range, keyword filters → 200
- Public user browses with size exceeding cap → 400
- Public user gets auction by known ID → 200 with seller info (WireMocked)
- Public user gets auction by unknown ID → 404
- Public user gets category auction counts → 200 (exercises Redis cache path)

### seller-auction-lifecycle.feature

Scenarios covering `POST/PUT/DELETE /api/v1/auctions` and publish/cancel sub-routes:

- Seller creates a draft auction → 201
- Seller creates auction with invalid data (missing title, end_time in past) → 400
- Seller updates a DRAFT auction → 200
- Seller cannot update a SCHEDULED or ACTIVE auction → 400
- Seller deletes a DRAFT auction → 204
- Seller cannot delete a non-DRAFT auction → 400
- Seller publishes a DRAFT auction → 200, status transitions to SCHEDULED or ACTIVE
- Seller cannot publish auction with past end_time → 400
- Seller cancels their own auction → 204
- Non-owner seller cannot modify another seller's auction → 403
- Unauthenticated request to seller endpoint → 401
- Seller lists their own auctions → 200

### admin-moderation.feature

Scenarios covering `GET/POST /api/v1/admin/auctions`:

- Admin lists all auctions with filters → 200
- Admin gets full auction detail with status history → 200
- Admin rejects a SCHEDULED auction with reason → 200, status becomes REJECTED
- Admin cannot reject a non-SCHEDULED auction → 400
- Admin cancels an ACTIVE auction → 200, status becomes CANCELLED
- Admin force-closes an ACTIVE auction with bids → 200, status becomes CLOSED
- Non-admin user calling admin endpoint → 403

### auction-categories.feature

- Any user gets active categories → 200, returns seeded categories

## Test Data Reset Strategy

Each step class that mutates auction state includes a Cucumber `@Before` hook that resets the affected rows back to their seeded baseline via `JdbcTemplate`. This is the same pattern used in `user-service` (which resets `display_name` rather than deleting rows). The reset targets only the fixed-UUID seed auctions, leaving any auctions created during a scenario to be cleaned up implicitly when the Testcontainers Postgres instance is recycled at the end of the test run.

## Scope Boundaries

- **Included:** All endpoints on `AuctionController`, `AdminAuctionController`, `AuctionCategoryController` — happy path and key failure cases.
- **Excluded:** JobRunr job execution behavior (tested separately as unit/integration tests), Kafka event consumption, auction closure lifecycle triggered by scheduled jobs. These involve time-dependent async behavior not well-suited to synchronous BDD scenarios.
- **Existing unit tests are not removed** — the MockMvc-based `AuctionControllerTest` and Mockito-based service tests remain. BDD tests complement them at the HTTP integration level.
