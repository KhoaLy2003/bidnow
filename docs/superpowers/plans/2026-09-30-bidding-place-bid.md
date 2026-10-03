# Story 3 — BID-102: Place Bid End-to-End Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `POST /api/v1/bids` actually place a bid. It pre-validates, locks the bidder's deposit in wallet-service on their first bid, inserts the bid, and applies it atomically in auction-service. It then updates the cached auction context, publishes `BidPlacedEvent`, and returns `201 Created`. It also evicts cached contexts when auctions end or are cancelled, and moves auction-service's in-transaction Kafka publishes to after-commit so they never run while holding the auction row lock.

**Architecture:**
- **Orchestration.** `BidService` orchestrates the flow in spec §3:
  - `preValidateWithRefresh` (moved from the controller)
  - `DepositGate.ensureLocked`: a Redis flag, else wallet-service's idempotent `POST deposit-lock`
  - then, inside a `TransactionTemplate`: insert the `Bid`, then `AuctionBidGateway.apply` (auction-service's row-locked apply-bid)
  - after commit: refresh the cached context, resolve the bidder's display name, publish the event
- **Downstream error mapping.** Both gateways turn Feign failures into bidding exceptions via `DownstreamError`, which parses the common `ErrorResponse` body.
- **Cache eviction.** A Kafka consumer evicts the context cache on `auction-ended-topic` and `auction-cancelled-topic`.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.0 (OpenFeign 13), Spring Data JPA, Spring Data Redis, Spring Kafka, Lombok, JUnit 5, Mockito, AssertJ, standalone MockMvc, Cucumber + Testcontainers (Postgres, Redis, Kafka) + WireMock.

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (§3 flow, §4 contracts and error codes, §5 Redis keys, §7 failure modes). Roadmap: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (Story 3, including 3.0 and 3.0a). Upstream contracts:
- auction-service: `ApplyBidRequest` / `ApplyBidResponse` in `backend/auction-service/src/main/java/com/bidnow/auction/dto/`.
- wallet-service: `WalletInternalController`, `DepositLockRequest`, `DepositLockResponse` and `WalletErrorCodes` in `backend/wallet-service/src/main/java/com/bidnow/wallet/`.
- user-service: `GET /api/v1/users/internal/{userId}/summary` → `BaseResponse<UserSummaryResponse>` (common).

## Global Constraints

- **Scope:**
  - Task 1: auction-service only.
  - Task 4: `common`, one additive change to `BidPlacedEvent`.
  - Tasks 2–8: bidding-service.
  - Task 9: docs.
  - Do not touch wallet-service, user-service, media-service or the gateway.
- **Flow order (spec §3):** pre-validate (refresh once on `AUCTION_NOT_OPEN`) → deposit gate → **transaction** { insert bid → apply-bid } → after commit { cache put → display name → publish }. The deposit lock is never inside the DB transaction.
- **`bidId`** is `UUID.randomUUID()`, generated once per request. It is the `Bid` primary key and the `bidId` sent to auction-service. bidding-service never retries apply-bid.
- **Error mapping** (the HTTP response bidding-service returns):

| Source | Downstream | bidding-service response |
|---|---|---|
| wallet | 400 `INSUFFICIENT_BALANCE` | **403 `BID_INSUFFICIENT_BALANCE`**, `errors` copied (`availableBalance`, `required`) |
| wallet | 403 `WALLET_NOT_ACTIVE` | 403 `WALLET_NOT_ACTIVE` |
| wallet | 404 `WALLET_NOT_FOUND` | 403 `WALLET_NOT_FOUND` |
| wallet | 409 `DEPOSIT_LOCK_CLOSED` | 409 `DEPOSIT_LOCK_CLOSED` |
| wallet | anything else (5xx, timeout, unknown code) | 503 `SERVICE_UNAVAILABLE` |
| auction apply-bid | 409 `AUCTION_NOT_OPEN` | 409 `AUCTION_NOT_OPEN` + evict context |
| auction apply-bid | 400 `BID_TOO_LOW` | 400 `BID_TOO_LOW`, `errors.minimumBid` from downstream + evict context |
| auction apply-bid | 403 `BID_OWN_AUCTION` | 403 `BID_OWN_AUCTION` |
| auction apply-bid | 404 | 404 `AUCTION_NOT_FOUND` |
| auction apply-bid | anything else | 503 `SERVICE_UNAVAILABLE` |

- **Redis keys:**
  - `bidding:deposit:{auctionId}:{userId}` = `"1"`, TTL = (auction `endTime` + 24h − now), minimum 60 s.
  - `bidding:user:{userId}:summary` = JSON `UserSummaryResponse`, TTL `bidding.cache.user-summary-ttl-seconds` (600).
  - Redis failures never fail a request. A missing flag means "call wallet"; a missing summary means "call user-service".
- **Display name:** `"Unknown bidder"` when user-service fails or returns no name. Never fail a bid over it.
- **Kafka:**
  - Topic `bid-placed-topic`, key = auctionId, `JsonSerializer`.
  - Producer `max.block.ms: 2000`.
  - A publish failure after commit is logged at ERROR with the prefix `CRITICAL:` and never fails the request.
  - Consumer group `bidding-service-group`.
- **Response:**
  - `201` with `BaseResponse<PlaceBidResponse>` (`status: 201`, `message: "Bid placed"`).
  - `placedAt` / `bidTime` come from the injected `Clock`: `OffsetDateTime.now(clock)` for the response, `LocalDateTime.now(clock)` for the event.
- **auction-service Task 1:** event publishes in seller cancel, admin cancel, admin force-close and activation run in an after-commit hook. Behaviour is otherwise unchanged.
- **Do not run `git commit` or `git add`.** Leave all changes uncommitted on branch `feature/bidding`.
- **Maven** (run from `backend/`):
  - Unit tests: `mvn -q -pl <module> -am test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`
  - Full: `mvn -q -pl <module> -am test`
  - BDD: `-Pbdd` (needs Docker).

---

## File Map

Paths are relative to `backend/` unless noted. (A) is auction-service, (C) is common, (B) is bidding-service.

| File | Action | Responsibility |
|---|---|---|
| (A) `auction-service/src/main/java/com/bidnow/auction/util/AfterCommit.java` | Create | Register an after-commit action |
| (A) `.../service/impl/AuctionServiceImpl.java` | Modify | Seller cancel publish → after commit |
| (A) `.../service/impl/AdminAuctionServiceImpl.java` | Modify | Admin cancel / force-close publish → after commit |
| (A) `.../service/AuctionActivationService.java` | Modify | Activation publish → after commit |
| (A) tests: `AuctionServiceImplTest`, `AdminAuctionServiceImplTest`, `AuctionActivationServiceTest` | Modify | Transaction sync + after-commit assertions |
| (B) `bidding-service/.../constant/BiddingErrorCodes.java` | Modify | Wallet-related codes |
| (B) `.../exception/InsufficientBalanceException.java` | Create | 403 carrying wallet details |
| (B) `.../exception/BiddingExceptionHandler.java` | Modify | Render `InsufficientBalanceException.errors` |
| (B) `.../feign/DownstreamError.java` | Create | Parse downstream `ErrorResponse` from a `FeignException` |
| (B) `.../dto/DepositLockCommand.java`, `.../dto/ApplyBidCommand.java`, `.../dto/ApplyBidResult.java` | Create | Feign payloads |
| (B) `.../feign/WalletServiceClient.java`, `.../feign/UserServiceClient.java` | Create | Feign clients |
| (B) `.../feign/AuctionServiceClient.java` | Modify | Add `applyBid` |
| (B) `.../service/DepositGate.java` | Create | Redis flag + wallet deposit lock |
| (B) `.../service/AuctionBidGateway.java` | Create | apply-bid call + error mapping |
| (B) `.../service/UserSummaryCacheService.java` | Create | Cached display-name lookup |
| (C) `common/src/main/java/com/bidnow/common/dto/event/BidPlacedEvent.java` | Modify | Add `bidId`, `totalBids`, `endTime` |
| (B) `.../kafka/BidEventPublisher.java` | Create | Publish `BidPlacedEvent` |
| (B) `.../dto/BidContext.java` | Modify | `@Builder(toBuilder = true)` |
| (B) `.../dto/response/PlaceBidResponse.java` | Create | 201 body |
| (B) `.../service/BidService.java` | Create | Orchestration |
| (B) `.../controller/BidController.java` | Modify | Delegate to `BidService`, 201 |
| (B) `.../kafka/AuctionLifecycleConsumer.java`, `.../config/KafkaConsumerConfig.java` | Create | Evict on ended/cancelled; retry + DLT |
| (B) `src/main/resources/application.yml` | Modify | Kafka + user-summary TTL |
| (B) tests: see each task | Create/Modify | — |
| (B) BDD: `CucumberSpringConfig`, `Hooks` (new), `BidPlacementSteps`, `CommonSteps`, `bidding-core.feature`, `place-bid.feature` (new) | Modify/Create | End-to-end |
| repo root `docs/architecture.md`, `docs/database/bidding-service-schema.md`, spec, roadmap | Modify | Docs |

---

### Task 1: auction-service — publish lifecycle events only after commit

**Files:**
- Create: `auction-service/src/main/java/com/bidnow/auction/util/AfterCommit.java`
- Modify: `auction-service/src/main/java/com/bidnow/auction/service/impl/AuctionServiceImpl.java` (`cancelAuction`)
- Modify: `auction-service/src/main/java/com/bidnow/auction/service/impl/AdminAuctionServiceImpl.java` (`cancelAuction`, `forceCloseAuction`)
- Modify: `auction-service/src/main/java/com/bidnow/auction/service/AuctionActivationService.java` (`activate`)
- Test: `auction-service/src/test/java/com/bidnow/auction/service/impl/AdminAuctionServiceImplTest.java`
- Test: `auction-service/src/test/java/com/bidnow/auction/service/impl/AuctionServiceImplTest.java`
- Test: `auction-service/src/test/java/com/bidnow/auction/service/AuctionActivationServiceTest.java`

**Interfaces:**
- Produces: `AfterCommit.run(Runnable action)`. It registers `action` to run in `afterCommit()`. It throws `IllegalStateException`, which comes from Spring, when no transaction synchronization is active.

**Why:** these four methods hold the `auction_items` row lock (Story 1). A synchronous `KafkaTemplate.send` can block for up to 60 s when the broker is down, which stalls every `applyBid` on that auction. `AuctionClosureService.close` already publishes after commit; this task makes the other four do the same.

- [ ] **Step 1: Make the tests require after-commit publishing**

In **each** of the three test classes, add these imports:

```java
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

Also add these members inside each class, directly after the `@InjectMocks` field:

```java
    @BeforeEach
    void initTransactionSync() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }
```

If a class already has an `@BeforeEach` method, keep it. Add the sync init as a separate method with a distinct name, as above.

`AdminAuctionServiceImplTest`:
- In `cancelAuction_happyPath_transitionsToCancelledAndPublishesEvent`, insert these lines immediately **before** the `ArgumentCaptor<AuctionCancelledEvent>` line:

```java
        org.mockito.Mockito.verify(auctionKafkaProducer, never()).publishAuctionCancelled(any());
        triggerAfterCommit();
```

- In `forceCloseAuction_happyPath_setsWinnerAndPublishesEvent`, insert immediately **before** its `ArgumentCaptor<AuctionEndedEvent>` line:

```java
        org.mockito.Mockito.verify(auctionKafkaProducer, never()).publishAuctionEnded(any());
        triggerAfterCommit();
```

`AuctionServiceImplTest`: add this test. It needs no extra imports; `ArgumentCaptor` is already imported by the class.

```java
    @Test
    void cancelAuction_activeAuction_publishesCancelledEventOnlyAfterCommit() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        AuctionItem item = buildItem(auctionId);
        item.setSellerId(sellerId);
        item.setStatus(AuctionStatus.ACTIVE);
        when(auctionItemRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(item));

        auctionService.cancelAuction(sellerId, auctionId, null);

        verify(auctionKafkaProducer, never()).publishAuctionCancelled(any());
        triggerAfterCommit();
        verify(auctionKafkaProducer).publishAuctionCancelled(any());
    }
```

`AuctionActivationServiceTest`: add this test (`never`, `verify` and `any` are already imported there):

```java
    @Test
    void activate_publishesCreatedEventOnlyAfterCommit() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = scheduled(id);
        when(auctionItemRepository.findByIdForUpdate(id)).thenReturn(Optional.of(auction));

        activationService.activate(id);

        verify(kafkaProducer, never()).publishAuctionCreated(any());
        triggerAfterCommit();
        verify(kafkaProducer).publishAuctionCreated(any());
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl auction-service -am test -Dtest='AdminAuctionServiceImplTest,AuctionServiceImplTest,AuctionActivationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The `verify(..., never())` checks fail because the events are still published inside the transaction.

- [ ] **Step 3: Create `AfterCommit`**

`auction-service/src/main/java/com/bidnow/auction/util/AfterCommit.java`:

```java
package com.bidnow.auction.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers side effects (Kafka publishes) until the surrounding transaction commits, so they never run
 * while the auction row lock is held and never fire for a rolled-back change.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * @throws IllegalStateException if no transaction synchronization is active
     */
    public static void run(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
```

- [ ] **Step 4: Defer the four publishes**

In each case, build the event into a local variable, unchanged from today, and pass the publish to `AfterCommit.run`. Add `import com.bidnow.auction.util.AfterCommit;` to each file.

**`AuctionServiceImpl.cancelAuction`.** Replace the `if (oldStatus == AuctionStatus.ACTIVE || oldStatus == AuctionStatus.SCHEDULED) { auctionKafkaProducer.publishAuctionCancelled(...); }` block with:

```java
        if (oldStatus == AuctionStatus.ACTIVE || oldStatus == AuctionStatus.SCHEDULED) {
            AuctionCancelledEvent event = AuctionCancelledEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .auctionTitle(auction.getTitle())
                    .previousStatus(oldStatus.name())
                    .reason(reason)
                    .cancelledAt(now.toInstant())
                    .build();
            AfterCommit.run(() -> auctionKafkaProducer.publishAuctionCancelled(event));
        }
```

**`AdminAuctionServiceImpl.cancelAuction`.** Replace the `auctionKafkaProducer.publishAuctionCancelled(AuctionCancelledEvent.builder()...build());` statement with:

```java
        AuctionCancelledEvent cancelledEvent = AuctionCancelledEvent.builder()
                .auctionId(auction.getId())
                .sellerId(auction.getSellerId())
                .auctionTitle(auction.getTitle())
                .previousStatus(oldStatus.name())
                .reason(reason)
                .cancelledAt(now.toInstant())
                .build();
        AfterCommit.run(() -> auctionKafkaProducer.publishAuctionCancelled(cancelledEvent));
```

**`AdminAuctionServiceImpl.forceCloseAuction`.** Replace the `auctionKafkaProducer.publishAuctionEnded(AuctionEndedEvent.builder()...build());` statement with:

```java
        AuctionEndedEvent endedEvent = AuctionEndedEvent.builder()
                .auctionId(auction.getId())
                .auctionTitle(auction.getTitle())
                .sellerId(auction.getSellerId())
                .winnerId(auction.getWinnerId())
                .winningBidAmount(auction.getCurrentPrice())
                .totalBids(auction.getTotalBids())
                .endedAt(now.toInstant())
                .closureSource("ADMIN")
                .build();
        AfterCommit.run(() -> auctionKafkaProducer.publishAuctionEnded(endedEvent));
```

**`AuctionActivationService.activate`.** Replace the `kafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()...build());` statement with:

```java
        AuctionCreatedEvent createdEvent = AuctionCreatedEvent.builder()
                .auctionId(auction.getId())
                .sellerId(auction.getSellerId())
                .title(auction.getTitle())
                .startingPrice(auction.getStartingPrice())
                .endTime(auction.getEndTime().toInstant())
                .build();
        AfterCommit.run(() -> kafkaProducer.publishAuctionCreated(createdEvent));
```

Before replacing each block, compare the builder fields above with the existing code. They must stay exactly as they are today. If the current code sets a field not listed here, keep it.

In `AuctionActivationService`, add `import com.bidnow.common.dto.event.AuctionCreatedEvent;` if it is not already imported. It is currently referenced by fully qualified name only in a Javadoc.

- [ ] **Step 5: Run the auction-service suite**

Run: `mvn -q -pl auction-service -am test`
Expected: BUILD SUCCESS with all tests green, including the two new ones.

---

### Task 2: Downstream error parsing, wallet error codes, `InsufficientBalanceException`

**Files:**
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/constant/BiddingErrorCodes.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/exception/InsufficientBalanceException.java`
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/exception/BiddingExceptionHandler.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/feign/DownstreamError.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/feign/DownstreamErrorTest.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/exception/BiddingExceptionHandlerTest.java` (add a test)

**Interfaces:**
- Produces:
  - Error codes `BiddingErrorCodes.{BID_INSUFFICIENT_BALANCE, WALLET_NOT_ACTIVE, WALLET_NOT_FOUND, DEPOSIT_LOCK_CLOSED}`.
  - `new InsufficientBalanceException(Map<String, String> details)`: status 403, code `BID_INSUFFICIENT_BALANCE`, `getDetails()`.
  - `record DownstreamError(int status, String errorCode, Map<String, String> errors)` with `static DownstreamError of(FeignException ex, ObjectMapper objectMapper)`.
    - `errorCode` is `null` and `errors` is empty when the body is missing or unparseable.
    - `status` is `ex.status()`, which is `-1` for connection errors and timeouts.

- [ ] **Step 1: Write the failing tests**

`bidding-service/src/test/java/com/bidnow/bidding/feign/DownstreamErrorTest.java`:

```java
package com.bidnow.bidding.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamErrorTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesErrorCodeAndErrorsFromCommonErrorResponse() {
        FeignException ex = new FeignException.BadRequest("bad", request(), body("""
                {"timestamp":"2026-10-01T05:00:00Z","status":400,"errorCode":"INSUFFICIENT_BALANCE",
                 "message":"Insufficient balance","path":"/api/v1/internal/wallet/deposit-lock",
                 "errors":{"availableBalance":"10.00","required":"20.00"}}
                """), Map.of());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.status()).isEqualTo(400);
        assertThat(error.errorCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(error.errors()).containsEntry("availableBalance", "10.00").containsEntry("required", "20.00");
    }

    @Test
    void responseWithoutErrorsMap_hasEmptyErrors() {
        FeignException ex = new FeignException.Conflict("conflict", request(),
                body("{\"status\":409,\"errorCode\":\"AUCTION_NOT_OPEN\",\"message\":\"closed\"}"), Map.of());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.errorCode()).isEqualTo("AUCTION_NOT_OPEN");
        assertThat(error.errors()).isEmpty();
    }

    @Test
    void unparseableBody_yieldsNullCode() {
        FeignException ex = new FeignException.InternalServerError("boom", request(), body("<html>oops</html>"), Map.of());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.status()).isEqualTo(500);
        assertThat(error.errorCode()).isNull();
        assertThat(error.errors()).isEmpty();
    }

    @Test
    void timeout_hasNegativeStatusAndNoCode() {
        RetryableException ex = new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.status()).isEqualTo(-1);
        assertThat(error.errorCode()).isNull();
    }
}
```

Add this test to `BiddingExceptionHandlerTest`:

```java
    @Test
    void handleInsufficientBalance_returns403WithWalletDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bids");

        ResponseEntity<ErrorResponse> response = handler.handleInsufficientBalance(
                new InsufficientBalanceException(java.util.Map.of("availableBalance", "10.00", "required", "20.00")),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(BiddingErrorCodes.BID_INSUFFICIENT_BALANCE);
        assertThat(response.getBody().getErrors())
                .containsEntry("availableBalance", "10.00")
                .containsEntry("required", "20.00");
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl bidding-service -am test -Dtest='DownstreamErrorTest,BiddingExceptionHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`DownstreamError` and `InsufficientBalanceException` don't exist yet).

- [ ] **Step 3: Implement**

In `BiddingErrorCodes`, add these constants below `SERVICE_UNAVAILABLE`:

```java
    public static final String BID_INSUFFICIENT_BALANCE = "BID_INSUFFICIENT_BALANCE";
    public static final String WALLET_NOT_ACTIVE = "WALLET_NOT_ACTIVE";
    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";
    public static final String DEPOSIT_LOCK_CLOSED = "DEPOSIT_LOCK_CLOSED";
```

`bidding-service/src/main/java/com/bidnow/bidding/exception/InsufficientBalanceException.java`:

```java
package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.exception.BaseException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** The bidder's wallet cannot cover the auction deposit. Details come from wallet-service (availableBalance, required). */
@Getter
public class InsufficientBalanceException extends BaseException {

    private final Map<String, String> details;

    public InsufficientBalanceException(Map<String, String> details) {
        super("Insufficient wallet balance to lock the auction deposit. Please top up your wallet.",
                BiddingErrorCodes.BID_INSUFFICIENT_BALANCE, HttpStatus.FORBIDDEN);
        this.details = Map.copyOf(details);
    }
}
```

In `BiddingExceptionHandler`, add:

```java
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex,
                                                                   HttpServletRequest request) {
        log.info("Bid rejected: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .errors(ex.getDetails())
                .build();
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }
```

`bidding-service/src/main/java/com/bidnow/bidding/feign/DownstreamError.java`:

```java
package com.bidnow.bidding.feign;

import com.bidnow.common.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

import java.util.Map;

/**
 * The status and common {@link ErrorResponse} fields of a failed Feign call. {@code status} is -1
 * for connection failures and timeouts; {@code errorCode} is null when the body is absent or not an
 * ErrorResponse.
 */
public record DownstreamError(int status, String errorCode, Map<String, String> errors) {

    public static DownstreamError of(FeignException ex, ObjectMapper objectMapper) {
        String body = ex.contentUTF8();
        if (body == null || body.isBlank()) {
            return new DownstreamError(ex.status(), null, Map.of());
        }
        try {
            ErrorResponse response = objectMapper.readValue(body, ErrorResponse.class);
            Map<String, String> errors = response.getErrors() == null ? Map.of() : response.getErrors();
            return new DownstreamError(ex.status(), response.getErrorCode(), errors);
        } catch (JsonProcessingException parseFailure) {
            return new DownstreamError(ex.status(), null, Map.of());
        }
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `mvn -q -pl bidding-service -am test -Dtest='DownstreamErrorTest,BiddingExceptionHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 + 4 tests).

---

### Task 3: Feign clients + `DepositGate` + `AuctionBidGateway`

**Files:**
- Create: `bidding-service/src/main/java/com/bidnow/bidding/dto/DepositLockCommand.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/dto/ApplyBidCommand.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/dto/ApplyBidResult.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/feign/WalletServiceClient.java`
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/feign/AuctionServiceClient.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/service/DepositGate.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/service/AuctionBidGateway.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/service/DepositGateTest.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/service/AuctionBidGatewayTest.java`

**Interfaces:**
- Consumes (Task 2): `DownstreamError`, `InsufficientBalanceException`, the new error codes. From Story 2: `BidContext`, `ConflictException`, `ServiceUnavailableException`, `BidTooLowException`, the `Clock` bean.
- Produces:
  - `record DepositLockCommand(UUID userId, UUID auctionId, BigDecimal depositAmount)`
  - `record ApplyBidCommand(UUID bidId, UUID bidderId, BigDecimal amount)`
  - `ApplyBidResult` (`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)`): `UUID auctionId, BigDecimal currentPrice, UUID currentWinnerId, UUID previousWinnerId, int totalBids, OffsetDateTime endTime, boolean extended, int extensionCount`
  - `WalletServiceClient.lockDeposit(DepositLockCommand): BaseResponse<Object>`
  - `AuctionServiceClient.applyBid(UUID auctionId, ApplyBidCommand): BaseResponse<ApplyBidResult>`
  - `DepositGate.ensureLocked(UUID userId, BidContext ctx): void` and `static String DepositGate.flagKey(UUID auctionId, UUID userId)`
  - `AuctionBidGateway.apply(UUID auctionId, ApplyBidCommand command): ApplyBidResult`

- [ ] **Step 1: Write the failing tests**

`bidding-service/src/test/java/com/bidnow/bidding/service/DepositGateTest.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.DepositLockCommand;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.InsufficientBalanceException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.WalletServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositGateTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String FLAG = "bidding:deposit:b0000000-0000-0000-0000-000000000005:00000000-0000-0000-0000-00000000000b";

    @Mock
    private WalletServiceClient walletServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private DepositGate gate;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        gate = new DepositGate(walletServiceClient, redisTemplate, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BidContext context(OffsetDateTime endTime) {
        return BidContext.builder()
                .auctionId(AUCTION_ID).sellerId(UUID.randomUUID()).status("ACTIVE")
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).totalBids(1).endTime(endTime)
                .build();
    }

    private static BidContext context() {
        return context(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/api/v1/internal/wallet/deposit-lock",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static byte[] error(int status, String code) {
        return ("{\"status\":" + status + ",\"errorCode\":\"" + code + "\",\"message\":\"m\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void flagKey_hasContractFormat() {
        assertThat(DepositGate.flagKey(AUCTION_ID, USER_ID)).isEqualTo(FLAG);
    }

    @Test
    void flagPresent_skipsWallet() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(true);

        gate.ensureLocked(USER_ID, context());

        verify(walletServiceClient, never()).lockDeposit(any());
    }

    @Test
    void flagAbsent_locksDepositAndSetsFlagUntil24hAfterEnd() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));

        gate.ensureLocked(USER_ID, context());

        verify(walletServiceClient).lockDeposit(new DepositLockCommand(USER_ID, AUCTION_ID, new BigDecimal("20.00")));
        verify(valueOps).set(FLAG, "1", Duration.ofHours(25));
    }

    @Test
    void flagTtl_hasOneMinuteFloor() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));

        gate.ensureLocked(USER_ID, context(OffsetDateTime.ofInstant(NOW.minusSeconds(25 * 3600), ZoneOffset.UTC)));

        verify(valueOps).set(FLAG, "1", Duration.ofMinutes(1));
    }

    @Test
    void redisReadFailure_stillLocksViaWallet() {
        when(redisTemplate.hasKey(FLAG)).thenThrow(new RedisConnectionFailureException("down"));
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));

        gate.ensureLocked(USER_ID, context());

        verify(walletServiceClient).lockDeposit(any());
    }

    @Test
    void redisWriteFailure_isSwallowed() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenReturn(BaseResponse.success(null));
        doThrow(new RedisConnectionFailureException("down")).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        gate.ensureLocked(USER_ID, context());
    }

    @Test
    void insufficientBalance_mapsTo403WithDetails_andSetsNoFlag() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(new FeignException.BadRequest("bad", request(), """
                {"status":400,"errorCode":"INSUFFICIENT_BALANCE","message":"m",
                 "errors":{"availableBalance":"10.00","required":"20.00"}}
                """.getBytes(StandardCharsets.UTF_8), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(InsufficientBalanceException.class)
                .satisfies(ex -> assertThat(((InsufficientBalanceException) ex).getDetails())
                        .containsEntry("availableBalance", "10.00").containsEntry("required", "20.00"));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void walletNotActive_mapsTo403() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.Forbidden("f", request(), error(403, "WALLET_NOT_ACTIVE"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.WALLET_NOT_ACTIVE);
    }

    @Test
    void walletNotFound_mapsTo403() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.NotFound("nf", request(), error(404, "WALLET_NOT_FOUND"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.WALLET_NOT_FOUND);
    }

    @Test
    void depositLockClosed_mapsTo409() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.Conflict("c", request(), error(409, "DEPOSIT_LOCK_CLOSED"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.DEPOSIT_LOCK_CLOSED);
    }

    @Test
    void walletServerError_mapsTo503() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.InternalServerError("boom", request(), null, Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void walletTimeout_mapsTo503() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void unexpectedWalletClientError_mapsTo503() {
        when(redisTemplate.hasKey(FLAG)).thenReturn(false);
        when(walletServiceClient.lockDeposit(any())).thenThrow(
                new FeignException.BadRequest("b", request(), error(400, "INVALID_INPUT"), Map.of()));

        assertThatThrownBy(() -> gate.ensureLocked(USER_ID, context()))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
```

`bidding-service/src/test/java/com/bidnow/bidding/service/AuctionBidGatewayTest.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidGatewayTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final ApplyBidCommand COMMAND =
            new ApplyBidCommand(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("105.00"));

    @Mock
    private AuctionServiceClient auctionServiceClient;

    private AuctionBidGateway gateway;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        gateway = new AuctionBidGateway(auctionServiceClient, objectMapper);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/api/v1/internal/auctions/x/bids",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static byte[] error(int status, String code, String errorsJson) {
        return ("{\"status\":" + status + ",\"errorCode\":\"" + code + "\",\"message\":\"m\""
                + (errorsJson == null ? "" : ",\"errors\":" + errorsJson) + "}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void success_returnsResult() {
        ApplyBidResult result = ApplyBidResult.builder().auctionId(AUCTION_ID)
                .currentPrice(new BigDecimal("105.00")).totalBids(2).build();
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenReturn(BaseResponse.success(result));

        assertThat(gateway.apply(AUCTION_ID, COMMAND)).isEqualTo(result);
    }

    @Test
    void emptyBody_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenReturn(BaseResponse.success(null));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void auctionNotOpen_mapsTo409() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.Conflict("c", request(), error(409, "AUCTION_NOT_OPEN", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void bidTooLow_mapsTo400WithDownstreamMinimum() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(new FeignException.BadRequest("b", request(),
                error(400, "BID_TOO_LOW", "{\"minimumBid\":\"110.00\"}"), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("110.00"));
    }

    @Test
    void bidTooLowWithoutMinimum_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.BadRequest("b", request(), error(400, "BID_TOO_LOW", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void ownAuction_mapsTo403() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.Forbidden("f", request(), error(403, "BID_OWN_AUCTION", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BID_OWN_AUCTION);
    }

    @Test
    void notFound_mapsTo404() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.NotFound("nf", request(), error(404, "AUCTION_NOT_FOUND", null), Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_FOUND);
    }

    @Test
    void serverErrorOrLockTimeout_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new FeignException.InternalServerError("boom", request(), null, Map.of()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void timeout_mapsTo503() {
        when(auctionServiceClient.applyBid(AUCTION_ID, COMMAND)).thenThrow(
                new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request()));

        assertThatThrownBy(() -> gateway.apply(AUCTION_ID, COMMAND)).isInstanceOf(ServiceUnavailableException.class);
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl bidding-service -am test -Dtest='DepositGateTest,AuctionBidGatewayTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: DTOs and Feign clients**

`bidding-service/src/main/java/com/bidnow/bidding/dto/DepositLockCommand.java`:

```java
package com.bidnow.bidding.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of wallet-service's internal POST /api/v1/internal/wallet/deposit-lock. */
public record DepositLockCommand(UUID userId, UUID auctionId, BigDecimal depositAmount) {
}
```

`bidding-service/src/main/java/com/bidnow/bidding/dto/ApplyBidCommand.java`:

```java
package com.bidnow.bidding.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of auction-service's internal POST /api/v1/internal/auctions/{id}/bids. */
public record ApplyBidCommand(UUID bidId, UUID bidderId, BigDecimal amount) {
}
```

`bidding-service/src/main/java/com/bidnow/bidding/dto/ApplyBidResult.java`:

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

/** auction-service's authoritative state after an applied bid (mirrors its ApplyBidResponse). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplyBidResult {
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

`bidding-service/src/main/java/com/bidnow/bidding/feign/WalletServiceClient.java`:

```java
package com.bidnow.bidding.feign;

import com.bidnow.bidding.dto.DepositLockCommand;
import com.bidnow.common.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "wallet-service")
public interface WalletServiceClient {

    /** Idempotent: a second call for the same user+auction returns the existing lock. */
    @PostMapping("/api/v1/internal/wallet/deposit-lock")
    BaseResponse<Object> lockDeposit(@RequestBody DepositLockCommand command);
}
```

In `AuctionServiceClient`, add the imports `com.bidnow.bidding.dto.ApplyBidCommand`, `com.bidnow.bidding.dto.ApplyBidResult`, `org.springframework.web.bind.annotation.PostMapping` and `org.springframework.web.bind.annotation.RequestBody`. Then add this method:

```java
    @PostMapping("/api/v1/internal/auctions/{id}/bids")
    BaseResponse<ApplyBidResult> applyBid(@PathVariable("id") UUID auctionId, @RequestBody ApplyBidCommand command);
```

- [ ] **Step 4: `DepositGate`**

`bidding-service/src/main/java/com/bidnow/bidding/service/DepositGate.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.DepositLockCommand;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.InsufficientBalanceException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.DownstreamError;
import com.bidnow.bidding.feign.WalletServiceClient;
import com.bidnow.common.exception.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Ensures the bidder's auction deposit is locked before a bid is applied (implicit registration on
 * the first bid). A Redis flag skips the wallet round-trip on later bids; without it the idempotent
 * wallet lock is simply called again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositGate {

    private static final Duration FLAG_RETENTION_AFTER_END = Duration.ofHours(24);
    private static final Duration MIN_FLAG_TTL = Duration.ofMinutes(1);

    private final WalletServiceClient walletServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    static String flagKey(UUID auctionId, UUID userId) {
        return "bidding:deposit:" + auctionId + ":" + userId;
    }

    public void ensureLocked(UUID userId, BidContext ctx) {
        String key = flagKey(ctx.getAuctionId(), userId);
        if (flagPresent(key)) {
            return;
        }
        try {
            walletServiceClient.lockDeposit(new DepositLockCommand(userId, ctx.getAuctionId(), ctx.getDepositAmount()));
        } catch (FeignException ex) {
            throw toBiddingException(DownstreamError.of(ex, objectMapper), userId, ctx.getAuctionId());
        }
        setFlag(key, flagTtl(ctx));
    }

    private RuntimeException toBiddingException(DownstreamError error, UUID userId, UUID auctionId) {
        String code = error.errorCode() == null ? "" : error.errorCode();
        return switch (code) {
            case "INSUFFICIENT_BALANCE" -> new InsufficientBalanceException(error.errors());
            case "WALLET_NOT_ACTIVE" -> new ForbiddenException("Your wallet is not active", BiddingErrorCodes.WALLET_NOT_ACTIVE);
            case "WALLET_NOT_FOUND" -> new ForbiddenException("No wallet found for this account", BiddingErrorCodes.WALLET_NOT_FOUND);
            case "DEPOSIT_LOCK_CLOSED" -> new ConflictException(
                    "Your deposit for this auction is no longer active", BiddingErrorCodes.DEPOSIT_LOCK_CLOSED);
            default -> {
                log.error("wallet-service deposit lock failed for user {} on auction {} (status {}, code {})",
                        userId, auctionId, error.status(), error.errorCode());
                yield new ServiceUnavailableException("Wallet service is unavailable");
            }
        };
    }

    private Duration flagTtl(BidContext ctx) {
        Duration ttl = Duration.between(clock.instant(), ctx.getEndTime().toInstant().plus(FLAG_RETENTION_AFTER_END));
        return ttl.compareTo(MIN_FLAG_TTL) < 0 ? MIN_FLAG_TTL : ttl;
    }

    private boolean flagPresent(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("Redis read failed for deposit flag {} - calling wallet-service: {}", key, ex.getMessage());
            return false;
        }
    }

    private void setFlag(String key, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to set deposit flag {}: {}", key, ex.getMessage());
        }
    }
}
```

- [ ] **Step 5: `AuctionBidGateway`**

`bidding-service/src/main/java/com/bidnow/bidding/service/AuctionBidGateway.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.bidding.feign.DownstreamError;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/** Calls auction-service's authoritative, row-locked apply-bid and maps its errors to bidding errors. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBidGateway {

    private final AuctionServiceClient auctionServiceClient;
    private final ObjectMapper objectMapper;

    public ApplyBidResult apply(UUID auctionId, ApplyBidCommand command) {
        BaseResponse<ApplyBidResult> response;
        try {
            response = auctionServiceClient.applyBid(auctionId, command);
        } catch (FeignException ex) {
            throw toBiddingException(DownstreamError.of(ex, objectMapper), auctionId, command.bidId());
        }
        if (response == null || response.getData() == null) {
            log.error("auction-service returned an empty apply-bid result for bid {} on auction {}", command.bidId(), auctionId);
            throw new ServiceUnavailableException("Auction service is unavailable");
        }
        return response.getData();
    }

    private RuntimeException toBiddingException(DownstreamError error, UUID auctionId, UUID bidId) {
        String code = error.errorCode() == null ? "" : error.errorCode();
        if (error.status() == 409 && BiddingErrorCodes.AUCTION_NOT_OPEN.equals(code)) {
            return new ConflictException("Auction is not open for bidding", BiddingErrorCodes.AUCTION_NOT_OPEN);
        }
        if (error.status() == 400 && BiddingErrorCodes.BID_TOO_LOW.equals(code) && error.errors().containsKey("minimumBid")) {
            return new BidTooLowException(new BigDecimal(error.errors().get("minimumBid")));
        }
        if (error.status() == 403 && BiddingErrorCodes.BID_OWN_AUCTION.equals(code)) {
            return new ForbiddenException("Sellers cannot bid on their own auction", BiddingErrorCodes.BID_OWN_AUCTION);
        }
        if (error.status() == 404) {
            return new NotFoundException("Auction not found: " + auctionId, BiddingErrorCodes.AUCTION_NOT_FOUND);
        }
        log.error("auction-service apply-bid failed for bid {} on auction {} (status {}, code {})",
                bidId, auctionId, error.status(), error.errorCode());
        return new ServiceUnavailableException("Auction service is unavailable");
    }
}
```

- [ ] **Step 6: Run the tests and confirm they pass**

Run: `mvn -q -pl bidding-service -am test -Dtest='DepositGateTest,AuctionBidGatewayTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (13 + 9 tests).

---

### Task 4: `BidPlacedEvent` fields, `UserSummaryCacheService`, `BidEventPublisher`, Kafka producer config

**Files:**
- Modify: `common/src/main/java/com/bidnow/common/dto/event/BidPlacedEvent.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/feign/UserServiceClient.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/service/UserSummaryCacheService.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/kafka/BidEventPublisher.java`
- Modify: `bidding-service/src/main/resources/application.yml`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/service/UserSummaryCacheServiceTest.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/kafka/BidEventPublisherTest.java`

**Interfaces:**
- Produces:
  - `BidPlacedEvent`: adds the fields `UUID bidId`, `Integer totalBids` and `OffsetDateTime endTime`. The existing fields are unchanged.
  - `UserServiceClient.getUserSummary(UUID): BaseResponse<UserSummaryResponse>`
  - `UserSummaryCacheService.displayName(UUID userId): String` (never throws), with `static final String UNKNOWN_BIDDER = "Unknown bidder"` and `static String key(UUID)`
  - `BidEventPublisher.publishBidPlaced(BidPlacedEvent)` (never throws), with `static final String BID_PLACED_TOPIC = "bid-placed-topic"`
  - properties: `bidding.cache.user-summary-ttl-seconds` and `spring.kafka.*`

- [ ] **Step 1: Write the failing tests**

`bidding-service/src/test/java/com/bidnow/bidding/service/UserSummaryCacheServiceTest.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.feign.UserServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSummaryCacheServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String KEY = "bidding:user:00000000-0000-0000-0000-00000000000b:summary";

    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private UserSummaryCacheService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new UserSummaryCacheService(userServiceClient, redisTemplate, objectMapper, 600);
    }

    private static UserSummaryResponse alice() {
        return UserSummaryResponse.builder().id(USER_ID).name("Alice").avatarUrl("https://cdn/a.png").build();
    }

    @Test
    void key_hasContractFormat() {
        assertThat(UserSummaryCacheService.key(USER_ID)).isEqualTo(KEY);
    }

    @Test
    void cacheHit_returnsNameWithoutFeign() throws Exception {
        when(valueOps.get(KEY)).thenReturn(objectMapper.writeValueAsString(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
        verify(userServiceClient, never()).getUserSummary(any());
    }

    @Test
    void cacheMiss_fetchesAndCachesSummary() throws Exception {
        when(valueOps.get(KEY)).thenReturn(null);
        when(userServiceClient.getUserSummary(USER_ID)).thenReturn(BaseResponse.success(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
        verify(valueOps).set(KEY, objectMapper.writeValueAsString(alice()), Duration.ofSeconds(600));
    }

    @Test
    void userServiceFailure_returnsUnknownBidder() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(userServiceClient.getUserSummary(USER_ID)).thenThrow(new RuntimeException("down"));

        assertThat(service.displayName(USER_ID)).isEqualTo(UserSummaryCacheService.UNKNOWN_BIDDER);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void blankName_returnsUnknownBidderAndIsNotCached() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(userServiceClient.getUserSummary(USER_ID))
                .thenReturn(BaseResponse.success(UserSummaryResponse.builder().id(USER_ID).name(" ").build()));

        assertThat(service.displayName(USER_ID)).isEqualTo(UserSummaryCacheService.UNKNOWN_BIDDER);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisFailure_fallsThroughToUserService() {
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(userServiceClient.getUserSummary(USER_ID)).thenReturn(BaseResponse.success(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
    }

    @Test
    void corruptCache_isIgnoredAndRefetched() {
        when(valueOps.get(KEY)).thenReturn("{not json");
        when(userServiceClient.getUserSummary(eq(USER_ID))).thenReturn(BaseResponse.success(alice()));

        assertThat(service.displayName(USER_ID)).isEqualTo("Alice");
    }
}
```

`bidding-service/src/test/java/com/bidnow/bidding/kafka/BidEventPublisherTest.java`:

```java
package com.bidnow.bidding.kafka;

import com.bidnow.common.dto.event.BidPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private BidEventPublisher publisher;

    private static BidPlacedEvent event() {
        return BidPlacedEvent.builder().auctionId(UUID.randomUUID()).bidId(UUID.randomUUID()).build();
    }

    @Test
    void publishes_toBidPlacedTopicKeyedByAuction() {
        BidPlacedEvent event = event();
        when(kafkaTemplate.send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishBidPlaced(event);

        verify(kafkaTemplate).send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event);
    }

    @Test
    void asyncSendFailure_isLoggedNotThrown() {
        BidPlacedEvent event = event();
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture.failedFuture(new KafkaException("down"));
        when(kafkaTemplate.send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event)).thenReturn(failed);

        assertThatCode(() -> publisher.publishBidPlaced(event)).doesNotThrowAnyException();
    }

    @Test
    void synchronousSendFailure_isLoggedNotThrown() {
        BidPlacedEvent event = event();
        when(kafkaTemplate.send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event))
                .thenThrow(new KafkaException("metadata timeout"));

        assertThatCode(() -> publisher.publishBidPlaced(event)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl bidding-service -am test -Dtest='UserSummaryCacheServiceTest,BidEventPublisherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Extend `BidPlacedEvent` (additive only)**

In `common/src/main/java/com/bidnow/common/dto/event/BidPlacedEvent.java`, add `import java.time.OffsetDateTime;` and append these fields after `isAntiSnipingTriggered`:

```java
    private UUID bidId;
    private Integer totalBids;
    private OffsetDateTime endTime; // auction end time after this bid (reflects any anti-sniping extension)
```

- [ ] **Step 4: `UserServiceClient` and `UserSummaryCacheService`**

`bidding-service/src/main/java/com/bidnow/bidding/feign/UserServiceClient.java`:

```java
package com.bidnow.bidding.feign;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/internal/{userId}/summary")
    BaseResponse<UserSummaryResponse> getUserSummary(@PathVariable("userId") UUID userId);
}
```

`bidding-service/src/main/java/com/bidnow/bidding/service/UserSummaryCacheService.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.feign.UserServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/** Cached bidder display names for events. Never fails: any problem yields "Unknown bidder". */
@Slf4j
@Service
public class UserSummaryCacheService {

    static final String UNKNOWN_BIDDER = "Unknown bidder";

    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public UserSummaryCacheService(UserServiceClient userServiceClient,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${bidding.cache.user-summary-ttl-seconds:600}") long ttlSeconds) {
        this.userServiceClient = userServiceClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    static String key(UUID userId) {
        return "bidding:user:" + userId + ":summary";
    }

    public String displayName(UUID userId) {
        UserSummaryResponse cached = readCache(userId);
        if (cached != null && hasName(cached)) {
            return cached.getName();
        }
        UserSummaryResponse fresh;
        try {
            BaseResponse<UserSummaryResponse> response = userServiceClient.getUserSummary(userId);
            fresh = response == null ? null : response.getData();
        } catch (RuntimeException ex) {
            log.warn("user-service summary lookup failed for {}: {}", userId, ex.getMessage());
            return UNKNOWN_BIDDER;
        }
        if (fresh == null || !hasName(fresh)) {
            return UNKNOWN_BIDDER;
        }
        writeCache(userId, fresh);
        return fresh.getName();
    }

    private static boolean hasName(UserSummaryResponse summary) {
        return summary.getName() != null && !summary.getName().isBlank();
    }

    private UserSummaryResponse readCache(UUID userId) {
        try {
            String json = redisTemplate.opsForValue().get(key(userId));
            return json == null ? null : objectMapper.readValue(json, UserSummaryResponse.class);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("User summary cache read failed for {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private void writeCache(UUID userId, UserSummaryResponse summary) {
        try {
            redisTemplate.opsForValue().set(key(userId), objectMapper.writeValueAsString(summary), ttl);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("User summary cache write failed for {}: {}", userId, ex.getMessage());
        }
    }
}
```

- [ ] **Step 5: `BidEventPublisher`**

`bidding-service/src/main/java/com/bidnow/bidding/kafka/BidEventPublisher.java`:

```java
package com.bidnow.bidding.kafka;

import com.bidnow.common.dto.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes accepted bids. Called only after the bid is committed and applied, so a publish failure
 * never fails the bid — it is logged CRITICAL (real-time clients miss the update; data is correct).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventPublisher {

    static final String BID_PLACED_TOPIC = "bid-placed-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBidPlaced(BidPlacedEvent event) {
        try {
            kafkaTemplate.send(BID_PLACED_TOPIC, event.getAuctionId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("CRITICAL: Failed to publish BidPlacedEvent for bid {} on auction {}",
                                    event.getBidId(), event.getAuctionId(), ex);
                        } else {
                            log.info("Published BidPlacedEvent for bid {} on auction {}", event.getBidId(), event.getAuctionId());
                        }
                    });
        } catch (RuntimeException ex) {
            log.error("CRITICAL: Failed to publish BidPlacedEvent for bid {} on auction {}",
                    event.getBidId(), event.getAuctionId(), ex);
        }
    }
}
```

- [ ] **Step 6: Kafka and user-summary config**

In `bidding-service/src/main/resources/application.yml`:

- Under `spring:` (the same level as `data:`), add:

```yaml
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    template:
      observation-enabled: true
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        max.block.ms: 2000   # never block a bid request for long when the broker is unreachable
    consumer:
      group-id: bidding-service-group
      auto-offset-reset: latest   # consumed events only evict caches; history is irrelevant
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "*"
    listener:
      observation-enabled: true
```

- Under the existing `bidding.cache:`, add `user-summary-ttl-seconds: 600`.
- Do not create duplicate `spring:` or `bidding:` blocks.

- [ ] **Step 7: Run the tests, then check that media-service still compiles**

Run: `mvn -q -pl bidding-service -am test -Dtest='UserSummaryCacheServiceTest,BidEventPublisherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (7 + 3 tests).

Run: `mvn -q -pl media-service -am compile`
Expected: BUILD SUCCESS. The `BidPlacedEvent` change is additive.

---

### Task 5: `BidService` — the placement orchestration

**Files:**
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/dto/BidContext.java` (`@Builder(toBuilder = true)`)
- Create: `bidding-service/src/main/java/com/bidnow/bidding/dto/response/PlaceBidResponse.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/service/BidService.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/service/BidServiceTest.java`

**Interfaces:**
- Consumes:
  - Story 2: `AuctionContextCacheService.get/refresh/put/evict`, `BidValidationService.preValidate`, `BidRepository`, `Bid`, `PlaceBidRequest`, the `Clock` bean.
  - Tasks 3–4: `DepositGate.ensureLocked`, `AuctionBidGateway.apply`, `ApplyBidCommand`, `ApplyBidResult`, `UserSummaryCacheService.displayName`, `BidEventPublisher.publishBidPlaced`, and the new `BidPlacedEvent` fields.
  - Spring Boot's auto-configured `TransactionTemplate` bean.
- Produces:
  - `PlaceBidResponse` (`@Data @Builder`): `UUID bidId, UUID auctionId, BigDecimal amount, OffsetDateTime placedAt, BigDecimal currentPrice, int totalBids, OffsetDateTime endTime, boolean extended`
  - `BidService.placeBid(UUID bidderId, PlaceBidRequest request): PlaceBidResponse`

- [ ] **Step 1: Write the failing test**

`bidding-service/src/test/java/com/bidnow/bidding/service/BidServiceTest.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.kafka.BidEventPublisher;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.event.BidPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PREVIOUS_WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final OffsetDateTime END = OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC);

    @Mock
    private AuctionContextCacheService contextCache;
    @Mock
    private DepositGate depositGate;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuctionBidGateway auctionBidGateway;
    @Mock
    private UserSummaryCacheService userSummaries;
    @Mock
    private BidEventPublisher bidEventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BidService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(bidRepository.saveAndFlush(any(Bid.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userSummaries.displayName(BIDDER_ID)).thenReturn("Bob");
        service = new BidService(contextCache, new BidValidationService(), depositGate, bidRepository,
                auctionBidGateway, userSummaries, bidEventPublisher,
                new TransactionTemplate(transactionManager), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BidContext openContext() {
        return BidContext.builder()
                .auctionId(AUCTION_ID).title("Vintage Watch").sellerId(SELLER_ID).status("ACTIVE")
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).currentWinnerId(PREVIOUS_WINNER).totalBids(1).endTime(END)
                .build();
    }

    private static ApplyBidResult applied(boolean extended, OffsetDateTime endTime) {
        return ApplyBidResult.builder()
                .auctionId(AUCTION_ID).currentPrice(new BigDecimal("105.00")).currentWinnerId(BIDDER_ID)
                .previousWinnerId(PREVIOUS_WINNER).totalBids(2).endTime(endTime).extended(extended).build();
    }

    private static PlaceBidRequest request(String amount) {
        return new PlaceBidRequest(AUCTION_ID, new BigDecimal(amount));
    }

    @Test
    void happyPath_runsStepsInOrderAndPublishesAfterCommit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        InOrder order = inOrder(contextCache, depositGate, bidRepository, auctionBidGateway, transactionManager,
                bidEventPublisher);
        order.verify(contextCache).get(AUCTION_ID);
        order.verify(depositGate).ensureLocked(eq(BIDDER_ID), any(BidContext.class));
        order.verify(bidRepository).saveAndFlush(any(Bid.class));
        order.verify(auctionBidGateway).apply(eq(AUCTION_ID), any(ApplyBidCommand.class));
        order.verify(transactionManager).commit(any());
        order.verify(contextCache).put(any(BidContext.class));
        order.verify(bidEventPublisher).publishBidPlaced(any(BidPlacedEvent.class));

        assertThat(response.getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(response.getAmount()).isEqualByComparingTo("105.00");
        assertThat(response.getCurrentPrice()).isEqualByComparingTo("105.00");
        assertThat(response.getTotalBids()).isEqualTo(2);
        assertThat(response.getEndTime()).isEqualTo(END);
        assertThat(response.isExtended()).isFalse();
        assertThat(response.getPlacedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void bidIdIsSharedByStoredBidApplyCommandAndEvent() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<Bid> bid = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).saveAndFlush(bid.capture());
        ArgumentCaptor<ApplyBidCommand> command = ArgumentCaptor.forClass(ApplyBidCommand.class);
        verify(auctionBidGateway).apply(eq(AUCTION_ID), command.capture());
        ArgumentCaptor<BidPlacedEvent> event = ArgumentCaptor.forClass(BidPlacedEvent.class);
        verify(bidEventPublisher).publishBidPlaced(event.capture());

        assertThat(bid.getValue().getId()).isNotNull().isEqualTo(response.getBidId());
        assertThat(command.getValue().bidId()).isEqualTo(response.getBidId());
        assertThat(command.getValue().bidderId()).isEqualTo(BIDDER_ID);
        assertThat(command.getValue().amount()).isEqualByComparingTo("105.00");
        assertThat(event.getValue().getBidId()).isEqualTo(response.getBidId());
        assertThat(bid.getValue().getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(bid.getValue().getBidderId()).isEqualTo(BIDDER_ID);
        assertThat(bid.getValue().isAutoBid()).isFalse();
    }

    @Test
    void event_carriesContextTitleBidderNamePreviousWinnerAndNewState() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<BidPlacedEvent> captor = ArgumentCaptor.forClass(BidPlacedEvent.class);
        verify(bidEventPublisher).publishBidPlaced(captor.capture());
        BidPlacedEvent event = captor.getValue();
        assertThat(event.getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(event.getAuctionTitle()).isEqualTo("Vintage Watch");
        assertThat(event.getBidderId()).isEqualTo(BIDDER_ID);
        assertThat(event.getBidderName()).isEqualTo("Bob");
        assertThat(event.getBidAmount()).isEqualByComparingTo("105.00");
        assertThat(event.getBidTime()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(event.getPreviousHighestBidderId()).isEqualTo(PREVIOUS_WINNER);
        assertThat(event.isAntiSnipingTriggered()).isFalse();
        assertThat(event.getTotalBids()).isEqualTo(2);
        assertThat(event.getEndTime()).isEqualTo(END);
    }

    @Test
    void cache_isOverwrittenWithAuthoritativeStateAfterCommit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        OffsetDateTime extendedEnd = END.plusMinutes(5);
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(true, extendedEnd));

        service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<BidContext> captor = ArgumentCaptor.forClass(BidContext.class);
        verify(contextCache).put(captor.capture());
        BidContext updated = captor.getValue();
        assertThat(updated.getCurrentPrice()).isEqualByComparingTo("105.00");
        assertThat(updated.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(updated.getTotalBids()).isEqualTo(2);
        assertThat(updated.getEndTime()).isEqualTo(extendedEnd);
        assertThat(updated.getTitle()).isEqualTo("Vintage Watch");
        assertThat(updated.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void extension_isPersistedOnBidAndReportedInEventAndResponse() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenReturn(applied(true, END.plusMinutes(5)));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<Bid> bid = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).saveAndFlush(bid.capture());
        assertThat(bid.getValue().isAntiSnipingTriggered()).isTrue();
        ArgumentCaptor<BidPlacedEvent> event = ArgumentCaptor.forClass(BidPlacedEvent.class);
        verify(bidEventPublisher).publishBidPlaced(event.capture());
        assertThat(event.getValue().isAntiSnipingTriggered()).isTrue();
        assertThat(response.isExtended()).isTrue();
    }

    @Test
    void applyBidConflict_rollsBackEvictsAndPublishesNothing() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenThrow(new ConflictException("closed", BiddingErrorCodes.AUCTION_NOT_OPEN));

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ConflictException.class);

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(contextCache).evict(AUCTION_ID);
        verify(contextCache, never()).put(any());
        verify(bidEventPublisher, never()).publishBidPlaced(any());
    }

    @Test
    void applyBidTooLow_rollsBackAndEvicts() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenThrow(new BidTooLowException(new BigDecimal("110.00")));

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(BidTooLowException.class);

        verify(transactionManager).rollback(any());
        verify(contextCache).evict(AUCTION_ID);
    }

    @Test
    void auctionServiceUnavailable_rollsBackWithoutEviction() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenThrow(new ServiceUnavailableException("down"));

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(transactionManager).rollback(any());
        verify(contextCache, never()).evict(any());
        verify(bidEventPublisher, never()).publishBidPlaced(any());
    }

    @Test
    void depositGateFailure_insertsNothingAndNeverAppliesBid() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        doThrow(new ServiceUnavailableException("wallet down")).when(depositGate).ensureLocked(eq(BIDDER_ID), any());

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(bidRepository, never()).saveAndFlush(any());
        verify(auctionBidGateway, never()).apply(any(), any());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void commitFailureAfterApply_propagatesAndPublishesNothing() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));
        doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(TransactionSystemException.class);

        verify(bidEventPublisher, never()).publishBidPlaced(any());
        verify(contextCache, never()).put(any());
    }

    @Test
    void preValidationTooLow_neitherRefreshesNorLocksDeposit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("104.00")))
                .isInstanceOf(BidTooLowException.class);

        verify(contextCache, never()).refresh(any());
        verify(depositGate, never()).ensureLocked(any(), any());
    }

    @Test
    void preValidationOwnAuction_neverRefreshes() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        assertThatThrownBy(() -> service.placeBid(SELLER_ID, request("500.00")))
                .hasFieldOrPropertyWithValue("errorCode", BiddingErrorCodes.BID_OWN_AUCTION);

        verify(contextCache, never()).refresh(any());
    }

    @Test
    void staleClosedContext_isRefreshedOnceAndBidProceeds() {
        BidContext stale = openContext();
        stale.setStatus("SCHEDULED");
        when(contextCache.get(AUCTION_ID)).thenReturn(stale);
        when(contextCache.refresh(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        assertThat(response.getTotalBids()).isEqualTo(2);
        verify(contextCache).refresh(AUCTION_ID);
    }

    @Test
    void genuinelyClosedAuction_returnsConflictAfterOneRefresh() {
        BidContext closed = openContext();
        closed.setStatus("COMPLETED");
        when(contextCache.get(AUCTION_ID)).thenReturn(closed);
        when(contextCache.refresh(AUCTION_ID)).thenReturn(closed);

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ConflictException.class);

        verify(contextCache).refresh(AUCTION_ID);
        verify(depositGate, never()).ensureLocked(any(), any());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`BidService` and `PlaceBidResponse` don't exist yet).

- [ ] **Step 3: `BidContext.toBuilder` and `PlaceBidResponse`**

In `BidContext.java`, change `@Builder` to `@Builder(toBuilder = true)`.

`bidding-service/src/main/java/com/bidnow/bidding/dto/response/PlaceBidResponse.java`:

```java
package com.bidnow.bidding.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PlaceBidResponse {
    private UUID bidId;
    private UUID auctionId;
    private BigDecimal amount;
    private OffsetDateTime placedAt;
    private BigDecimal currentPrice;
    private int totalBids;
    private OffsetDateTime endTime;
    private boolean extended;
}
```

- [ ] **Step 4: `BidService`**

`bidding-service/src/main/java/com/bidnow/bidding/service/BidService.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.kafka.BidEventPublisher;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Places a bid (spec §3): pre-validate → lock deposit → [insert bid → apply in auction-service] →
 * after commit: refresh cache, publish event. The deposit lock and all post-commit work stay outside
 * the DB transaction; only the insert and the (fast, bounded) apply-bid call run inside it, so a
 * rejected or failed apply-bid rolls the insert back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final AuctionContextCacheService contextCache;
    private final BidValidationService bidValidationService;
    private final DepositGate depositGate;
    private final BidRepository bidRepository;
    private final AuctionBidGateway auctionBidGateway;
    private final UserSummaryCacheService userSummaries;
    private final BidEventPublisher bidEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PlaceBidResponse placeBid(UUID bidderId, PlaceBidRequest request) {
        UUID auctionId = request.getAuctionId();
        BigDecimal amount = request.getAmount();

        BidContext ctx = preValidateWithRefresh(auctionId, bidderId, amount);
        depositGate.ensureLocked(bidderId, ctx);

        UUID bidId = UUID.randomUUID();
        ApplyBidResult result = persistAndApply(bidId, auctionId, bidderId, amount);

        contextCache.put(ctx.toBuilder()
                .currentPrice(result.getCurrentPrice())
                .currentWinnerId(result.getCurrentWinnerId())
                .totalBids(result.getTotalBids())
                .endTime(result.getEndTime())
                .build());
        bidEventPublisher.publishBidPlaced(BidPlacedEvent.builder()
                .bidId(bidId)
                .auctionId(auctionId)
                .auctionTitle(ctx.getTitle())
                .bidderId(bidderId)
                .bidderName(userSummaries.displayName(bidderId))
                .bidAmount(amount)
                .bidTime(LocalDateTime.now(clock))
                .previousHighestBidderId(result.getPreviousWinnerId())
                .isAntiSnipingTriggered(result.isExtended())
                .totalBids(result.getTotalBids())
                .endTime(result.getEndTime())
                .build());

        return PlaceBidResponse.builder()
                .bidId(bidId)
                .auctionId(auctionId)
                .amount(amount)
                .placedAt(OffsetDateTime.now(clock))
                .currentPrice(result.getCurrentPrice())
                .totalBids(result.getTotalBids())
                .endTime(result.getEndTime())
                .extended(result.isExtended())
                .build();
    }

    /**
     * A cached context can be stale (the auction has started, or its end time was extended by
     * anti-sniping), so a 409 must reflect auction-service's current state: on a conflict the
     * context is refreshed once and re-validated. Other rejections are never refreshed - a stale
     * price only lowers the minimum bid and the seller never changes.
     */
    private BidContext preValidateWithRefresh(UUID auctionId, UUID bidderId, BigDecimal amount) {
        BidContext ctx = contextCache.get(auctionId);
        try {
            bidValidationService.preValidate(ctx, bidderId, amount, clock.instant());
            return ctx;
        } catch (ConflictException ex) {
            BidContext fresh = contextCache.refresh(auctionId);
            bidValidationService.preValidate(fresh, bidderId, amount, clock.instant());
            return fresh;
        }
    }

    private ApplyBidResult persistAndApply(UUID bidId, UUID auctionId, UUID bidderId, BigDecimal amount) {
        AtomicBoolean applied = new AtomicBoolean(false);
        try {
            return transactionTemplate.execute(status -> {
                Bid bid = bidRepository.saveAndFlush(Bid.builder()
                        .id(bidId)
                        .auctionId(auctionId)
                        .bidderId(bidderId)
                        .amount(amount)
                        .build());
                ApplyBidResult result = auctionBidGateway.apply(auctionId, new ApplyBidCommand(bidId, bidderId, amount));
                applied.set(true);
                bid.setAntiSnipingTriggered(result.isExtended());
                return result;
            });
        } catch (ConflictException | BidTooLowException ex) {
            // auction-service's authoritative state disagreed with our cached context
            contextCache.evict(auctionId);
            throw ex;
        } catch (RuntimeException ex) {
            if (applied.get()) {
                log.error("CRITICAL: bid {} on auction {} was applied by auction-service but the local commit failed "
                        + "- auction last_bid_id = {} has no bids row; reconcile manually", bidId, auctionId, bidId, ex);
            }
            throw ex;
        }
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (14 tests).

---

### Task 6: `BidController` → 201 via `BidService`

**Files:**
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/controller/BidController.java` (replace the whole file)
- Modify: `bidding-service/src/test/java/com/bidnow/bidding/controller/BidControllerTest.java` (replace the whole file)

**Interfaces:**
- Consumes: `BidService.placeBid` (Task 5), and the exceptions and handler from Task 2 and Story 2.
- Produces: `POST /api/v1/bids` → `201` with `BaseResponse<PlaceBidResponse>`.

The pre-validation and refresh behaviour moved into `BidService` in Task 5 and is tested there. This controller test covers only HTTP concerns: status codes, body shape, validation and error rendering.

- [ ] **Step 1: Replace the controller test**

`bidding-service/src/test/java/com/bidnow/bidding/controller/BidControllerTest.java`:

```java
package com.bidnow.bidding.controller;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.BiddingExceptionHandler;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.InsufficientBalanceException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.service.BidService;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private BidService bidService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BidController(bidService))
                .setControllerAdvice(new BiddingExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private ResultActions postBid(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/bids").header("X-User-Id", BIDDER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String auctionId, String amount) {
        return """
                {"auctionId": %s, "amount": %s}
                """.formatted(auctionId, amount);
    }

    private static String validBody() {
        return body("\"" + AUCTION_ID + "\"", "105.00");
    }

    private void serviceThrows(RuntimeException ex) {
        when(bidService.placeBid(eq(BIDDER_ID), any(PlaceBidRequest.class))).thenThrow(ex);
    }

    @Test
    void acceptedBid_returns201WithBody() throws Exception {
        UUID bidId = UUID.randomUUID();
        when(bidService.placeBid(eq(BIDDER_ID), any(PlaceBidRequest.class))).thenReturn(PlaceBidResponse.builder()
                .bidId(bidId).auctionId(AUCTION_ID).amount(new BigDecimal("105.00"))
                .placedAt(OffsetDateTime.parse("2026-10-01T12:00:00Z"))
                .currentPrice(new BigDecimal("105.00")).totalBids(2)
                .endTime(OffsetDateTime.parse("2026-10-01T13:00:00Z")).extended(false).build());

        postBid(validBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Bid placed"))
                .andExpect(jsonPath("$.data.bidId").value(bidId.toString()))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID.toString()))
                .andExpect(jsonPath("$.data.totalBids").value(2))
                .andExpect(jsonPath("$.data.extended").value(false));

        ArgumentCaptor<PlaceBidRequest> captor = ArgumentCaptor.forClass(PlaceBidRequest.class);
        verify(bidService).placeBid(eq(BIDDER_ID), captor.capture());
        assertThat(captor.getValue().getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("105.00");
    }

    @Test
    void bidTooLow_returns400WithMinimumBid() throws Exception {
        serviceThrows(new BidTooLowException(new BigDecimal("110.00")));

        postBid(validBody())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_TOO_LOW))
                .andExpect(jsonPath("$.errors.minimumBid").value("110.00"));
    }

    @Test
    void insufficientBalance_returns403WithWalletDetails() throws Exception {
        serviceThrows(new InsufficientBalanceException(Map.of("availableBalance", "10.00", "required", "20.00")));

        postBid(validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_INSUFFICIENT_BALANCE))
                .andExpect(jsonPath("$.errors.availableBalance").value("10.00"))
                .andExpect(jsonPath("$.errors.required").value("20.00"));
    }

    @Test
    void ownAuction_returns403() throws Exception {
        serviceThrows(new ForbiddenException("own", BiddingErrorCodes.BID_OWN_AUCTION));

        postBid(validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.BID_OWN_AUCTION));
    }

    @Test
    void walletNotActive_returns403() throws Exception {
        serviceThrows(new ForbiddenException("inactive", BiddingErrorCodes.WALLET_NOT_ACTIVE));

        postBid(validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.WALLET_NOT_ACTIVE));
    }

    @Test
    void unknownAuction_returns404() throws Exception {
        serviceThrows(new NotFoundException("nf", BiddingErrorCodes.AUCTION_NOT_FOUND));

        postBid(validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_FOUND));
    }

    @Test
    void auctionNotOpen_returns409() throws Exception {
        serviceThrows(new ConflictException("closed", BiddingErrorCodes.AUCTION_NOT_OPEN));

        postBid(validBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.AUCTION_NOT_OPEN));
    }

    @Test
    void depositLockClosed_returns409() throws Exception {
        serviceThrows(new ConflictException("closed", BiddingErrorCodes.DEPOSIT_LOCK_CLOSED));

        postBid(validBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.DEPOSIT_LOCK_CLOSED));
    }

    @Test
    void downstreamUnavailable_returns503() throws Exception {
        serviceThrows(new ServiceUnavailableException("down"));

        postBid(validBody())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(BiddingErrorCodes.SERVICE_UNAVAILABLE));
    }

    @Test
    void missingAuctionId_returns400InvalidInput() throws Exception {
        postBid(body("null", "105.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.auctionId").exists());
        verifyNoInteractions(bidService);
    }

    @Test
    void nonPositiveAmount_returns400InvalidInput() throws Exception {
        postBid(body("\"" + AUCTION_ID + "\"", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(bidService);
    }

    @Test
    void amountWithThreeDecimals_returns400InvalidInput() throws Exception {
        postBid(body("\"" + AUCTION_ID + "\"", "105.001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(bidService);
    }

    @Test
    void malformedAuctionId_returns400InvalidInput() throws Exception {
        postBid(body("\"not-a-uuid\"", "105.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verifyNoInteractions(bidService);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR. The `BidController(BidService)` constructor doesn't exist yet.

- [ ] **Step 3: Replace the controller**

`bidding-service/src/main/java/com/bidnow/bidding/controller/BidController.java`:

```java
package com.bidnow.bidding.controller;

import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.service.BidService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bids")
@RequiredArgsConstructor
@Tag(name = "Bids", description = "Bid placement")
public class BidController {

    private final BidService bidService;

    @Operation(summary = "Place a bid",
            description = "Validates the bid, locks the bidder's auction deposit on their first bid, and applies the "
                    + "bid atomically in auction-service.")
    @PostMapping
    public ResponseEntity<BaseResponse<PlaceBidResponse>> placeBid(@AuthenticatedUserId UUID bidderId,
                                                                   @Valid @RequestBody PlaceBidRequest request) {
        PlaceBidResponse placed = bidService.placeBid(bidderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<PlaceBidResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Bid placed")
                        .data(placed)
                        .build());
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (13 tests).

---

### Task 7: Evict cached context on auction ended / cancelled

**Files:**
- Create: `bidding-service/src/main/java/com/bidnow/bidding/config/KafkaConsumerConfig.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumer.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/config/KafkaConsumerConfigTest.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumerTest.java`

**Interfaces:**
- Consumes: `AuctionContextCacheService.evict(UUID)`, and the common `AuctionEndedEvent` and `AuctionCancelledEvent`. Consumer properties come from Task 4.
- Produces: listeners on `auction-ended-topic` and `auction-cancelled-topic`. The config also provides a `DefaultErrorHandler` bean with 3 retries (1 s, 2 s, 4 s), then `<topic>.DLT`.

- [ ] **Step 1: Write the failing tests**

`bidding-service/src/test/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumerTest.java`:

```java
package com.bidnow.bidding.kafka;

import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleConsumerTest {

    @Mock
    private AuctionContextCacheService contextCache;

    @InjectMocks
    private AuctionLifecycleConsumer consumer;

    @Test
    void auctionEnded_evictsContext() {
        UUID auctionId = UUID.randomUUID();

        consumer.onAuctionEnded(AuctionEndedEvent.builder().auctionId(auctionId).build());

        verify(contextCache).evict(auctionId);
    }

    @Test
    void auctionCancelled_evictsContext() {
        UUID auctionId = UUID.randomUUID();

        consumer.onAuctionCancelled(AuctionCancelledEvent.builder().auctionId(auctionId).build());

        verify(contextCache).evict(auctionId);
    }
}
```

`bidding-service/src/test/java/com/bidnow/bidding/config/KafkaConsumerConfigTest.java`:

```java
package com.bidnow.bidding.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    void retryBackOff_isThreeRetriesStartingAtOneSecondDoubling() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerConfig.retryBackOff();

        assertThat(backOff.getMaxRetries()).isEqualTo(3);
        assertThat(backOff.getInitialInterval()).isEqualTo(1_000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaErrorHandler_isBuilt() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(template);

        assertThat(handler).isNotNull();
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl bidding-service -am test -Dtest='AuctionLifecycleConsumerTest,KafkaConsumerConfigTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`bidding-service/src/main/java/com/bidnow/bidding/config/KafkaConsumerConfig.java`:

```java
package com.bidnow.bidding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retries failed records with exponential backoff (1s, 2s, 4s), then publishes them to
 * {@code <topic>.DLT}. Spring Boot applies this CommonErrorHandler to every bidding listener.
 */
@Configuration
public class KafkaConsumerConfig {

    static final int MAX_RETRIES = 3;
    static final long INITIAL_INTERVAL_MS = 1_000L;
    static final double MULTIPLIER = 2.0;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), retryBackOff());
    }

    static ExponentialBackOffWithMaxRetries retryBackOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        return backOff;
    }
}
```

`bidding-service/src/main/java/com/bidnow/bidding/kafka/AuctionLifecycleConsumer.java`:

```java
package com.bidnow.bidding.kafka;

import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Drops the cached bid context when an auction closes so pre-validation rejects new bids immediately. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleConsumer {

    private final AuctionContextCacheService contextCache;

    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionEnded(AuctionEndedEvent event) {
        log.info("Auction {} ended - evicting bid context", event.getAuctionId());
        contextCache.evict(event.getAuctionId());
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionCancelled(AuctionCancelledEvent event) {
        log.info("Auction {} cancelled - evicting bid context", event.getAuctionId());
        contextCache.evict(event.getAuctionId());
    }
}
```

- [ ] **Step 4: Run the full bidding-service unit suite**

Run: `mvn -q -pl bidding-service -am test`
Expected: BUILD SUCCESS. Story 2's `BidControllerTest` was replaced (12 → 13 tests) and every other class is additive.

---

### Task 8: BDD — end-to-end placement against WireMock downstreams

**Files:**
- Modify: `bidding-service/src/test/java/com/bidnow/bidding/bdd/config/CucumberSpringConfig.java`
- Create: `bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/Hooks.java`
- Modify: `bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidPlacementSteps.java`
- Modify: `bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/CommonSteps.java`
- Modify: `bidding-service/src/test/resources/features/bidding-core.feature`
- Create: `bidding-service/src/test/resources/features/place-bid.feature`

**Interfaces:**
- Consumes: the whole service; `bdd-support` `KafkaContainerSupport`, `PostgresContainerSupport`, `RedisContainerSupport`, `WireMockSupport`, `BddRestClient` and `ScenarioContext`.
- Produces: none.

- [ ] **Step 1: Wire in Kafka and the new Feign clients**

In `CucumberSpringConfig`, add `import com.bidnow.bdd.container.KafkaContainerSupport;`. Inside `overrideProperties`, add:

```java
        KafkaContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.cloud.openfeign.client.config.wallet-service.url", WireMockSupport::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.user-service.url", WireMockSupport::baseUrl);
```

- [ ] **Step 2: Move the reset hook into `Hooks`**

`bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/Hooks.java`:

```java
// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/Hooks.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Resets external state before every scenario: WireMock stubs/requests and all Redis keys. */
@RequiredArgsConstructor
public class Hooks {

    private final StringRedisTemplate redisTemplate;

    @Before
    public void resetExternalState() {
        WireMockSupport.reset();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}
```

In `BidPlacementSteps`, delete the `@Before resetExternalState()` method and any imports it no longer needs (`io.cucumber.java.Before`, `RedisCallback`). Keep the `StringRedisTemplate` field, because the "cached in Redis" step still uses it.

- [ ] **Step 3: Add the placement steps**

In `BidPlacementSteps`, add these imports: `static com.github.tomakehurst.wiremock.client.WireMock.post`, `static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor` and `java.util.UUID`. Then add these steps:

```java
    @Given("wallet-service locks deposits successfully")
    public void walletLocksDeposits() {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(WALLET_LOCK_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":200,"message":"Success","data":{"lockId":"%s","amount":20.00,
                                 "status":"LOCKED","alreadyLocked":false,"availableBalance":80.00,"lockedBalance":20.00}}
                                """.formatted(UUID.randomUUID()))));
    }

    @Given("wallet-service rejects deposit locks for insufficient balance")
    public void walletInsufficientBalance() {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(WALLET_LOCK_PATH))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":400,"errorCode":"INSUFFICIENT_BALANCE","message":"Insufficient balance",
                                 "errors":{"availableBalance":"10.00","required":"20.00"}}
                                """)));
    }

    @Given("wallet-service responds {int} to deposit locks")
    public void walletResponds(int status) {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(WALLET_LOCK_PATH))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":" + status + ",\"errorCode\":\"X\",\"message\":\"stub\"}")));
    }

    @Given("auction-service applies bids on auction {string} returning price {string} and {int} total bids")
    public void auctionAppliesBids(String auctionId, String price, int totalBids) {
        String body = """
                {"status":200,"message":"Success","data":{"auctionId":"%s","currentPrice":%s,
                 "currentWinnerId":"%s","previousWinnerId":null,"totalBids":%d,"endTime":"%s",
                 "extended":false,"extensionCount":0}}
                """.formatted(auctionId, price, UUID.randomUUID(), totalBids,
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(applyPath(auctionId)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Given("auction-service rejects bids on auction {string} with {int} {string}")
    public void auctionRejectsBids(String auctionId, int status, String errorCode) {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(applyPath(auctionId)))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":" + status + ",\"errorCode\":\"" + errorCode + "\",\"message\":\"stub\"}")));
    }

    @Given("user-service knows user {string} as {string}")
    public void userServiceKnows(String userId, String name) {
        WireMockSupport.SERVER.stubFor(get(urlEqualTo("/api/v1/users/internal/" + userId + "/summary"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":200,\"message\":\"Success\",\"data\":{\"id\":\"" + userId
                                + "\",\"name\":\"" + name + "\",\"avatarUrl\":null}}")));
    }

    @Then("wallet-service should have received {int} deposit-lock request(s)")
    public void verifyWalletCalls(int count) {
        WireMockSupport.SERVER.verify(count, postRequestedFor(urlEqualTo(WALLET_LOCK_PATH)));
    }

    @Then("auction-service should have received {int} apply-bid request(s) for auction {string}")
    public void verifyApplyCalls(int count, String auctionId) {
        WireMockSupport.SERVER.verify(count, postRequestedFor(urlEqualTo(applyPath(auctionId))));
    }

    @Then("{int} bid(s) should be stored for auction {string}")
    public void storedBids(int count, String auctionId) {
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bids WHERE auction_id = ?::uuid", Integer.class, auctionId);
        assertThat(stored).isEqualTo(count);
    }
```

Add these constants and the helper to the class:

```java
    private static final String WALLET_LOCK_PATH = "/api/v1/internal/wallet/deposit-lock";

    private static String applyPath(String auctionId) {
        return "/api/v1/internal/auctions/" + auctionId + "/bids";
    }
```

- [ ] **Step 4: Add a presence assertion to `CommonSteps`**

```java
    @Then("the response field {string} should be present")
    public void assertFieldPresent(String jsonPath) {
        Object value = ctx.getLastResponse().jsonPath().get(jsonPath);
        assertThat(value)
                .as("Expected field '%s' to be present in response: %s", jsonPath, ctx.getLastResponse().asString())
                .isNotNull();
    }
```

- [ ] **Step 5: Remove the obsolete 501 scenario**

In `bidding-core.feature`, delete the whole scenario titled `A valid bid passes pre-validation (placement arrives in BID-102)`, including its `Given`, `When`, `Then` and `And` lines. The accepted-bid path is now covered by `place-bid.feature`.

- [ ] **Step 6: New feature**

`bidding-service/src/test/resources/features/place-bid.feature`:

```gherkin
@bidding @place-bid @regression
Feature: Placing a bid end to end (deposit lock + authoritative apply-bid)

  Background:
    Given user-service knows user "550e8400-e29b-41d4-a716-446655440010" as "Bob"

  @smoke
  Scenario: A first bid locks the deposit, is applied and stored
    Given auction-service has auction "d0000000-0000-0000-0000-000000000001" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service applies bids on auction "d0000000-0000-0000-0000-000000000001" returning price "105.00" and 2 total bids
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000001"
    Then the response status should be 201
    And the response field "data.totalBids" should equal "2"
    And the response field "data.bidId" should be present
    And 1 bid should be stored for auction "d0000000-0000-0000-0000-000000000001"
    And wallet-service should have received 1 deposit-lock request
    And the bid context for auction "d0000000-0000-0000-0000-000000000001" should be cached in Redis

  Scenario: A second bid by the same user skips the wallet
    Given auction-service has auction "d0000000-0000-0000-0000-000000000002" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service applies bids on auction "d0000000-0000-0000-0000-000000000002" returning price "105.00" and 2 total bids
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000002"
    And user "550e8400-e29b-41d4-a716-446655440010" bids "110.00" on auction "d0000000-0000-0000-0000-000000000002"
    Then the response status should be 201
    And 2 bids should be stored for auction "d0000000-0000-0000-0000-000000000002"
    And wallet-service should have received 1 deposit-lock request

  @negative
  Scenario: Insufficient wallet balance rejects the bid and stores nothing
    Given auction-service has auction "d0000000-0000-0000-0000-000000000003" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service rejects deposit locks for insufficient balance
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000003"
    Then the response status should be 403
    And the response field "errorCode" should equal "BID_INSUFFICIENT_BALANCE"
    And the response field "errors.required" should equal "20.00"
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000003"
    And auction-service should have received 0 apply-bid requests for auction "d0000000-0000-0000-0000-000000000003"

  @negative
  Scenario: wallet-service failure makes bidding unavailable
    Given auction-service has auction "d0000000-0000-0000-0000-000000000004" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service responds 500 to deposit locks
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000004"
    Then the response status should be 503
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000004"
    And auction-service should have received 0 apply-bid requests for auction "d0000000-0000-0000-0000-000000000004"

  @negative
  Scenario: auction-service rejecting the bid rolls the stored bid back
    Given auction-service has auction "d0000000-0000-0000-0000-000000000005" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service rejects bids on auction "d0000000-0000-0000-0000-000000000005" with 409 "AUCTION_NOT_OPEN"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000005"
    Then the response status should be 409
    And the response field "errorCode" should equal "AUCTION_NOT_OPEN"
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000005"

  @negative
  Scenario: auction-service failure during apply-bid rolls back and returns 503
    Given auction-service has auction "d0000000-0000-0000-0000-000000000006" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service rejects bids on auction "d0000000-0000-0000-0000-000000000006" with 500 "INTERNAL_SERVER_ERROR"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000006"
    Then the response status should be 503
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000006"
```

- [ ] **Step 7: Compile, and run BDD only if requested**

Run: `mvn -q -pl bidding-service -am test-compile`
Expected: BUILD SUCCESS.

Run `mvn -q -pl bidding-service -am test -Pbdd` only if the controller says so (it needs Docker). Expected result: all scenarios pass, which is 8 in `bidding-core.feature` and 6 in `place-bid.feature`.

---

### Task 9: Documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/database/bidding-service-schema.md`
- Modify: `docs/superpowers/specs/2026-07-04-bidding-service-design.md`
- Modify: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`

All paths are relative to the repo root.

- [ ] **Step 1: `docs/architecture.md`**

In "Service-to-Service Internal APIs", no new rows are needed. bidding-service calls existing internal endpoints: wallet deposit-lock, auction bid-context and apply-bid, and user summary. The caller column of the wallet and auction rows already says "Bidding".

Directly after that table, add a subsection:

```markdown
### Bidding Service (public API & events)

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/v1/bids` `{auctionId, amount}` | `X-User-Id` | Place a bid: pre-validate (cached context, refreshed once on 409) → lock deposit on first bid (wallet) → insert bid + apply atomically (auction, row-locked) → 201 `PlaceBidResponse`. Errors: `BID_TOO_LOW` 400 (`errors.minimumBid`), `BID_OWN_AUCTION` / `BID_INSUFFICIENT_BALANCE` (`errors.availableBalance`, `errors.required`) / `WALLET_NOT_ACTIVE` / `WALLET_NOT_FOUND` 403, `AUCTION_NOT_FOUND` 404, `AUCTION_NOT_OPEN` / `DEPOSIT_LOCK_CLOSED` 409, `SERVICE_UNAVAILABLE` 503 |

| Topic | Direction | Payload / effect |
|---|---|---|
| `bid-placed-topic` | publishes (after commit, key = auctionId) | `BidPlacedEvent` incl. `bidId`, `totalBids`, `endTime`, `previousHighestBidderId` |
| `auction-ended-topic`, `auction-cancelled-topic` | consumes (`bidding-service-group`) | Evict `bidding:auction:{id}:context` |
```

- [ ] **Step 2: Schema doc Redis keys**

In `docs/database/bidding-service-schema.md`, add these rows to the "Redis keys" table:

```markdown
| `bidding:deposit:{auctionId}:{userId}` | `"1"` — deposit already locked in wallet-service | auction `endTime` + 24h (min 60 s) |
| `bidding:user:{userId}:summary` | JSON `UserSummaryResponse` (display name for events) | `bidding.cache.user-summary-ttl-seconds` (600) |
```

- [ ] **Step 3: Spec**

In `docs/superpowers/specs/2026-07-04-bidding-service-design.md` §3, after the flow block, add:

```markdown
- **Implementation notes (BID-102):** `bidTime`/`placedAt` come from the service `Clock` (UTC). The Kafka producer uses `max.block.ms: 2000` so an unreachable broker cannot stall a bid. A replayed `bidId` cannot reach the `bids` INSERT because `bidId` is generated per request and apply-bid is never retried — no duplicate-key handling is needed. auction-service publishes its cancel / force-close / activation events after commit so a stalled broker never extends the auction row lock.
```

- [ ] **Step 4: Roadmap**

In `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`, Story 3 section:
- Tick `3.0`, `3.0a`, `3.1`, `3.2`, `3.3`, `3.4` and `3.5`.
- Leave `3.6` unticked. It needs a real docker-compose run; the BDD in Task 8 covers the same scenarios against WireMock, but only once executed.
- Under 3.0a, append the note: "Replay handling not needed (bidId is per-request, never retried); single-flight and the @WebMvcTest security slice deferred — 401 covered by BDD."
- Under 3.0, append: "Also applied to `AuctionActivationService.activate`."

---

## Self-review notes

- **Coverage against roadmap Story 3:**

| Roadmap item | Task |
|---|---|
| 3.0 (and activation) | Task 1 |
| 3.0a | Task 5 (move into `BidService`), Task 8 (`Hooks`), Task 9 (replay/single-flight/slice rulings) |
| 3.1 | Tasks 2–3 (`DownstreamError` + two gateways instead of a Feign `ErrorDecoder`) |
| 3.2 | Task 3 |
| 3.3 | Task 5 |
| 3.4 | Task 7 |
| 3.5 | Task 6 |
| 3.6 | Task 8 (WireMock BDD; the real docker-compose contract run stays open) |

  Issue #122 scenarios:
  - 1 (first bid locks) and 3 (subsequent bid skips): `DepositGateTest` and BDD.
  - 2 (insufficient balance → 403, no row): Tasks 3, 5 and 8.
  - 4 (wallet down → 503): Tasks 3, 5 and 8.
  - 5 (event + cache update): Tasks 4 and 5.
- **Deliberate deviations:**
  - Error mapping uses explicit gateways plus `DownstreamError` instead of a global Feign `ErrorDecoder`. The same status code, such as a 400 or 404, means different things for wallet and auction, so mapping per call site is clearer and easier to test.
  - The issue's "GET deposit-lock then POST" is replaced by a Redis flag plus the idempotent POST, per spec §3.
- **Known risk carried forward:** a DB connection is held across the apply-bid Feign call. It is bounded by the 2 s read timeout on the call and the 1 s lock timeout in auction-service.
