# Story 2 — BID-101: Bidding Service Bootstrap, Schema, Validation & Context Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the bare `bidding-service` into a running service with its own `bids` table, Redis and Feign wiring, gateway-header security, a unit-tested bid pre-validator, and a cache-aside reader of auction-service's bid context. It also gets a `POST /api/v1/bids` skeleton that pre-validates and returns `501 Not Implemented` until Story 3 adds placement.

**Architecture:**
- **Context cache:** `AuctionContextCacheService` reads `bidding:auction:{id}:context` from Redis as JSON. On a miss it calls auction-service's internal `GET /api/v1/internal/auctions/{id}/bid-context` through the Feign `AuctionServiceClient` and writes the result back with a TTL. Redis failures degrade to a cache miss. A Feign 404 becomes `AUCTION_NOT_FOUND`, and any other Feign failure becomes `503 SERVICE_UNAVAILABLE`.
- **Pre-validation:** `BidValidationService.preValidate` applies the same rules as auction-service's authoritative `applyBid`, in the same order, as a fast pre-filter.
- **Controller:** `BidController` wires the two together.
- **BDD harness:** a Cucumber setup (Testcontainers Postgres and Redis, WireMock standing in for auction-service) proves the schema, security, caching and error mapping end to end. Stories 3–5 reuse it.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.0 (OpenFeign, Eureka), Spring Data JPA, Spring Data Redis (`StringRedisTemplate`), Liquibase formatted SQL, PostgreSQL, Lombok, JUnit 5, Mockito, AssertJ, standalone MockMvc, Cucumber + Testcontainers + WireMock (`bdd-support`).

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (§3 steps 1–2, §4 bidding-service contract and error codes, §5 data). Roadmap: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (Story 2). Upstream contract: auction-service `BidContextResponse` from Story 1 (`backend/auction-service/src/main/java/com/bidnow/auction/dto/response/BidContextResponse.java`).

## Global Constraints

- **Scope.** bidding-service plus: the repo-root `docker-compose.yml` (bidding-service env only), `docs/database/bidding-service-schema.md` (new), spec §5, and the roadmap. Do not modify auction-service, wallet-service, the gateway or `common`.
- **The public API path is `/api/v1/bids`.** It is already routed by the gateway (`Path=/api/v1/bids/**`), so no gateway change is needed.
- **`bidderId` always comes from `@AuthenticatedUserId UUID` (the `X-User-Id` header), never from the body.** `PlaceBidRequest` contains only `auctionId` and `amount`.
- **Pre-validation rules, in this order:**
  1. `status != "ACTIVE"` or `now >= endTime` → `409 AUCTION_NOT_OPEN`
  2. `bidderId == sellerId` → `403 BID_OWN_AUCTION`
  3. `amount < minimumBid` → `400 BID_TOO_LOW` with `errors: { minimumBid }`
  - `minimumBid` is `currentPrice` when `totalBids == 0`, otherwise `currentPrice + bidIncrement`.
- **Other error codes:** unknown auction → `404 AUCTION_NOT_FOUND`. auction-service unreachable, 5xx or timeout → `503 SERVICE_UNAVAILABLE`. Bean-validation failures keep the common `INVALID_INPUT` 400.
- **Redis context key:** exactly `bidding:auction:{auctionId}:context`, value = JSON of `BidContext`, TTL from `bidding.cache.context-ttl-seconds` (default `600`). A Redis read, write or delete failure must never fail a request.
- **Feign timeouts:** `connect-timeout: 1000`, `read-timeout: 2000` (ms), as the default for all clients.
- **Types:**
  - Money is `BigDecimal`, and money comparisons use `compareTo`.
  - Time comes from an injected `java.time.Clock` bean.
  - `bids` timestamps follow the project's `BaseEntity`: `LocalDateTime` ↔ `TIMESTAMP`.
- **Responses:** `ResponseEntity<BaseResponse<T>>` for data. Errors are common `ErrorResponse`.
- **`POST /api/v1/bids` returns `501 Not Implemented` with an empty body after successful pre-validation.** Story 3 replaces this with `201` and a body.
- **Do not add Kafka configuration** (Story 3). `spring-kafka` already comes from `common` and needs no config while nothing produces or consumes.
- **Do not run `git commit` or `git add`.** Leave all changes uncommitted on branch `feature/bidding`. The user commits.
- **Maven** runs from `backend/`:
  - Unit tests: `mvn -q -pl bidding-service -am test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`.
  - Full: `mvn -q -pl bidding-service -am test`.
  - BDD (needs Docker): `mvn -q -pl bidding-service -am test -Pbdd`.

---

## File Map

All paths are relative to `backend/bidding-service/` unless noted.

| File | Action | Responsibility |
|---|---|---|
| `pom.xml` | Modify | openfeign, data-redis, bdd-support (test) |
| `src/main/resources/application.yml` | Modify | Redis, Feign timeouts, cache TTL |
| `src/main/java/com/bidnow/bidding/BiddingApplication.java` | Modify | `@EnableFeignClients` |
| `src/main/resources/db/changelog/db.changelog-master.xml` | Create | Liquibase master |
| `src/main/resources/db/changelog/migrations/001-init-bids.sql` | Create | `bids` table + indexes |
| `src/main/java/com/bidnow/bidding/domain/entity/Bid.java` | Create | JPA entity (app-assigned UUID) |
| `src/main/java/com/bidnow/bidding/repository/BidRepository.java` | Create | Spring Data repository |
| `src/main/java/com/bidnow/bidding/config/ClockConfig.java` | Create | `Clock` bean |
| `src/main/java/com/bidnow/bidding/config/SecurityConfig.java` | Create | `RoleHeaderFilter`, 401 entry point |
| `src/main/java/com/bidnow/bidding/constant/BiddingErrorCodes.java` | Create | Error-code constants |
| `src/main/java/com/bidnow/bidding/exception/ConflictException.java` | Create | 409 |
| `src/main/java/com/bidnow/bidding/exception/ServiceUnavailableException.java` | Create | 503 |
| `src/main/java/com/bidnow/bidding/exception/BidTooLowException.java` | Create | 400 carrying `minimumBid` |
| `src/main/java/com/bidnow/bidding/exception/BiddingExceptionHandler.java` | Create | `BidTooLowException` → `errors.minimumBid` |
| `src/main/java/com/bidnow/bidding/dto/BidContext.java` | Create | Feign payload + cached value |
| `src/main/java/com/bidnow/bidding/dto/request/PlaceBidRequest.java` | Create | POST body |
| `src/main/java/com/bidnow/bidding/feign/AuctionServiceClient.java` | Create | `getBidContext` |
| `src/main/java/com/bidnow/bidding/service/BidValidationService.java` | Create | Pre-validation rules |
| `src/main/java/com/bidnow/bidding/service/AuctionContextCacheService.java` | Create | Cache-aside context read |
| `src/main/java/com/bidnow/bidding/controller/BidController.java` | Create | `POST /api/v1/bids` skeleton |
| `src/test/java/com/bidnow/bidding/exception/BiddingExceptionHandlerTest.java` | Create | Handler test |
| `src/test/java/com/bidnow/bidding/service/BidValidationServiceTest.java` | Create | Rule/boundary tests |
| `src/test/java/com/bidnow/bidding/service/AuctionContextCacheServiceTest.java` | Create | Cache/Feign tests |
| `src/test/java/com/bidnow/bidding/controller/BidControllerTest.java` | Create | HTTP tests |
| `src/test/resources/application-bdd.yml` | Create | BDD profile |
| `src/test/java/com/bidnow/bidding/bdd/CucumberTest.java` | Create | Suite runner |
| `src/test/java/com/bidnow/bidding/bdd/config/CucumberSpringConfig.java` | Create | Containers + WireMock wiring |
| `src/test/java/com/bidnow/bidding/bdd/steps/CommonSteps.java` | Create | Shared assertions |
| `src/test/java/com/bidnow/bidding/bdd/steps/BidPlacementSteps.java` | Create | Bid steps + WireMock stubs |
| `src/test/resources/features/bidding-core.feature` | Create | Scenarios |
| `docker-compose.yml` (repo root) | Modify | `REDIS_HOST` for bidding-service |
| `docs/database/bidding-service-schema.md` (repo root) | Create | Schema doc |
| `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (repo root) | Modify | §5 timestamp type |
| `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (repo root) | Modify | Tick Story 2 |

---

### Task 1: Bootstrap — dependencies, config, schema, entity, repository

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/bidnow/bidding/BiddingApplication.java`
- Create: `src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `src/main/resources/db/changelog/migrations/001-init-bids.sql`
- Create: `src/main/java/com/bidnow/bidding/domain/entity/Bid.java`
- Create: `src/main/java/com/bidnow/bidding/repository/BidRepository.java`
- Create: `src/main/java/com/bidnow/bidding/config/ClockConfig.java`
- Modify: `docker-compose.yml` (repo root)

**Interfaces:**
- Produces:
  - `Bid` (Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, extends `BaseEntity`, implements `Persistable<UUID>`), with fields `UUID id` (app-assigned), `UUID auctionId`, `UUID bidderId`, `BigDecimal amount`, `boolean autoBid`, and `boolean antiSnipingTriggered`
  - `BidRepository extends JpaRepository<Bid, UUID>`
  - a `Clock` bean (`Clock.systemUTC()`)
  - the property `bidding.cache.context-ttl-seconds`

This task has no unit test. The schema, context load and Liquibase are verified by the BDD in Task 5. The check here is that the module compiles and the empty test run passes.

- [ ] **Step 1: Add dependencies**

In `pom.xml`, inside `<dependencies>` after the `common` dependency, add:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bidnow</groupId>
            <artifactId>bdd-support</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Extend `application.yml`**

Under `spring:` (the same level as `datasource:`), add:

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 1000
            read-timeout: 2000
```

At the end of the file, add:

```yaml
bidding:
  cache:
    context-ttl-seconds: 600
```

- [ ] **Step 3: Enable Feign clients**

Replace `BiddingApplication.java` with:

```java
/*
 * BidNow Auction System
 */
package com.bidnow.bidding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication(scanBasePackages = {"com.bidnow.bidding", "com.bidnow.common"})
public class BiddingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BiddingApplication.class, args);
    }
}
```

- [ ] **Step 4: Liquibase changelog and migration**

`src/main/resources/db/changelog/db.changelog-master.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <include file="db/changelog/migrations/001-init-bids.sql"/>

</databaseChangeLog>
```

`src/main/resources/db/changelog/migrations/001-init-bids.sql`:

```sql
-- liquibase formatted sql

-- changeset bidnow:bidding_001
-- comment: Bid ledger for bidding-service (Epic #120, BID-101)
CREATE TABLE bids
(
    id                        UUID PRIMARY KEY,
    auction_id                UUID           NOT NULL,
    bidder_id                 UUID           NOT NULL,
    amount                    DECIMAL(15, 2) NOT NULL,
    is_auto_bid               BOOLEAN        NOT NULL DEFAULT FALSE,
    is_anti_sniping_triggered BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bids_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_bids_auction_created ON bids (auction_id, created_at DESC);
CREATE INDEX idx_bids_auction_amount ON bids (auction_id, amount DESC);
CREATE INDEX idx_bids_bidder_auction_created ON bids (bidder_id, auction_id, created_at DESC);
```

`id` has no database default. It is always assigned by the application, because Story 3 generates the `bidId` before calling auction-service.

- [ ] **Step 5: Entity and repository**

`src/main/java/com/bidnow/bidding/domain/entity/Bid.java`:

```java
package com.bidnow.bidding.domain.entity;

import com.bidnow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One accepted bid. The id is assigned by bidding-service before the auction-service call (it is the
 * {@code bidId} auction-service stores as {@code last_bid_id}), so the entity implements
 * {@link Persistable} to make {@code save} INSERT directly instead of SELECT-then-merge.
 */
@Entity
@Table(name = "bids")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bid extends BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private UUID bidderId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_auto_bid", nullable = false)
    private boolean autoBid;

    @Column(name = "is_anti_sniping_triggered", nullable = false)
    private boolean antiSnipingTriggered;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
```

`src/main/java/com/bidnow/bidding/repository/BidRepository.java`:

```java
package com.bidnow.bidding.repository;

import com.bidnow.bidding.domain.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {
}
```

- [ ] **Step 6: Clock bean**

`src/main/java/com/bidnow/bidding/config/ClockConfig.java`:

```java
package com.bidnow.bidding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 7: Fix the Redis host env var in docker-compose**

In the repo-root `docker-compose.yml`, in the `bidding-service` service's `environment` list, replace:

```yaml
      - SPRING_REDIS_HOST=redis
```

with:

```yaml
      - REDIS_HOST=redis
```

Spring Boot 3 ignores `spring.redis.*`. `application.yml` now reads `${REDIS_HOST}`.

- [ ] **Step 8: Compile**

Run: `mvn -q -pl bidding-service -am test`
Expected: BUILD SUCCESS. There are no tests yet, and the build must compile.

---

### Task 2: Errors, `BidContext` and `BidValidationService`

**Files:**
- Create: `src/main/java/com/bidnow/bidding/constant/BiddingErrorCodes.java`
- Create: `src/main/java/com/bidnow/bidding/exception/ConflictException.java`
- Create: `src/main/java/com/bidnow/bidding/exception/ServiceUnavailableException.java`
- Create: `src/main/java/com/bidnow/bidding/exception/BidTooLowException.java`
- Create: `src/main/java/com/bidnow/bidding/exception/BiddingExceptionHandler.java`
- Create: `src/main/java/com/bidnow/bidding/dto/BidContext.java`
- Create: `src/main/java/com/bidnow/bidding/service/BidValidationService.java`
- Test: `src/test/java/com/bidnow/bidding/exception/BiddingExceptionHandlerTest.java`
- Test: `src/test/java/com/bidnow/bidding/service/BidValidationServiceTest.java`

**Interfaces:**
- Produces:
  - `BiddingErrorCodes.{AUCTION_NOT_FOUND, AUCTION_NOT_OPEN, BID_TOO_LOW, BID_OWN_AUCTION, SERVICE_UNAVAILABLE}` (String)
  - `new ConflictException(String message, String errorCode)` → 409
  - `new ServiceUnavailableException(String message)` → 503, error code `SERVICE_UNAVAILABLE`
  - `new BidTooLowException(BigDecimal minimumBid)` with `getMinimumBid()` → 400, error code `BID_TOO_LOW`
  - `BidContext` (`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)`), with fields `UUID auctionId, String title, UUID sellerId, String status, BigDecimal currentPrice, BigDecimal bidIncrement, BigDecimal depositAmount, UUID currentWinnerId, int totalBids, OffsetDateTime endTime`. The field names match auction-service's `BidContextResponse` JSON exactly.
  - `BidValidationService.preValidate(BidContext ctx, UUID bidderId, BigDecimal amount, Instant now): void` (throws on rejection)
  - `static BigDecimal BidValidationService.minimumBid(BidContext ctx)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/bidnow/bidding/exception/BiddingExceptionHandlerTest.java`:

```java
package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BiddingExceptionHandlerTest {

    private final BiddingExceptionHandler handler = new BiddingExceptionHandler();

    @Test
    void handleBidTooLow_returns400WithMinimumBid() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bids");

        ResponseEntity<ErrorResponse> response =
                handler.handleBidTooLow(new BidTooLowException(new BigDecimal("105.00")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(BiddingErrorCodes.BID_TOO_LOW);
        assertThat(response.getBody().getErrors()).containsEntry("minimumBid", "105.00");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/bids");
    }

    @Test
    void exceptionStatuses() {
        assertThat(new ConflictException("closed", BiddingErrorCodes.AUCTION_NOT_OPEN).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        ServiceUnavailableException unavailable = new ServiceUnavailableException("down");
        assertThat(unavailable.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getErrorCode()).isEqualTo(BiddingErrorCodes.SERVICE_UNAVAILABLE);
    }
}
```

`src/test/java/com/bidnow/bidding/service/BidValidationServiceTest.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BidValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final BidValidationService service = new BidValidationService();

    private BidContext context(int totalBids, String currentPrice) {
        return BidContext.builder()
                .auctionId(UUID.randomUUID())
                .title("Vintage Watch")
                .sellerId(SELLER_ID)
                .status("ACTIVE")
                .currentPrice(new BigDecimal(currentPrice))
                .bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00"))
                .totalBids(totalBids)
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }

    @Test
    void validFirstBidAtCurrentPrice_passes() {
        assertThatCode(() -> service.preValidate(context(0, "100.00"), BIDDER_ID, new BigDecimal("100.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void firstBidBelowCurrentPrice_throwsBidTooLow() {
        assertThatThrownBy(() -> service.preValidate(context(0, "100.00"), BIDDER_ID, new BigDecimal("99.99"), NOW))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("100.00"));
    }

    @Test
    void subsequentBidBelowIncrement_throwsBidTooLow() {
        assertThatThrownBy(() -> service.preValidate(context(1, "100.00"), BIDDER_ID, new BigDecimal("104.00"), NOW))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("105.00"));
    }

    @Test
    void subsequentBidAtExactMinimum_passes() {
        assertThatCode(() -> service.preValidate(context(1, "100.00"), BIDDER_ID, new BigDecimal("105.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void sellerBidding_throwsForbidden() {
        assertThatThrownBy(() -> service.preValidate(context(0, "100.00"), SELLER_ID, new BigDecimal("500.00"), NOW))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BID_OWN_AUCTION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SCHEDULED", "COMPLETED", "FAILED", "CANCELLED", "REJECTED"})
    void notActive_throwsConflict(String status) {
        BidContext ctx = context(0, "100.00");
        ctx.setStatus(status);

        assertThatThrownBy(() -> service.preValidate(ctx, BIDDER_ID, new BigDecimal("100.00"), NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void exactlyAtEndTime_throwsConflict() {
        BidContext ctx = context(0, "100.00");
        ctx.setEndTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.preValidate(ctx, BIDDER_ID, new BigDecimal("100.00"), NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void oneMillisecondBeforeEndTime_passes() {
        BidContext ctx = context(0, "100.00");
        ctx.setEndTime(OffsetDateTime.ofInstant(NOW.plusMillis(1), ZoneOffset.UTC));

        assertThatCode(() -> service.preValidate(ctx, BIDDER_ID, new BigDecimal("100.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void closedAuctionIsReportedBeforeSellerAndAmountChecks() {
        BidContext ctx = context(1, "100.00");
        ctx.setStatus("COMPLETED");

        assertThatThrownBy(() -> service.preValidate(ctx, SELLER_ID, new BigDecimal("1.00"), NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sellerIsReportedBeforeAmountCheck() {
        assertThatThrownBy(() -> service.preValidate(context(1, "100.00"), SELLER_ID, new BigDecimal("1.00"), NOW))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void minimumBid_usesCurrentPriceForFirstBidAndAddsIncrementAfter() {
        assertThat(BidValidationService.minimumBid(context(0, "100.00"))).isEqualByComparingTo("100.00");
        assertThat(BidValidationService.minimumBid(context(3, "100.00"))).isEqualByComparingTo("105.00");
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl bidding-service -am test -Dtest='BiddingExceptionHandlerTest,BidValidationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (the classes don't exist yet).

- [ ] **Step 3: Implement the error types and handler**

`src/main/java/com/bidnow/bidding/constant/BiddingErrorCodes.java`:

```java
package com.bidnow.bidding.constant;

public final class BiddingErrorCodes {
    public static final String AUCTION_NOT_FOUND = "AUCTION_NOT_FOUND";
    public static final String AUCTION_NOT_OPEN = "AUCTION_NOT_OPEN";
    public static final String BID_TOO_LOW = "BID_TOO_LOW";
    public static final String BID_OWN_AUCTION = "BID_OWN_AUCTION";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private BiddingErrorCodes() {
    }
}
```

`src/main/java/com/bidnow/bidding/exception/ConflictException.java`:

```java
package com.bidnow.bidding.exception;

import com.bidnow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {
    public ConflictException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.CONFLICT);
    }
}
```

`src/main/java/com/bidnow/bidding/exception/ServiceUnavailableException.java`:

```java
package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseException {
    public ServiceUnavailableException(String message) {
        super(message, BiddingErrorCodes.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
```

`src/main/java/com/bidnow/bidding/exception/BidTooLowException.java`:

```java
package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.exception.BaseException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

@Getter
public class BidTooLowException extends BaseException {

    private final BigDecimal minimumBid;

    public BidTooLowException(BigDecimal minimumBid) {
        super("Bid must be at least " + minimumBid.toPlainString(), BiddingErrorCodes.BID_TOO_LOW, HttpStatus.BAD_REQUEST);
        this.minimumBid = minimumBid;
    }
}
```

`src/main/java/com/bidnow/bidding/exception/BiddingExceptionHandler.java`:

```java
package com.bidnow.bidding.exception;

import com.bidnow.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BiddingExceptionHandler {

    @ExceptionHandler(BidTooLowException.class)
    public ResponseEntity<ErrorResponse> handleBidTooLow(BidTooLowException ex, HttpServletRequest request) {
        log.info("Bid rejected: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .errors(Map.of("minimumBid", ex.getMinimumBid().toPlainString()))
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 4: Implement `BidContext` and `BidValidationService`**

`src/main/java/com/bidnow/bidding/dto/BidContext.java`:

```java
package com.bidnow.bidding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Auction state used to pre-validate bids. Deserialized from auction-service's internal
 * {@code GET /api/v1/internal/auctions/{id}/bid-context} and cached as JSON in Redis — field names
 * must stay identical to auction-service's {@code BidContextResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BidContext {
    public static final String STATUS_ACTIVE = "ACTIVE";

    private UUID auctionId;
    private String title;
    private UUID sellerId;
    private String status;
    private BigDecimal currentPrice;
    private BigDecimal bidIncrement;
    private BigDecimal depositAmount;
    private UUID currentWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
}
```

`src/main/java/com/bidnow/bidding/service/BidValidationService.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fast pre-filter against the cached auction context. Mirrors auction-service's authoritative
 * apply-bid rules and their order; auction-service re-checks everything under a row lock.
 */
@Service
public class BidValidationService {

    public void preValidate(BidContext ctx, UUID bidderId, BigDecimal amount, Instant now) {
        if (!BidContext.STATUS_ACTIVE.equals(ctx.getStatus()) || !now.isBefore(ctx.getEndTime().toInstant())) {
            throw new ConflictException("Auction is not open for bidding", BiddingErrorCodes.AUCTION_NOT_OPEN);
        }
        if (ctx.getSellerId().equals(bidderId)) {
            throw new ForbiddenException("Sellers cannot bid on their own auction", BiddingErrorCodes.BID_OWN_AUCTION);
        }
        BigDecimal minimumBid = minimumBid(ctx);
        if (amount.compareTo(minimumBid) < 0) {
            throw new BidTooLowException(minimumBid);
        }
    }

    /** The first bid may equal the starting price (current price starts there); later bids must add the increment. */
    static BigDecimal minimumBid(BidContext ctx) {
        return ctx.getTotalBids() == 0
                ? ctx.getCurrentPrice()
                : ctx.getCurrentPrice().add(ctx.getBidIncrement());
    }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `mvn -q -pl bidding-service -am test -Dtest='BiddingExceptionHandlerTest,BidValidationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. That is 2 handler tests plus 16 validation tests (11 methods, one of which is parameterized over 6 statuses).

---

### Task 3: `AuctionServiceClient` + `AuctionContextCacheService`

**Files:**
- Create: `src/main/java/com/bidnow/bidding/feign/AuctionServiceClient.java`
- Create: `src/main/java/com/bidnow/bidding/service/AuctionContextCacheService.java`
- Test: `src/test/java/com/bidnow/bidding/service/AuctionContextCacheServiceTest.java`

**Interfaces:**
- Consumes (Task 2): `BidContext`, `BiddingErrorCodes`, `ServiceUnavailableException`, and the common `NotFoundException(String, String)`.
- Produces:
  - `@FeignClient(name = "auction-service") interface AuctionServiceClient` with `BaseResponse<BidContext> getBidContext(UUID auctionId)` → `GET /api/v1/internal/auctions/{id}/bid-context`
  - `AuctionContextCacheService(AuctionServiceClient, StringRedisTemplate, ObjectMapper, @Value("${bidding.cache.context-ttl-seconds:600}") long ttlSeconds)`, with:
    - `BidContext get(UUID auctionId)` (cache-aside)
    - `void put(BidContext ctx)`
    - `void evict(UUID auctionId)`
    - `static String key(UUID auctionId)` → `"bidding:auction:" + auctionId + ":context"`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bidnow/bidding/service/AuctionContextCacheServiceTest.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionContextCacheServiceTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final String KEY = "bidding:auction:b0000000-0000-0000-0000-000000000005:context";

    @Mock
    private AuctionServiceClient auctionServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private AuctionContextCacheService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new AuctionContextCacheService(auctionServiceClient, redisTemplate, objectMapper, 600);
    }

    private static BidContext context() {
        return BidContext.builder()
                .auctionId(AUCTION_ID)
                .title("Vintage Watch")
                .sellerId(UUID.randomUUID())
                .status("ACTIVE")
                .currentPrice(new BigDecimal("100.00"))
                .bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00"))
                .totalBids(2)
                .endTime(OffsetDateTime.parse("2026-10-01T12:00:00Z"))
                .build();
    }

    private static Request feignRequest() {
        return Request.create(Request.HttpMethod.GET, "/api/v1/internal/auctions/x/bid-context",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    @Test
    void key_hasContractFormat() {
        assertThat(AuctionContextCacheService.key(AUCTION_ID)).isEqualTo(KEY);
    }

    @Test
    void get_cacheHit_returnsCachedContextWithoutFeignCall() throws Exception {
        when(valueOps.get(KEY)).thenReturn(objectMapper.writeValueAsString(context()));

        BidContext result = service.get(AUCTION_ID);

        assertThat(result).isEqualTo(context());
        verify(auctionServiceClient, never()).getBidContext(any());
    }

    @Test
    void get_cacheMiss_fetchesFromAuctionServiceAndCachesWithTtl() throws Exception {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));

        BidContext result = service.get(AUCTION_ID);

        assertThat(result).isEqualTo(context());
        verify(valueOps).set(KEY, objectMapper.writeValueAsString(context()), Duration.ofSeconds(600));
    }

    @Test
    void get_redisReadFails_fallsThroughToFeign() {
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));

        assertThat(service.get(AUCTION_ID)).isEqualTo(context());
    }

    @Test
    void get_redisWriteFails_stillReturnsContext() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));
        doThrow(new RedisConnectionFailureException("down")).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThat(service.get(AUCTION_ID)).isEqualTo(context());
    }

    @Test
    void get_corruptCachedJson_isEvictedAndRefetched() {
        when(valueOps.get(KEY)).thenReturn("{not json");
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(context()));

        assertThat(service.get(AUCTION_ID)).isEqualTo(context());
        verify(redisTemplate).delete(KEY);
    }

    @Test
    void get_auctionNotFound_throwsNotFound() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID))
                .thenThrow(new FeignException.NotFound("not found", feignRequest(), null, null));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_FOUND);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void get_auctionServiceError_throwsServiceUnavailable() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID))
                .thenThrow(new FeignException.InternalServerError("boom", feignRequest(), null, null));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void get_auctionServiceTimeout_throwsServiceUnavailable() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID))
                .thenThrow(new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, feignRequest()));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void get_emptyResponseBody_throwsServiceUnavailable() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(auctionServiceClient.getBidContext(AUCTION_ID)).thenReturn(BaseResponse.success(null));

        assertThatThrownBy(() -> service.get(AUCTION_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void put_writesJsonWithTtl() throws Exception {
        service.put(context());

        verify(valueOps).set(eq(KEY), eq(objectMapper.writeValueAsString(context())), eq(Duration.ofSeconds(600)));
    }

    @Test
    void evict_deletesKey() {
        service.evict(AUCTION_ID);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void evict_redisFailure_isSwallowed() {
        when(redisTemplate.delete(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        service.evict(AUCTION_ID);
    }
}
```

> Note on `RetryableException`: its constructor signature differs across Feign versions. If `new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, feignRequest())` doesn't compile against the resolved Feign version, use the constructor that version offers with the same meaning (status `-1`, message, method `GET`, no retry-after, the request). Do not change the test's intent.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl bidding-service -am test -Dtest=AuctionContextCacheServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`AuctionServiceClient` and `AuctionContextCacheService` don't exist yet).

- [ ] **Step 3: Implement the Feign client**

`src/main/java/com/bidnow/bidding/feign/AuctionServiceClient.java`:

```java
package com.bidnow.bidding.feign;

import com.bidnow.bidding.dto.BidContext;
import com.bidnow.common.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "auction-service")
public interface AuctionServiceClient {

    @GetMapping("/api/v1/internal/auctions/{id}/bid-context")
    BaseResponse<BidContext> getBidContext(@PathVariable("id") UUID auctionId);
}
```

- [ ] **Step 4: Implement the cache service**

`src/main/java/com/bidnow/bidding/service/AuctionContextCacheService.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Cache-aside reader of auction-service's bid context. Redis is an optimization only: any Redis
 * failure degrades to a cache miss and never fails the request. auction-service stays the authority.
 */
@Slf4j
@Service
public class AuctionContextCacheService {

    private final AuctionServiceClient auctionServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public AuctionContextCacheService(AuctionServiceClient auctionServiceClient,
                                      StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      @Value("${bidding.cache.context-ttl-seconds:600}") long ttlSeconds) {
        this.auctionServiceClient = auctionServiceClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    static String key(UUID auctionId) {
        return "bidding:auction:" + auctionId + ":context";
    }

    public BidContext get(UUID auctionId) {
        BidContext cached = readCache(auctionId);
        if (cached != null) {
            return cached;
        }
        BidContext fresh = fetch(auctionId);
        put(fresh);
        return fresh;
    }

    public void put(BidContext ctx) {
        try {
            redisTemplate.opsForValue().set(key(ctx.getAuctionId()), objectMapper.writeValueAsString(ctx), ttl);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Failed to cache bid context for auction {}: {}", ctx.getAuctionId(), ex.getMessage());
        }
    }

    public void evict(UUID auctionId) {
        try {
            redisTemplate.delete(key(auctionId));
        } catch (RuntimeException ex) {
            log.warn("Failed to evict bid context for auction {}: {}", auctionId, ex.getMessage());
        }
    }

    private BidContext readCache(UUID auctionId) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key(auctionId));
        } catch (RuntimeException ex) {
            log.warn("Redis read failed for auction {} — treating as cache miss: {}", auctionId, ex.getMessage());
            return null;
        }
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BidContext.class);
        } catch (JsonProcessingException ex) {
            log.warn("Corrupt cached bid context for auction {} — evicting: {}", auctionId, ex.getMessage());
            evict(auctionId);
            return null;
        }
    }

    private BidContext fetch(UUID auctionId) {
        BaseResponse<BidContext> response;
        try {
            response = auctionServiceClient.getBidContext(auctionId);
        } catch (FeignException.NotFound ex) {
            throw new NotFoundException("Auction not found: " + auctionId, BiddingErrorCodes.AUCTION_NOT_FOUND);
        } catch (FeignException ex) {
            log.error("auction-service bid-context call failed for auction {} (status {})", auctionId, ex.status(), ex);
            throw new ServiceUnavailableException("Auction service is unavailable");
        }
        if (response == null || response.getData() == null) {
            log.error("auction-service returned an empty bid context for auction {}", auctionId);
            throw new ServiceUnavailableException("Auction service is unavailable");
        }
        return response.getData();
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -q -pl bidding-service -am test -Dtest=AuctionContextCacheServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (13 tests).

---

### Task 4: `SecurityConfig` + `BidController` skeleton

**Files:**
- Create: `src/main/java/com/bidnow/bidding/config/SecurityConfig.java`
- Create: `src/main/java/com/bidnow/bidding/dto/request/PlaceBidRequest.java`
- Create: `src/main/java/com/bidnow/bidding/controller/BidController.java`
- Test: `src/test/java/com/bidnow/bidding/controller/BidControllerTest.java`

**Interfaces:**
- Consumes: `AuctionContextCacheService.get(UUID)` (Task 3); `BidValidationService.preValidate(...)`, `BiddingExceptionHandler` and the exceptions (Task 2); the `Clock` bean (Task 1).
- Produces:
  - `PlaceBidRequest` (`@Data @NoArgsConstructor @AllArgsConstructor`): `@NotNull UUID auctionId`, `@NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount`
  - `POST /api/v1/bids`, which returns `501` after a successful pre-validation. Story 3 replaces the body of `placeBid`.

`SecurityConfig` has no unit test. No service in this repo unit-tests its security chain. The 401 for a missing `X-User-Id` is proven end to end by the Task 5 BDD.

- [ ] **Step 1: Write the failing controller test**

`src/test/java/com/bidnow/bidding/controller/BidControllerTest.java`:

```java
package com.bidnow.bidding.controller;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.BiddingExceptionHandler;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.bidding.service.BidValidationService;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final String BIDDER_ID = "00000000-0000-0000-0000-00000000000b";

    @Mock
    private AuctionContextCacheService contextCache;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BidController controller = new BidController(contextCache, new BidValidationService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new BiddingExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private static BidContext openContext() {
        return BidContext.builder()
                .auctionId(AUCTION_ID).title("Vintage Watch").sellerId(SELLER_ID).status("ACTIVE")
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).totalBids(1)
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }

    private static String body(String auctionId, String amount) {
        return """
                {"auctionId": %s, "amount": %s}
                """.formatted(auctionId, amount);
    }

    private static String validBody(String amount) {
        return body("\"" + AUCTION_ID + "\"", amount);
    }

    @Test
    void validBid_passesPreValidation_returns501UntilStory3() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void bidTooLow_returns400WithMinimumBid() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("104.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_TOO_LOW))
                .andExpect(jsonPath("$.errors.minimumBid").value("105.00"));
    }

    @Test
    void sellerBid_returns403() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", SELLER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("500.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_OWN_AUCTION));
    }

    @Test
    void closedAuction_returns409() throws Exception {
        BidContext closed = openContext();
        closed.setStatus("COMPLETED");
        when(contextCache.get(AUCTION_ID)).thenReturn(closed);

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_OPEN));
    }

    @Test
    void unknownAuction_returns404() throws Exception {
        when(contextCache.get(AUCTION_ID))
                .thenThrow(new NotFoundException("Auction not found", BiddingErrorCodes.AUCTION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_FOUND));
    }

    @Test
    void auctionServiceDown_returns503() throws Exception {
        when(contextCache.get(AUCTION_ID)).thenThrow(new ServiceUnavailableException("down"));

        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.00")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.SERVICE_UNAVAILABLE));
    }

    @Test
    void missingAuctionId_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body("null", "105.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.auctionId").exists());
        verifyNoInteractions(contextCache);
    }

    @Test
    void nonPositiveAmount_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(contextCache);
    }

    @Test
    void amountWithThreeDecimals_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("105.001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(contextCache);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`BidController` and `PlaceBidRequest` don't exist yet).

- [ ] **Step 3: Implement the request DTO and the controller**

`src/main/java/com/bidnow/bidding/dto/request/PlaceBidRequest.java`:

```java
package com.bidnow.bidding.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Public bid body. The bidder is never taken from the body — it comes from the X-User-Id header. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceBidRequest {

    @NotNull(message = "auctionId is required")
    private UUID auctionId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer digits and 2 decimal places")
    private BigDecimal amount;
}
```

`src/main/java/com/bidnow/bidding/controller/BidController.java`:

```java
package com.bidnow.bidding.controller;

import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.bidding.service.BidValidationService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bids")
@RequiredArgsConstructor
@Tag(name = "Bids", description = "Bid placement")
public class BidController {

    private final AuctionContextCacheService contextCache;
    private final BidValidationService bidValidationService;
    private final Clock clock;

    @Operation(summary = "Place a bid",
            description = "Pre-validates against the cached auction context. Placement (deposit lock + apply-bid) "
                    + "lands in BID-102; until then a valid bid returns 501.")
    @PostMapping
    public ResponseEntity<Void> placeBid(@AuthenticatedUserId UUID bidderId,
                                         @Valid @RequestBody PlaceBidRequest request) {
        BidContext ctx = contextCache.get(request.getAuctionId());
        bidValidationService.preValidate(ctx, bidderId, request.getAmount(), clock.instant());
        // BID-102 (Story 3) replaces this with the full placement flow and 201 Created.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
```

- [ ] **Step 4: Implement `SecurityConfig`**

`src/main/java/com/bidnow/bidding/config/SecurityConfig.java`:

```java
package com.bidnow.bidding.config;

import com.bidnow.common.constant.SecurityConstants;
import com.bidnow.common.security.RoleHeaderFilter;
import com.bidnow.common.web.RequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityConstants.PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(401, "Unauthorized"))
                )
                .addFilterBefore(new RoleHeaderFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RequestLoggingFilter(), RoleHeaderFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 5: Run the controller test and the full unit suite**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (9 tests).

Run: `mvn -q -pl bidding-service -am test`
Expected: BUILD SUCCESS. That is 40 tests: 2 + 16 + 13 + 9.

---

### Task 5: BDD harness and end-to-end scenarios

**Files:**
- Create: `src/test/resources/application-bdd.yml`
- Create: `src/test/java/com/bidnow/bidding/bdd/CucumberTest.java`
- Create: `src/test/java/com/bidnow/bidding/bdd/config/CucumberSpringConfig.java`
- Create: `src/test/java/com/bidnow/bidding/bdd/steps/CommonSteps.java`
- Create: `src/test/java/com/bidnow/bidding/bdd/steps/BidPlacementSteps.java`
- Create: `src/test/resources/features/bidding-core.feature`

**Interfaces:**
- Consumes: the whole running service from Tasks 1–4; `bdd-support` (`PostgresContainerSupport`, `RedisContainerSupport`, `WireMockSupport`, `BddRestClient`, `ScenarioContext`).
- Produces: a reusable bidding-service BDD harness. Stories 3–5 add features and steps and register more WireMock stubs (wallet-service, user-service).

The harness has no Kafka container: bidding-service neither produces nor consumes yet, and `spring-kafka` connects lazily. The Feign client is pointed at WireMock through `spring.cloud.openfeign.client.config.auction-service.url`, which bypasses Eureka and the load balancer. Redis is flushed before each scenario so cached contexts never leak between scenarios.

- [ ] **Step 1: BDD profile and runner**

`src/test/resources/application-bdd.yml`:

```yaml
# backend/bidding-service/src/test/resources/application-bdd.yml
spring:
  cloud:
    discovery:
      enabled: false
  jpa:
    show-sql: false

eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false

management:
  tracing:
    enabled: false
```

`src/test/java/com/bidnow/bidding/bdd/CucumberTest.java`:

```java
// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/CucumberTest.java
package com.bidnow.bidding.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameters({
        @ConfigurationParameter(
                key = PLUGIN_PROPERTY_NAME,
                value = "pretty, html:target/cucumber-reports/report.html"
        ),
        @ConfigurationParameter(
                key = GLUE_PROPERTY_NAME,
                value = "com.bidnow.bidding.bdd"
        )
})
public class CucumberTest {
}
```

`src/test/java/com/bidnow/bidding/bdd/config/CucumberSpringConfig.java`:

```java
// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/config/CucumberSpringConfig.java
package com.bidnow.bidding.bdd.config;

import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.bdd.container.RedisContainerSupport;
import com.bidnow.bdd.wiremock.WireMockSupport;
import com.bidnow.bidding.BiddingApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(
        classes = BiddingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("bdd")
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        RedisContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.cloud.openfeign.client.config.auction-service.url", WireMockSupport::baseUrl);
    }
}
```

If the context fails to start because a Kafka bean tries to connect eagerly (for example, `common`'s audit publisher), add `KafkaContainerSupport.properties()` to the registry in the same way. Record that in the report.

- [ ] **Step 2: Common assertion steps**

`src/test/java/com/bidnow/bidding/bdd/steps/CommonSteps.java`:

```java
// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/CommonSteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class CommonSteps {

    private final ScenarioContext ctx;

    @Then("the response status should be {int}")
    public void assertStatus(int expectedStatus) {
        assertThat(ctx.getLastResponse().statusCode())
                .as("Expected HTTP status %d but got %d. Body: %s",
                        expectedStatus,
                        ctx.getLastResponse().statusCode(),
                        ctx.getLastResponse().asString())
                .isEqualTo(expectedStatus);
    }

    @Then("the response field {string} should equal {string}")
    public void assertFieldEquals(String jsonPath, String expectedValue) {
        String actual = ctx.getLastResponse().jsonPath().getString(jsonPath);
        assertThat(actual)
                .as("Expected field '%s' to equal '%s' but was '%s'", jsonPath, expectedValue, actual)
                .isEqualTo(expectedValue);
    }
}
```

- [ ] **Step 3: Bid steps with WireMock stubs**

`src/test/java/com/bidnow/bidding/bdd/steps/BidPlacementSteps.java`:

```java
// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidPlacementSteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class BidPlacementSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Before
    public void resetExternalState() {
        WireMockSupport.reset();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Given("auction-service has auction {string} with status {string}, price {string}, increment {string}, {int} bids and seller {string}")
    public void stubBidContext(String auctionId, String status, String price, String increment,
                               int totalBids, String sellerId) {
        String endTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).toString();
        String body = """
                {"status":200,"message":"Success","data":{
                  "auctionId":"%s","title":"BDD Auction","sellerId":"%s","status":"%s",
                  "currentPrice":%s,"bidIncrement":%s,"depositAmount":20.00,
                  "currentWinnerId":null,"totalBids":%d,"endTime":"%s"}}
                """.formatted(auctionId, sellerId, status, price, increment, totalBids, endTime);
        WireMockSupport.SERVER.stubFor(get(urlEqualTo(contextPath(auctionId)))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Given("auction-service responds {int} for the bid context of auction {string}")
    public void stubBidContextError(int status, String auctionId) {
        WireMockSupport.SERVER.stubFor(get(urlEqualTo(contextPath(auctionId)))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":" + status + ",\"errorCode\":\"X\",\"message\":\"stub\"}")));
    }

    @When("user {string} bids {string} on auction {string}")
    public void userBids(String userId, String amount, String auctionId) {
        ctx.setLastResponse(client.given()
                .header("X-User-Id", userId)
                .body(Map.of("auctionId", auctionId, "amount", new BigDecimal(amount)))
                .post("/api/v1/bids"));
    }

    @When("an unauthenticated request bids {string} on auction {string}")
    public void unauthenticatedBid(String amount, String auctionId) {
        ctx.setLastResponse(client.given()
                .body(Map.of("auctionId", auctionId, "amount", new BigDecimal(amount)))
                .post("/api/v1/bids"));
    }

    @Then("auction-service should have received {int} bid-context request(s) for auction {string}")
    public void verifyContextCalls(int count, String auctionId) {
        WireMockSupport.SERVER.verify(count, getRequestedFor(urlEqualTo(contextPath(auctionId))));
    }

    @Then("the bid context for auction {string} should be cached in Redis")
    public void contextCached(String auctionId) {
        assertThat(redisTemplate.opsForValue().get("bidding:auction:" + auctionId + ":context")).isNotNull();
        assertThat(redisTemplate.getExpire("bidding:auction:" + auctionId + ":context")).isPositive();
    }

    @Then("the bids table should exist with its indexes")
    public void bidsSchemaExists() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'bids'", String.class);
        assertThat(columns).contains("id", "auction_id", "bidder_id", "amount", "is_auto_bid",
                "is_anti_sniping_triggered", "created_at", "updated_at");
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'bids'", String.class);
        assertThat(indexes).contains("idx_bids_auction_created", "idx_bids_auction_amount",
                "idx_bids_bidder_auction_created");
    }

    private static String contextPath(String auctionId) {
        return "/api/v1/internal/auctions/" + auctionId + "/bid-context";
    }
}
```

- [ ] **Step 4: Feature file**

`src/test/resources/features/bidding-core.feature`:

```gherkin
@bidding @core @regression
Feature: Bidding service core — schema, security, context cache and pre-validation

  @smoke
  Scenario: Liquibase creates the bids schema
    Then the bids table should exist with its indexes

  @negative
  Scenario: A bid without the gateway user header is rejected
    When an unauthenticated request bids "105.00" on auction "c0000000-0000-0000-0000-000000000001"
    Then the response status should be 401

  Scenario: A valid bid passes pre-validation (placement arrives in BID-102)
    Given auction-service has auction "c0000000-0000-0000-0000-000000000002" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "c0000000-0000-0000-0000-000000000002"
    Then the response status should be 501
    And the bid context for auction "c0000000-0000-0000-0000-000000000002" should be cached in Redis

  Scenario: The bid context is fetched once and then served from Redis
    Given auction-service has auction "c0000000-0000-0000-0000-000000000003" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "104.00" on auction "c0000000-0000-0000-0000-000000000003"
    And user "550e8400-e29b-41d4-a716-446655440011" bids "104.00" on auction "c0000000-0000-0000-0000-000000000003"
    Then auction-service should have received 1 bid-context request for auction "c0000000-0000-0000-0000-000000000003"

  @negative
  Scenario: A bid below the minimum increment is rejected
    Given auction-service has auction "c0000000-0000-0000-0000-000000000004" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "104.00" on auction "c0000000-0000-0000-0000-000000000004"
    Then the response status should be 400
    And the response field "errorCode" should equal "BID_TOO_LOW"
    And the response field "errors.minimumBid" should equal "105.00"

  @negative
  Scenario: The seller cannot bid on their own auction
    Given auction-service has auction "c0000000-0000-0000-0000-000000000005" with status "ACTIVE", price "100.00", increment "5.00", 0 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440001" bids "500.00" on auction "c0000000-0000-0000-0000-000000000005"
    Then the response status should be 403
    And the response field "errorCode" should equal "BID_OWN_AUCTION"

  @negative
  Scenario: A bid on a non-live auction is rejected
    Given auction-service has auction "c0000000-0000-0000-0000-000000000006" with status "SCHEDULED", price "100.00", increment "5.00", 0 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "c0000000-0000-0000-0000-000000000006"
    Then the response status should be 409
    And the response field "errorCode" should equal "AUCTION_NOT_OPEN"

  @negative
  Scenario: A bid on an unknown auction is rejected
    Given auction-service responds 404 for the bid context of auction "c0000000-0000-0000-0000-000000000007"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "c0000000-0000-0000-0000-000000000007"
    Then the response status should be 404
    And the response field "errorCode" should equal "AUCTION_NOT_FOUND"

  @negative
  Scenario: auction-service failure makes bidding unavailable
    Given auction-service responds 500 for the bid context of auction "c0000000-0000-0000-0000-000000000008"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "c0000000-0000-0000-0000-000000000008"
    Then the response status should be 503
    And the response field "errorCode" should equal "SERVICE_UNAVAILABLE"
```

- [ ] **Step 5: Compile, and run the BDD suite if Docker is available**

Run: `mvn -q -pl bidding-service -am test-compile`
Expected: BUILD SUCCESS.

Check Docker: `timeout 30 docker info --format '{{.ServerVersion}}'`. If it answers, run `mvn -q -pl bidding-service -am test -Pbdd` and expect all 9 scenarios to pass. If Docker is unavailable, report the BDD run as NOT RUN. Do not claim it passed.

---

### Task 6: Documentation

**Files:**
- Create: `docs/database/bidding-service-schema.md` (repo root)
- Modify: `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (repo root), §5
- Modify: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (repo root)

- [ ] **Step 1: Schema doc**

Create `docs/database/bidding-service-schema.md`:

````markdown
# Bidding Service Schema (`bidding_db`)

Owned exclusively by bidding-service. Migrations: `backend/bidding-service/src/main/resources/db/changelog/`.

## bids

```sql
CREATE TABLE bids (
    id                        UUID PRIMARY KEY,               -- assigned by bidding-service (the bidId sent to auction-service)
    auction_id                UUID           NOT NULL,        -- auction-service auction id (no FK: separate database)
    bidder_id                 UUID           NOT NULL,        -- identity user id
    amount                    DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    is_auto_bid               BOOLEAN        NOT NULL DEFAULT FALSE,
    is_anti_sniping_triggered BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bids_auction_created        ON bids (auction_id, created_at DESC);          -- auction bid history
CREATE INDEX idx_bids_auction_amount         ON bids (auction_id, amount DESC);              -- highest bid lookups
CREATE INDEX idx_bids_bidder_auction_created ON bids (bidder_id, auction_id, created_at DESC); -- "my bids"
```

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | App-assigned bid id; auction-service stores it as `last_bid_id` |
| auction_id | UUID | NOT NULL | Auction the bid was placed on |
| bidder_id | UUID | NOT NULL | Bidder (from gateway `X-User-Id`) |
| amount | DECIMAL(15,2) | NOT NULL, > 0 | Bid amount |
| is_auto_bid | BOOLEAN | NOT NULL | Placed by auto-bid (Phase 2; always false for now) |
| is_anti_sniping_triggered | BOOLEAN | NOT NULL | This bid extended the auction end time (BID-104) |
| created_at / updated_at | TIMESTAMP | NOT NULL | Managed by `BaseEntity` |

## Redis keys

| Key | Value | TTL |
| :--- | :--- | :--- |
| `bidding:auction:{auctionId}:context` | JSON bid context from auction-service | `bidding.cache.context-ttl-seconds` (600) |
````

- [ ] **Step 2: Align the spec's timestamp type**

In `docs/superpowers/specs/2026-07-04-bidding-service-design.md`, §5, in the `bids (...)` definition, replace `created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL` with `created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL`. Add after the code block:

```markdown
Timestamps are `TIMESTAMP` (not `TIMESTAMPTZ`) to match the shared `BaseEntity` (`LocalDateTime`) used by every service. `id` has no DB default — bidding-service assigns it.
```

- [ ] **Step 3: Update the roadmap Story 2 section**

In `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`, under "## Story 2":
- Tick 2.2, 2.3 and 2.4 (`- [ ]` → `- [x]`).
- Tick 2.1 only if the BDD suite in Task 5 actually ran and passed. Its check includes the Liquibase changelog being applied against Postgres.
- Replace the Files bullet that mentions `spring.kafka` and `RedisConfig` with this text: "Kafka config deferred to Story 3; no `RedisConfig` — Boot's `StringRedisTemplate` + `ObjectMapper` are used; `PlaceBidResponse` deferred to Story 3; the BDD harness (Testcontainers Postgres/Redis + WireMock auction-service) is added here and reused by Stories 3–5."

- [ ] **Step 4: Final verification**

Run: `mvn -q -pl bidding-service -am test`
Expected: BUILD SUCCESS (40 tests). Leave all changes uncommitted.

---

## Self-review notes

- **Coverage against issue #121 and roadmap Story 2:**
  - Scenario 1 (schema): Task 1 plus the BDD schema scenario.
  - Scenario 2 (increment): Tasks 2 and 4, plus BDD.
  - Scenario 3 (seller): Tasks 2 and 4, plus BDD.
  - Scenario 4 (non-live or ended): Task 2's boundaries, plus BDD.
  - Scenario 5 (Redis cache-aside): Task 3, plus the BDD "fetched once" scenario.
  - Roadmap 2.1–2.4: Tasks 1–4.
  - The DoD's "security config, Redis config" item: Tasks 1 and 4.
- **Deliberate deviations from the roadmap's Story 2 file list:**
  - No Kafka config, because it is YAGNI until Story 3 produces events.
  - No `RedisConfig`, because Boot auto-configures `StringRedisTemplate` and `ObjectMapper` with `JavaTimeModule`.
  - No `PlaceBidResponse`, because nothing returns it until Story 3.
  - No `permitAll` for history GETs, which belongs to Story 5.
  - Added a BDD harness, which issue #121's DoD and later stories need.
- **Known limitation:** the `501` skeleton is intentional and lives only on `feature/bidding`. Story 3 must replace it before the branch merges.
