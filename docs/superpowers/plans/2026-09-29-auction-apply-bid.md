# Story 1 — AUC-BID: Auction Internal Bid API & Row Locking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give bidding-service two internal auction-service endpoints: read an auction's bid context, and atomically apply a bid under a row lock. Also serialize closure and cancellation against bid application, so no bid is lost or accepted after close.

**Architecture:** A new `AuctionBidService` loads the `auction_items` row with `SELECT … FOR UPDATE` (`AuctionItemRepository.findByIdForUpdate`). Under that lock it re-validates status, end time, ownership and minimum amount, then updates `current_price`, `current_winner_id`, `total_bids` and `last_bid_id` in one transaction. `AuctionInternalController` exposes it under `/api/v1/internal/auctions`, which is `permitAll` in auction-service and already blocked from public traffic by the gateway's `AuthenticationFilter`. `AuctionClosureService.close`, seller cancel, admin cancel and admin force-close switch to the same locking read. Anti-sniping is **not** in this story (Story 4). `extended` is always `false` here.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Data JPA (Hibernate 6), Liquibase formatted SQL, PostgreSQL, Lombok, JUnit 5, Mockito, AssertJ, standalone MockMvc, Cucumber + Testcontainers (`bdd-support`).

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (§2, §4 auction-service contract). Roadmap: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (Story 1).

## Global Constraints

- Scope is auction-service only, plus `docs/architecture.md`. Do not touch bidding-service, wallet-service, the api-gateway or `common`.
- Base path: `/api/v1/internal/auctions`. The gateway's `AuthenticationFilter` already blocks `/api/v1/**/internal/**` publicly, so no gateway change is needed. auction-service must `permitAll` `/api/v1/internal/**`.
- Responses are `ResponseEntity<BaseResponse<T>>`. Errors are common `ErrorResponse`.
- Error codes:
  - `AUCTION_NOT_FOUND` 404
  - `AUCTION_NOT_OPEN` 409, when status ≠ `ACTIVE` or `now >= endTime`
  - `BID_OWN_AUCTION` 403
  - `BID_TOO_LOW` 400, with `errors: { minimumBid }`
  - validation failures keep the existing `INVALID_INPUT` 400
- Minimum bid: `totalBids == 0` → `currentPrice` (which equals `starting_price` for an auction with no bids). Otherwise `currentPrice + bidIncrement`.
- The current winner may raise their own bid. There is no rule against it.
- `applyBid` is idempotent on `bidId`: if `auction.lastBidId == bidId`, return the current state unchanged, with `previousWinnerId = null` and no validation.
- Every read of `auction_items` that leads to a write depending on `status`/`end_time` inside `applyBid`, `close`, seller `cancelAuction`, admin `cancelAuction` and admin `forceCloseAuction` must use `findByIdForUpdate`.
- Time comes from an injected `java.time.Clock` in `AuctionBidService`. Never call `OffsetDateTime.now()` without the clock in the new code.
- **Do not run `git commit` or `git add`.** Leave all changes uncommitted on branch `feature/bidding`. The user commits.
- Run Maven from `backend/`. Unit tests: `mvn -q -pl auction-service -am test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`. BDD needs Docker: `mvn -q -pl auction-service -am test -Pbdd`.

---

## File Map

All paths are relative to `backend/auction-service/` unless noted.

| File | Action | Responsibility |
|---|---|---|
| `src/main/resources/db/changelog/migrations/004-bid-tracking.sql` | Create | `last_bid_id` column |
| `src/main/resources/db/changelog/db.changelog-master.xml` | Modify | Include 004 |
| `src/main/java/com/bidnow/auction/domain/entity/AuctionItem.java` | Modify | `lastBidId` field |
| `src/main/java/com/bidnow/auction/repository/AuctionItemRepository.java` | Modify | `findByIdForUpdate` |
| `src/main/java/com/bidnow/auction/config/ClockConfig.java` | Create | `Clock` bean |
| `src/main/java/com/bidnow/auction/constant/AuctionErrorCodes.java` | Create | Error-code constants |
| `src/main/java/com/bidnow/auction/exception/ConflictException.java` | Create | 409 |
| `src/main/java/com/bidnow/auction/exception/BidTooLowException.java` | Create | 400 carrying `minimumBid` |
| `src/main/java/com/bidnow/auction/exception/AuctionExceptionHandler.java` | Create | `BidTooLowException` → `errors.minimumBid` |
| `src/main/java/com/bidnow/auction/dto/response/BidContextResponse.java` | Create | GET payload |
| `src/main/java/com/bidnow/auction/dto/request/ApplyBidRequest.java` | Create | POST body |
| `src/main/java/com/bidnow/auction/dto/response/ApplyBidResponse.java` | Create | POST payload |
| `src/main/java/com/bidnow/auction/service/AuctionBidService.java` | Create | Context read + locked apply |
| `src/main/java/com/bidnow/auction/controller/AuctionInternalController.java` | Create | Internal endpoints |
| `src/main/java/com/bidnow/auction/config/SecurityConfig.java` | Modify | `permitAll` internal |
| `src/main/java/com/bidnow/auction/service/AuctionClosureService.java` | Modify | Locked read in `close` |
| `src/main/java/com/bidnow/auction/service/impl/AuctionServiceImpl.java` | Modify | Locked read in seller `cancelAuction` |
| `src/main/java/com/bidnow/auction/service/impl/AdminAuctionServiceImpl.java` | Modify | Locked read in admin cancel / force-close |
| `src/test/java/com/bidnow/auction/exception/AuctionExceptionHandlerTest.java` | Create | Handler test |
| `src/test/java/com/bidnow/auction/service/AuctionBidServiceTest.java` | Create | Service tests |
| `src/test/java/com/bidnow/auction/controller/AuctionInternalControllerTest.java` | Create | HTTP tests |
| `src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java` | Modify | Stub locked read |
| `src/test/java/com/bidnow/auction/service/impl/AdminAuctionServiceImplTest.java` | Modify | Stub locked read |
| `src/test/java/com/bidnow/auction/service/impl/AuctionServiceImplTest.java` | Modify | Seller-cancel lock test |
| `src/test/resources/db/changelog/test-data.sql` | Modify | Bid BDD seed auctions |
| `src/test/resources/features/internal-bid-api.feature` | Create | BDD incl. concurrency |
| `src/test/java/com/bidnow/auction/bdd/steps/InternalBidSteps.java` | Create | Step definitions |
| `docs/architecture.md` (repo root) | Modify | Internal API table |

---

### Task 1: Persistence — `last_bid_id`, entity field, locking read

**Files:**
- Create: `src/main/resources/db/changelog/migrations/004-bid-tracking.sql`
- Modify: `src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `src/main/java/com/bidnow/auction/domain/entity/AuctionItem.java`
- Modify: `src/main/java/com/bidnow/auction/repository/AuctionItemRepository.java`

**Interfaces:**
- Produces:
  - `AuctionItem.getLastBidId()/setLastBidId(UUID)`
  - `AuctionItemRepository.findByIdForUpdate(UUID id): Optional<AuctionItem>`, which takes a `PESSIMISTIC_WRITE` lock and excludes soft-deleted rows

There is no unit test for this task. The locking query is exercised against real Postgres by the BDD in Task 7.

- [ ] **Step 1: Create the migration**

`src/main/resources/db/changelog/migrations/004-bid-tracking.sql`:

```sql
-- liquibase formatted sql

--changeset bidnow:auction-bid-tracking
--comment: Track the last applied bid for idempotent internal apply-bid (Epic #120, Story 1)

ALTER TABLE auction_items
    ADD COLUMN last_bid_id UUID NULL;
```

- [ ] **Step 2: Include it in the master changelog**

In `db.changelog-master.xml`, after the `003-admin-auction-moderation.sql` include, add:

```xml
    <include file="/db/changelog/migrations/004-bid-tracking.sql"/>
```

- [ ] **Step 3: Add the entity field**

In `AuctionItem.java`, directly after the `totalBids` field, add:

```java
    @Column(name = "last_bid_id")
    private UUID lastBidId;
```

- [ ] **Step 4: Add the locking repository method**

In `AuctionItemRepository.java`, add these imports:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
```

and this method below `findByIdAndDeletedAtIsNull`:

```java
    /**
     * Loads a non-deleted auction with a {@code SELECT … FOR UPDATE} row lock. Every write that depends
     * on {@code status} or {@code end_time} (apply-bid, closure, cancel, force-close) must use this so
     * those operations serialize on the auction row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AuctionItem a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AuctionItem> findByIdForUpdate(@Param("id") UUID id);
```

- [ ] **Step 5: Compile and run the existing suite**

Run: `mvn -q -pl auction-service -am test`
Expected: BUILD SUCCESS. Existing tests are unaffected.

---

### Task 2: Error infrastructure, clock bean and DTOs

**Files:**
- Create: `src/main/java/com/bidnow/auction/config/ClockConfig.java`
- Create: `src/main/java/com/bidnow/auction/constant/AuctionErrorCodes.java`
- Create: `src/main/java/com/bidnow/auction/exception/ConflictException.java`
- Create: `src/main/java/com/bidnow/auction/exception/BidTooLowException.java`
- Create: `src/main/java/com/bidnow/auction/exception/AuctionExceptionHandler.java`
- Create: `src/main/java/com/bidnow/auction/dto/response/BidContextResponse.java`
- Create: `src/main/java/com/bidnow/auction/dto/request/ApplyBidRequest.java`
- Create: `src/main/java/com/bidnow/auction/dto/response/ApplyBidResponse.java`
- Test: `src/test/java/com/bidnow/auction/exception/AuctionExceptionHandlerTest.java`

**Interfaces:**
- Produces:
  - `AuctionErrorCodes.{AUCTION_NOT_FOUND, AUCTION_NOT_OPEN, BID_TOO_LOW, BID_OWN_AUCTION}` (String)
  - `new ConflictException(String message, String errorCode)` → HTTP 409
  - `new BidTooLowException(BigDecimal minimumBid)` with `getMinimumBid()`; error code `BID_TOO_LOW`, HTTP 400
  - `BidContextResponse` (builder): `UUID auctionId, String title, UUID sellerId, AuctionStatus status, BigDecimal currentPrice, BigDecimal bidIncrement, BigDecimal depositAmount, UUID currentWinnerId, int totalBids, OffsetDateTime endTime`
  - `ApplyBidRequest` (`@Data @NoArgsConstructor @AllArgsConstructor`): `UUID bidId, UUID bidderId, BigDecimal amount`
  - `ApplyBidResponse` (builder): `UUID auctionId, BigDecimal currentPrice, UUID currentWinnerId, UUID previousWinnerId, int totalBids, OffsetDateTime endTime, boolean extended, int extensionCount`
  - A `Clock` bean (`Clock.systemUTC()`)

- [ ] **Step 1: Write the failing handler test**

`src/test/java/com/bidnow/auction/exception/AuctionExceptionHandlerTest.java`:

```java
package com.bidnow.auction.exception;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionExceptionHandlerTest {

    private final AuctionExceptionHandler handler = new AuctionExceptionHandler();

    @Test
    void handleBidTooLow_returns400WithMinimumBid() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/auctions/x/bids");

        ResponseEntity<ErrorResponse> response =
                handler.handleBidTooLow(new BidTooLowException(new BigDecimal("550.00")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(AuctionErrorCodes.BID_TOO_LOW);
        assertThat(response.getBody().getErrors()).containsEntry("minimumBid", "550.00");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/internal/auctions/x/bids");
    }

    @Test
    void conflictException_hasStatus409() {
        ConflictException ex = new ConflictException("closed", AuctionErrorCodes.AUCTION_NOT_OPEN);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo(AuctionErrorCodes.AUCTION_NOT_OPEN);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`AuctionExceptionHandler`, `BidTooLowException`, `ConflictException`, `AuctionErrorCodes` not found).

- [ ] **Step 3: Create the error codes, exceptions and handler**

`src/main/java/com/bidnow/auction/constant/AuctionErrorCodes.java`:

```java
package com.bidnow.auction.constant;

public final class AuctionErrorCodes {
    public static final String AUCTION_NOT_FOUND = "AUCTION_NOT_FOUND";
    public static final String AUCTION_NOT_OPEN = "AUCTION_NOT_OPEN";
    public static final String BID_TOO_LOW = "BID_TOO_LOW";
    public static final String BID_OWN_AUCTION = "BID_OWN_AUCTION";

    private AuctionErrorCodes() {
    }
}
```

`src/main/java/com/bidnow/auction/exception/ConflictException.java`:

```java
package com.bidnow.auction.exception;

import com.bidnow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {
    public ConflictException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.CONFLICT);
    }
}
```

`src/main/java/com/bidnow/auction/exception/BidTooLowException.java`:

```java
package com.bidnow.auction.exception;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.common.exception.BaseException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

@Getter
public class BidTooLowException extends BaseException {

    private final BigDecimal minimumBid;

    public BidTooLowException(BigDecimal minimumBid) {
        super("Bid must be at least " + minimumBid.toPlainString(), AuctionErrorCodes.BID_TOO_LOW, HttpStatus.BAD_REQUEST);
        this.minimumBid = minimumBid;
    }
}
```

`src/main/java/com/bidnow/auction/exception/AuctionExceptionHandler.java`:

```java
package com.bidnow.auction.exception;

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
public class AuctionExceptionHandler {

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

- [ ] **Step 4: Create the clock bean and the DTOs**

`src/main/java/com/bidnow/auction/config/ClockConfig.java`:

```java
package com.bidnow.auction.config;

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

`src/main/java/com/bidnow/auction/dto/response/BidContextResponse.java`:

```java
package com.bidnow.auction.dto.response;

import com.bidnow.auction.domain.enums.AuctionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BidContextResponse {
    private UUID auctionId;
    private String title;
    private UUID sellerId;
    private AuctionStatus status;
    private BigDecimal currentPrice;
    private BigDecimal bidIncrement;
    private BigDecimal depositAmount;
    private UUID currentWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
}
```

`src/main/java/com/bidnow/auction/dto/request/ApplyBidRequest.java`:

```java
package com.bidnow.auction.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyBidRequest {

    @NotNull(message = "bidId is required")
    private UUID bidId;

    @NotNull(message = "bidderId is required")
    private UUID bidderId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer digits and 2 decimal places")
    private BigDecimal amount;
}
```

`src/main/java/com/bidnow/auction/dto/response/ApplyBidResponse.java`:

```java
package com.bidnow.auction.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ApplyBidResponse {
    private UUID auctionId;
    private BigDecimal currentPrice;
    private UUID currentWinnerId;
    private UUID previousWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
    private boolean extended;
    private int extensionCount;
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests).

---

### Task 3: `AuctionBidService` — bid context and locked apply-bid

**Files:**
- Create: `src/main/java/com/bidnow/auction/service/AuctionBidService.java`
- Test: `src/test/java/com/bidnow/auction/service/AuctionBidServiceTest.java`

**Interfaces:**
- Consumes (Tasks 1–2): `AuctionItemRepository.findByIdAndDeletedAtIsNull`, `findByIdForUpdate`, `AuctionItem.lastBidId`, `AuctionErrorCodes`, `ConflictException`, `BidTooLowException`, `BidContextResponse`, `ApplyBidRequest`, `ApplyBidResponse`, the `Clock` bean.
- Produces:
  - `AuctionBidService(AuctionItemRepository, Clock)` (constructor via `@RequiredArgsConstructor`)
  - `BidContextResponse getBidContext(UUID auctionId)`, read-only
  - `ApplyBidResponse applyBid(UUID auctionId, ApplyBidRequest request)`, `@Transactional`
  - `static BigDecimal minimumBid(AuctionItem auction)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/bidnow/auction/service/AuctionBidServiceTest.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.exception.BidTooLowException;
import com.bidnow.auction.exception.ConflictException;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PREVIOUS_WINNER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Mock
    private AuctionItemRepository auctionItemRepository;

    private AuctionBidService service;

    @BeforeEach
    void setUp() {
        service = new AuctionBidService(auctionItemRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuctionItem activeAuction(int totalBids, String currentPrice) {
        return AuctionItem.builder()
                .id(UUID.randomUUID())
                .sellerId(SELLER_ID)
                .title("Vintage Watch")
                .status(AuctionStatus.ACTIVE)
                .startingPrice(new BigDecimal("100.00"))
                .bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00"))
                .currentPrice(new BigDecimal(currentPrice))
                .currentWinnerId(totalBids == 0 ? null : PREVIOUS_WINNER_ID)
                .totalBids(totalBids)
                .extensionCount(0)
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }

    private ApplyBidRequest bid(String amount) {
        return new ApplyBidRequest(UUID.randomUUID(), BIDDER_ID, new BigDecimal(amount));
    }

    // ---------------------------------------------------------------------
    // getBidContext
    // ---------------------------------------------------------------------

    @Test
    void getBidContext_mapsAuctionFields() {
        AuctionItem auction = activeAuction(2, "110.00");
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auction.getId())).thenReturn(Optional.of(auction));

        BidContextResponse ctx = service.getBidContext(auction.getId());

        assertThat(ctx.getAuctionId()).isEqualTo(auction.getId());
        assertThat(ctx.getTitle()).isEqualTo("Vintage Watch");
        assertThat(ctx.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(ctx.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(ctx.getCurrentPrice()).isEqualByComparingTo("110.00");
        assertThat(ctx.getBidIncrement()).isEqualByComparingTo("5.00");
        assertThat(ctx.getDepositAmount()).isEqualByComparingTo("20.00");
        assertThat(ctx.getCurrentWinnerId()).isEqualTo(PREVIOUS_WINNER_ID);
        assertThat(ctx.getTotalBids()).isEqualTo(2);
        assertThat(ctx.getEndTime()).isEqualTo(auction.getEndTime());
    }

    @Test
    void getBidContext_unknownAuction_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBidContext(id))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_FOUND);
    }

    // ---------------------------------------------------------------------
    // applyBid — happy paths
    // ---------------------------------------------------------------------

    @Test
    void applyBid_firstBidAtStartingPrice_isAccepted() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        ApplyBidRequest request = bid("100.00");

        ApplyBidResponse response = service.applyBid(auction.getId(), request);

        assertThat(response.getCurrentPrice()).isEqualByComparingTo("100.00");
        assertThat(response.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(response.getPreviousWinnerId()).isNull();
        assertThat(response.getTotalBids()).isEqualTo(1);
        assertThat(response.isExtended()).isFalse();
        assertThat(auction.getLastBidId()).isEqualTo(request.getBidId());
        verify(auctionItemRepository).save(auction);
    }

    @Test
    void applyBid_subsequentBidAtExactMinimum_updatesStateAndReturnsPreviousWinner() {
        AuctionItem auction = activeAuction(3, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(auction.getCurrentPrice()).isEqualByComparingTo("105.00");
        assertThat(auction.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(auction.getTotalBids()).isEqualTo(4);
        assertThat(response.getPreviousWinnerId()).isEqualTo(PREVIOUS_WINNER_ID);
        assertThat(response.getEndTime()).isEqualTo(auction.getEndTime());
        assertThat(response.getExtensionCount()).isZero();
    }

    @Test
    void applyBid_readsWithRowLockOnly() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        service.applyBid(auction.getId(), bid("100.00"));

        verify(auctionItemRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void applyBid_replayOfLastBidId_returnsCurrentStateWithoutChanges() {
        AuctionItem auction = activeAuction(4, "120.00");
        UUID bidId = UUID.randomUUID();
        auction.setLastBidId(bidId);
        auction.setCurrentWinnerId(BIDDER_ID);
        auction.setStatus(AuctionStatus.COMPLETED); // replay must succeed even after the auction closed
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(),
                new ApplyBidRequest(bidId, BIDDER_ID, new BigDecimal("120.00")));

        assertThat(response.getTotalBids()).isEqualTo(4);
        assertThat(response.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(response.getPreviousWinnerId()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // applyBid — rejections
    // ---------------------------------------------------------------------

    @Test
    void applyBid_unknownAuction_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(auctionItemRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyBid(id, bid("100.00")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = AuctionStatus.class, names = {"DRAFT", "SCHEDULED", "COMPLETED", "FAILED", "CANCELLED", "REJECTED"})
    void applyBid_notActive_throwsConflict(AuctionStatus status) {
        AuctionItem auction = activeAuction(0, "100.00");
        auction.setStatus(status);
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("100.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_OPEN);
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void applyBid_exactlyAtEndTime_throwsConflict() {
        AuctionItem auction = activeAuction(0, "100.00");
        auction.setEndTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("100.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void applyBid_oneMillisecondBeforeEndTime_isAccepted() {
        AuctionItem auction = activeAuction(0, "100.00");
        auction.setEndTime(OffsetDateTime.ofInstant(NOW.plusMillis(1), ZoneOffset.UTC));
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("100.00"));

        assertThat(response.getTotalBids()).isEqualTo(1);
    }

    @Test
    void applyBid_sellerBiddingOnOwnAuction_throwsForbidden() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(),
                new ApplyBidRequest(UUID.randomUUID(), SELLER_ID, new BigDecimal("500.00"))))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.BID_OWN_AUCTION);
    }

    @Test
    void applyBid_firstBidBelowStartingPrice_throwsBidTooLow() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("99.99")))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("100.00"));
    }

    @Test
    void applyBid_subsequentBidBelowIncrement_throwsBidTooLow() {
        AuctionItem auction = activeAuction(1, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("104.00")))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("105.00"));
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void minimumBid_usesCurrentPriceForFirstBidAndAddsIncrementAfter() {
        assertThat(AuctionBidService.minimumBid(activeAuction(0, "100.00"))).isEqualByComparingTo("100.00");
        assertThat(AuctionBidService.minimumBid(activeAuction(2, "100.00"))).isEqualByComparingTo("105.00");
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionBidServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`AuctionBidService` not found).

- [ ] **Step 3: Implement `AuctionBidService`**

`src/main/java/com/bidnow/auction/service/AuctionBidService.java`:

```java
package com.bidnow.auction.service;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.exception.BidTooLowException;
import com.bidnow.auction.exception.ConflictException;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Internal bid operations called by bidding-service. auction-service is the source of truth for
 * price, winner and end time. {@link #applyBid} re-validates and mutates under a row lock, so it
 * serializes with closure and cancellation, which take the same lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBidService {

    private final AuctionItemRepository auctionItemRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BidContextResponse getBidContext(UUID auctionId) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElseThrow(() -> notFound(auctionId));
        return BidContextResponse.builder()
                .auctionId(auction.getId())
                .title(auction.getTitle())
                .sellerId(auction.getSellerId())
                .status(auction.getStatus())
                .currentPrice(auction.getCurrentPrice())
                .bidIncrement(auction.getBidIncrement())
                .depositAmount(auction.getDepositAmount())
                .currentWinnerId(auction.getCurrentWinnerId())
                .totalBids(auction.getTotalBids())
                .endTime(auction.getEndTime())
                .build();
    }

    /**
     * Applies a bid atomically. Idempotent on {@code bidId}: replaying the last applied bid returns
     * the current state without re-validating or changing anything.
     */
    @Transactional
    public ApplyBidResponse applyBid(UUID auctionId, ApplyBidRequest request) {
        AuctionItem auction = auctionItemRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> notFound(auctionId));

        if (request.getBidId().equals(auction.getLastBidId())) {
            log.info("Replay of bid {} on auction {} — returning current state", request.getBidId(), auctionId);
            return toResponse(auction, null);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (auction.getStatus() != AuctionStatus.ACTIVE || !now.isBefore(auction.getEndTime())) {
            throw new ConflictException("Auction is not open for bidding", AuctionErrorCodes.AUCTION_NOT_OPEN);
        }
        if (auction.getSellerId().equals(request.getBidderId())) {
            throw new ForbiddenException("Sellers cannot bid on their own auction", AuctionErrorCodes.BID_OWN_AUCTION);
        }
        BigDecimal minimumBid = minimumBid(auction);
        if (request.getAmount().compareTo(minimumBid) < 0) {
            throw new BidTooLowException(minimumBid);
        }

        UUID previousWinnerId = auction.getCurrentWinnerId();
        auction.setCurrentPrice(request.getAmount());
        auction.setCurrentWinnerId(request.getBidderId());
        auction.setTotalBids(auction.getTotalBids() + 1);
        auction.setLastBidId(request.getBidId());
        auctionItemRepository.save(auction);

        log.info("Applied bid {} on auction {}: price={}, bidder={}, totalBids={}",
                request.getBidId(), auctionId, request.getAmount(), request.getBidderId(), auction.getTotalBids());
        return toResponse(auction, previousWinnerId);
    }

    /** The first bid may equal the starting price (current_price starts there); later bids must add the increment. */
    static BigDecimal minimumBid(AuctionItem auction) {
        return auction.getTotalBids() == 0
                ? auction.getCurrentPrice()
                : auction.getCurrentPrice().add(auction.getBidIncrement());
    }

    private static ApplyBidResponse toResponse(AuctionItem auction, UUID previousWinnerId) {
        return ApplyBidResponse.builder()
                .auctionId(auction.getId())
                .currentPrice(auction.getCurrentPrice())
                .currentWinnerId(auction.getCurrentWinnerId())
                .previousWinnerId(previousWinnerId)
                .totalBids(auction.getTotalBids())
                .endTime(auction.getEndTime())
                .extended(false)
                .extensionCount(auction.getExtensionCount())
                .build();
    }

    private static NotFoundException notFound(UUID auctionId) {
        return new NotFoundException("Auction not found: " + auctionId, AuctionErrorCodes.AUCTION_NOT_FOUND);
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionBidServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (19 tests including the 6 parameterized cases).

---

### Task 4: `AuctionInternalController` + security

**Files:**
- Create: `src/main/java/com/bidnow/auction/controller/AuctionInternalController.java`
- Modify: `src/main/java/com/bidnow/auction/config/SecurityConfig.java`
- Test: `src/test/java/com/bidnow/auction/controller/AuctionInternalControllerTest.java`

**Interfaces:**
- Consumes: `AuctionBidService.getBidContext`, `AuctionBidService.applyBid` (Task 3), `AuctionExceptionHandler` (Task 2).
- Produces:
  - `GET /api/v1/internal/auctions/{id}/bid-context` → `BaseResponse<BidContextResponse>`
  - `POST /api/v1/internal/auctions/{id}/bids` → `BaseResponse<ApplyBidResponse>`

- [ ] **Step 1: Write the failing controller test**

`src/test/java/com/bidnow/auction/controller/AuctionInternalControllerTest.java`:

```java
package com.bidnow.auction.controller;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.exception.AuctionExceptionHandler;
import com.bidnow.auction.exception.BidTooLowException;
import com.bidnow.auction.exception.ConflictException;
import com.bidnow.auction.service.AuctionBidService;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionInternalControllerTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final String BASE = "/api/v1/internal/auctions/" + AUCTION_ID;

    @Mock
    private AuctionBidService auctionBidService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionInternalController(auctionBidService))
                .setControllerAdvice(new AuctionExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private static String bidJson(String bidId, String bidderId, String amount) {
        return """
                {"bidId": %s, "bidderId": %s, "amount": %s}
                """.formatted(bidId, bidderId, amount);
    }

    private static String validBidJson() {
        return bidJson("\"" + UUID.randomUUID() + "\"", "\"" + UUID.randomUUID() + "\"", "105.00");
    }

    @Test
    void getBidContext_returns200WithContext() throws Exception {
        when(auctionBidService.getBidContext(AUCTION_ID)).thenReturn(BidContextResponse.builder()
                .auctionId(AUCTION_ID).title("Watch").status(AuctionStatus.ACTIVE)
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .totalBids(2).endTime(OffsetDateTime.parse("2026-10-01T12:00:00Z")).build());

        mockMvc.perform(get(BASE + "/bid-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalBids").value(2));
    }

    @Test
    void getBidContext_unknownAuction_returns404() throws Exception {
        when(auctionBidService.getBidContext(AUCTION_ID))
                .thenThrow(new NotFoundException("Auction not found", AuctionErrorCodes.AUCTION_NOT_FOUND));

        mockMvc.perform(get(BASE + "/bid-context"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.AUCTION_NOT_FOUND));
    }

    @Test
    void applyBid_returns200WithNewState() throws Exception {
        UUID winner = UUID.randomUUID();
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class))).thenReturn(ApplyBidResponse.builder()
                .auctionId(AUCTION_ID).currentPrice(new BigDecimal("105.00")).currentWinnerId(winner)
                .totalBids(3).extended(false).build());

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPrice").value(105.00))
                .andExpect(jsonPath("$.data.currentWinnerId").value(winner.toString()))
                .andExpect(jsonPath("$.data.totalBids").value(3))
                .andExpect(jsonPath("$.data.extended").value(false));
    }

    @Test
    void applyBid_missingBidId_returns400InvalidInput() throws Exception {
        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON)
                        .content(bidJson("null", "\"" + UUID.randomUUID() + "\"", "105.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.bidId").exists());
        verifyNoInteractions(auctionBidService);
    }

    @Test
    void applyBid_nonPositiveAmount_returns400InvalidInput() throws Exception {
        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON)
                        .content(bidJson("\"" + UUID.randomUUID() + "\"", "\"" + UUID.randomUUID() + "\"", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(auctionBidService);
    }

    @Test
    void applyBid_tooLow_returns400WithMinimumBid() throws Exception {
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class)))
                .thenThrow(new BidTooLowException(new BigDecimal("110.00")));

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.BID_TOO_LOW))
                .andExpect(jsonPath("$.errors.minimumBid").value("110.00"));
    }

    @Test
    void applyBid_ownAuction_returns403() throws Exception {
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class)))
                .thenThrow(new ForbiddenException("own", AuctionErrorCodes.BID_OWN_AUCTION));

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.BID_OWN_AUCTION));
    }

    @Test
    void applyBid_auctionClosed_returns409() throws Exception {
        when(auctionBidService.applyBid(eq(AUCTION_ID), any(ApplyBidRequest.class)))
                .thenThrow(new ConflictException("closed", AuctionErrorCodes.AUCTION_NOT_OPEN));

        mockMvc.perform(post(BASE + "/bids").contentType(MediaType.APPLICATION_JSON).content(validBidJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(AuctionErrorCodes.AUCTION_NOT_OPEN));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionInternalControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`AuctionInternalController` not found).

- [ ] **Step 3: Implement the controller**

`src/main/java/com/bidnow/auction/controller/AuctionInternalController.java`:

```java
package com.bidnow.auction.controller;

import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.service.AuctionBidService;
import com.bidnow.common.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/auctions")
@RequiredArgsConstructor
@Tag(name = "Internal Auction Interface", description = "Service-to-service bid context and atomic bid application")
public class AuctionInternalController {

    private final AuctionBidService auctionBidService;

    @Operation(summary = "Get bid context (Internal)",
            description = "Price, increment, deposit, status and end time used by bidding-service to pre-validate bids.")
    @GetMapping("/{id}/bid-context")
    public ResponseEntity<BaseResponse<BidContextResponse>> getBidContext(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(auctionBidService.getBidContext(id)));
    }

    @Operation(summary = "Apply bid (Internal)",
            description = "Atomically re-validates and applies a bid under a row lock. Idempotent on bidId.")
    @PostMapping("/{id}/bids")
    public ResponseEntity<BaseResponse<ApplyBidResponse>> applyBid(@PathVariable UUID id,
                                                                   @Valid @RequestBody ApplyBidRequest request) {
        return ResponseEntity.ok(BaseResponse.success(auctionBidService.applyBid(id, request)));
    }
}
```

- [ ] **Step 4: Permit the internal paths in `SecurityConfig`**

In `SecurityConfig.securityFilterChain`, after the `.requestMatchers("/demo/**").permitAll()` line, add:

```java
                        .requestMatchers("/api/v1/internal/**")
                        .permitAll()
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -q -pl auction-service -am test -Dtest=AuctionInternalControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests).

---

### Task 5: Serialize closure, cancel and force-close on the row lock

**Files:**
- Modify: `src/main/java/com/bidnow/auction/service/AuctionClosureService.java`
- Modify: `src/main/java/com/bidnow/auction/service/impl/AuctionServiceImpl.java`
- Modify: `src/main/java/com/bidnow/auction/service/impl/AdminAuctionServiceImpl.java`
- Modify: `src/test/java/com/bidnow/auction/service/AuctionClosureServiceTest.java`
- Modify: `src/test/java/com/bidnow/auction/service/impl/AdminAuctionServiceImplTest.java`
- Modify: `src/test/java/com/bidnow/auction/service/impl/AuctionServiceImplTest.java`

**Interfaces:**
- Consumes: `AuctionItemRepository.findByIdForUpdate` (Task 1).
- Produces: no new API. Behaviour change: these four operations block while a bid is being applied to the same auction, and the reverse holds too.

The tests use Mockito strict stubs. Once production code calls `findByIdForUpdate`, any test still stubbing `findByIdAndDeletedAtIsNull` fails with `UnnecessaryStubbingException` or NPE/empty-Optional behaviour. That failure is the red step here.

- [ ] **Step 1: Point the tests at the locking read**

`AuctionClosureServiceTest.java`: replace every `findByIdAndDeletedAtIsNull(` with `findByIdForUpdate(` (four stubs). Then add this test at the end of the class:

```java
    @Test
    void close_readsAuctionWithRowLock() {
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(auctionId)
                .status(AuctionStatus.ACTIVE)
                .totalBids(0)
                .title("Test Auction")
                .sellerId(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .build();
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        closureService.close(auctionId);

        verify(auctionItemRepository, never()).findByIdAndDeletedAtIsNull(any());
    }
```

`AdminAuctionServiceImplTest.java`: in these six tests only, replace the stub `auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())` with `auctionItemRepository.findByIdForUpdate(item.getId())`:
`cancelAuction_happyPath_transitionsToCancelledAndPublishesEvent`, `cancelAuction_notActive_throwsBadRequest`, `cancelAuction_blankReason_throwsBadRequest`, `forceCloseAuction_happyPath_setsWinnerAndPublishesEvent`, `forceCloseAuction_notActive_throwsBadRequest`, `forceCloseAuction_zeroBids_throwsBadRequest`. Leave the `getAuctionDetail_*` and `rejectAuction_*` tests unchanged, because those paths are not locked.

`AuctionServiceImplTest.java`: add this test (and add `import static org.mockito.Mockito.never;` / `verify` / `ArgumentMatchers.any` if not already imported):

```java
    @Test
    void cancelAuction_activeAuction_readsWithRowLockAndCancels() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        AuctionItem item = buildItem(auctionId);
        item.setSellerId(sellerId);
        item.setStatus(AuctionStatus.ACTIVE);
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(item));

        auctionService.cancelAuction(sellerId, auctionId, null);

        assertThat(item.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        verify(auctionItemRepository, never()).findByIdAndDeletedAtIsNull(any());
    }
```

- [ ] **Step 2: Run the three test classes and confirm they fail**

Run: `mvn -q -pl auction-service -am test -Dtest='AuctionClosureServiceTest,AdminAuctionServiceImplTest,AuctionServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The closure, cancel and force-close tests fail (strict-stubbing / not-found), and `cancelAuction_activeAuction_readsWithRowLockAndCancels` fails.

- [ ] **Step 3: Switch production code to the locking read**

`AuctionClosureService.close`, first statement:

```java
        AuctionItem auction = auctionItemRepository.findByIdForUpdate(auctionId)
                .orElse(null);
```

`AuctionServiceImpl.cancelAuction` (seller), first statement:

```java
        AuctionItem auction = auctionItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));
```

`AdminAuctionServiceImpl`: add a helper next to the existing `findById`:

```java
    /** Locking read for writes that race with bid application (cancel, force-close). */
    private AuctionItem findByIdForUpdate(UUID id) {
        return auctionItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));
    }
```

In `cancelAuction` and `forceCloseAuction`, change `AuctionItem auction = findById(id);` to `AuctionItem auction = findByIdForUpdate(id);`.

- [ ] **Step 4: Run the full auction-service unit suite**

Run: `mvn -q -pl auction-service -am test`
Expected: BUILD SUCCESS, with all tests green, including the new ones from Tasks 2–5.

---

### Task 6: BDD — endpoint behaviour and concurrency against real Postgres

**Files:**
- Modify: `src/test/resources/db/changelog/test-data.sql`
- Create: `src/test/resources/features/internal-bid-api.feature`
- Create: `src/test/java/com/bidnow/auction/bdd/steps/InternalBidSteps.java`

**Interfaces:**
- Consumes: the endpoints from Task 4, `AuctionClosureService.close` (Task 5), and the `bdd-support` beans `BddRestClient`, `ScenarioContext`, plus Spring's `JdbcTemplate`.
- Produces: none.

Seed auctions used **only** by this feature, so other features' state is unaffected:
- `…005` is open: start 500, increment 50, no bids.
- `…006` is ACTIVE but its end time has passed.
- `…007` is used for the same-price race: current 1000, increment 10, 1 bid.
- `…008` is used for the close-vs-bid race: current 100, increment 1, 1 bid.

- [ ] **Step 1: Add the seed changeset**

Append to `src/test/resources/db/changelog/test-data.sql`:

```sql

-- changeset bidnow:bdd-bid-auctions
-- comment: Auctions reserved for internal-bid-api.feature (Epic #120 Story 1)
INSERT INTO auction_items (id, seller_id, title, description, category_id,
                           starting_price, bid_increment, deposit_amount,
                           current_price, total_bids, current_winner_id,
                           status, start_time, end_time, original_end_time,
                           created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000005'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Open Auction', 'Open auction for bid BDD',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        500.00, 50.00, 100.00, 500.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000006'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Expired Auction', 'Active auction whose end time has passed',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        200.00, 20.00, 40.00, 200.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 minute', NOW() - INTERVAL '1 minute',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000007'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Same-Price Race Auction', 'Concurrent same-price bids',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        900.00, 10.00, 100.00, 1000.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000008'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Close Race Auction', 'Closure racing concurrent bids',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        90.00, 1.00, 10.00, 100.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()) ON CONFLICT (id) DO NOTHING;
```

- [ ] **Step 2: Write the feature file**

`src/test/resources/features/internal-bid-api.feature`:

```gherkin
@auction @internal-bid @regression
Feature: Internal bid API used by bidding-service

  @smoke
  Scenario: Bid context of an open auction
    When bidding-service requests the bid context for auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 200
    And the response field "data.status" should equal "ACTIVE"
    And the response field "data.totalBids" should equal "0"
    And the response field "data.title" should equal "BDD Bid Open Auction"

  @negative
  Scenario: Bid context of an unknown auction
    When bidding-service requests the bid context for auction "b0000000-0000-0000-0000-0000000000ff"
    Then the response status should be 404
    And the response field "errorCode" should equal "AUCTION_NOT_FOUND"

  Scenario: First bid at the starting price, then increment enforcement
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "500.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 200
    And the response field "data.totalBids" should equal "1"
    When bidder "550e8400-e29b-41d4-a716-446655440011" bids "540.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 400
    And the response field "errorCode" should equal "BID_TOO_LOW"
    And the response field "errors.minimumBid" should equal "550.00"
    When bidder "550e8400-e29b-41d4-a716-446655440011" bids "550.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 200
    And the response field "data.previousWinnerId" should equal "550e8400-e29b-41d4-a716-446655440010"
    And the response field "data.currentWinnerId" should equal "550e8400-e29b-41d4-a716-446655440011"

  @negative
  Scenario: Seller cannot bid on their own auction
    When bidder "550e8400-e29b-41d4-a716-446655440001" bids "99999.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 403
    And the response field "errorCode" should equal "BID_OWN_AUCTION"

  @negative
  Scenario: Bid after the end time is rejected
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "500.00" on auction "b0000000-0000-0000-0000-000000000006"
    Then the response status should be 409
    And the response field "errorCode" should equal "AUCTION_NOT_OPEN"

  @concurrency
  Scenario: Concurrent bids at the same price — exactly one is accepted
    When 5 bidders concurrently bid "1010.00" on auction "b0000000-0000-0000-0000-000000000007"
    Then exactly 1 of the concurrent bids should succeed
    And the remaining concurrent bids should be rejected with status 400
    And auction "b0000000-0000-0000-0000-000000000007" should have 2 total bids and current price "1010.00"

  @concurrency
  Scenario: Closure racing concurrent bids never loses the winner
    When auction "b0000000-0000-0000-0000-000000000008" is closed while 5 bidders bid concurrently
    Then auction "b0000000-0000-0000-0000-000000000008" should be COMPLETED with winner equal to its current winner
    And auction "b0000000-0000-0000-0000-000000000008" total bids should equal 1 plus the accepted concurrent bids
```

- [ ] **Step 3: Write the step definitions**

`src/test/java/com/bidnow/auction/bdd/steps/InternalBidSteps.java`:

```java
package com.bidnow.auction.bdd.steps;

import com.bidnow.auction.service.AuctionClosureService;
import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class InternalBidSteps {

    private static final String BASE = "/api/v1/internal/auctions/";

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;
    private final AuctionClosureService closureService;

    /** HTTP status codes of the concurrent bids fired by the last concurrency step (glue is scenario-scoped). */
    private final List<Integer> concurrentStatuses = new ArrayList<>();

    @When("bidding-service requests the bid context for auction {string}")
    public void requestBidContext(String auctionId) {
        ctx.setLastResponse(client.given().get(BASE + auctionId + "/bid-context"));
    }

    @When("bidder {string} bids {string} on auction {string}")
    public void bid(String bidderId, String amount, String auctionId) {
        ctx.setLastResponse(client.given()
                .body(bidBody(bidderId, amount))
                .post(BASE + auctionId + "/bids"));
    }

    @When("{int} bidders concurrently bid {string} on auction {string}")
    public void concurrentSamePriceBids(int bidders, String amount, String auctionId) throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < bidders; i++) {
            String bidderId = UUID.randomUUID().toString();
            tasks.add(() -> client.given().body(bidBody(bidderId, amount))
                    .post(BASE + auctionId + "/bids").statusCode());
        }
        concurrentStatuses.addAll(runConcurrently(tasks));
    }

    @When("auction {string} is closed while {int} bidders bid concurrently")
    public void closeWhileBidding(String auctionId, int bidders) throws Exception {
        UUID id = UUID.fromString(auctionId);
        BigDecimal start = currentPrice(auctionId);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 1; i <= bidders; i++) {
            String bidderId = UUID.randomUUID().toString();
            String amount = start.add(BigDecimal.valueOf(i)).toPlainString();
            tasks.add(() -> client.given().body(bidBody(bidderId, amount))
                    .post(BASE + auctionId + "/bids").statusCode());
        }
        tasks.add(() -> {
            closureService.close(id);
            return -1; // marker for the closure task, excluded from bid statuses
        });
        runConcurrently(tasks).stream().filter(s -> s != -1).forEach(concurrentStatuses::add);
    }

    @Then("exactly {int} of the concurrent bids should succeed")
    public void exactlyNSucceed(int expected) {
        assertThat(concurrentStatuses.stream().filter(s -> s == 200).count())
                .as("statuses: %s", concurrentStatuses).isEqualTo(expected);
    }

    @Then("the remaining concurrent bids should be rejected with status {int}")
    public void remainingRejected(int status) {
        assertThat(concurrentStatuses.stream().filter(s -> s != 200))
                .as("statuses: %s", concurrentStatuses).allMatch(s -> s == status);
    }

    @Then("auction {string} should have {int} total bids and current price {string}")
    public void auctionState(String auctionId, int totalBids, String price) {
        Map<String, Object> row = row(auctionId);
        assertThat(((Number) row.get("total_bids")).intValue()).isEqualTo(totalBids);
        assertThat((BigDecimal) row.get("current_price")).isEqualByComparingTo(price);
    }

    @Then("auction {string} should be COMPLETED with winner equal to its current winner")
    public void completedWithConsistentWinner(String auctionId) {
        Map<String, Object> row = row(auctionId);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("winner_id")).isNotNull().isEqualTo(row.get("current_winner_id"));
    }

    @Then("auction {string} total bids should equal {int} plus the accepted concurrent bids")
    public void totalBidsMatchAccepted(String auctionId, int initial) {
        long accepted = concurrentStatuses.stream().filter(s -> s == 200).count();
        assertThat(concurrentStatuses.stream().filter(s -> s != 200))
                .as("a bid after closure must be 409, statuses: %s", concurrentStatuses)
                .allMatch(s -> s == 409 || s == 400);
        assertThat(((Number) row(auctionId).get("total_bids")).intValue()).isEqualTo(initial + (int) accepted);
    }

    private static Map<String, Object> bidBody(String bidderId, String amount) {
        return Map.of("bidId", UUID.randomUUID().toString(), "bidderId", bidderId, "amount", new BigDecimal(amount));
    }

    private Map<String, Object> row(String auctionId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, winner_id, current_winner_id, total_bids, current_price FROM auction_items WHERE id = ?::uuid",
                auctionId);
    }

    private BigDecimal currentPrice(String auctionId) {
        return (BigDecimal) row(auctionId).get("current_price");
    }

    /** Releases all tasks at once through a latch to maximize overlap, then waits for every result. */
    private static List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
```

- [ ] **Step 4: Run the BDD suite (requires Docker)**

Run: `mvn -q -pl auction-service -am test -Pbdd`
Expected: all scenarios pass, including the existing features. To see this suite protect against the race, temporarily change `AuctionBidService.applyBid` to `findByIdAndDeletedAtIsNull`. The same-price scenario should then fail (more than one 200). Revert the change afterwards.

---

### Task 7: Documentation

**Files:**
- Modify: `docs/architecture.md` (repo root)

- [ ] **Step 1: Add the auction rows to the internal API table**

In `docs/architecture.md`, section "Service-to-Service Internal APIs", insert these rows directly under the table header separator (before the first `Wallet` row):

```markdown
| Auction | `GET /api/v1/internal/auctions/{id}/bid-context` | Bidding | Price, increment, deposit, status, seller, winner, total bids and end time for bid pre-validation. Errors: `AUCTION_NOT_FOUND` 404 |
| Auction | `POST /api/v1/internal/auctions/{id}/bids` `{bidId, bidderId, amount}` | Bidding | Authoritative, row-locked bid application (`SELECT … FOR UPDATE`, serialized with closure/cancel). Idempotent on `bidId`. First bid ≥ current price, later bids ≥ current price + increment. Errors: `BID_TOO_LOW` 400 (`errors.minimumBid`), `BID_OWN_AUCTION` 403, `AUCTION_NOT_FOUND` 404, `AUCTION_NOT_OPEN` 409 |
```

- [ ] **Step 2: Tick Story 1 in the roadmap**

In `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`, mark tasks 1.1–1.7 of Story 1 as `[x]`.

- [ ] **Step 3: Final verification**

Run: `mvn -q -pl auction-service -am test` and then `mvn -q -pl auction-service -am test -Pbdd`
Expected: both BUILD SUCCESS. Leave all changes uncommitted.

---

## Self-review notes

- **Spec coverage (roadmap Story 1 tasks):**

  | Roadmap task | Covered by |
  |---|---|
  | 1.1 persistence | Task 1 |
  | 1.2 getBidContext | Task 3 |
  | 1.3 applyBid validation | Task 3 |
  | 1.4 controller | Task 4 |
  | 1.5 closure/cancel locks | Task 5, plus force-close, which also races bids |
  | 1.6 concurrency | Task 6 |
  | 1.7 full suite | Task 7, Step 3 |

- **Deliberately not here:** anti-snipe extension, `AuctionExtendedEvent` and closure rescheduling (Story 4); bidding-service Feign clients (Stories 2–3).
- **Known behaviour change:** closure, cancel and force-close now wait for any in-flight `applyBid` on the same row. Lock hold time is normally a single-row update. However, in-transaction Kafka publishes in the cancel and force-close paths can extend the hold while the broker is down. `applyBid` lock waits are bounded to 1 s. Moving those publishes to `afterCommit` is tracked in the roadmap (Story 3, task 3.0).
