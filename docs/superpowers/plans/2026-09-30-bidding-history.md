# Story 5 — BID-103: Bid History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a paginated public bid history for an auction and a "my bids" view for the signed-in bidder. Resolve bidder names and avatars with one batch lookup per page, never one call per bid.

**Architecture:**
- **user-service:** a new internal `POST /api/v1/users/internal/summaries` returns summaries for up to 100 user IDs in one query.
- **Name cache:** bidding-service's `UserSummaryCacheService.getAll` reads the cached summaries in one Redis `MGET`, fetches the misses in one Feign call, and caches the results.
- **History service:** `BidHistoryService` pages `bids` (newest first), resolves the page's distinct bidders once, and maps to `BidHistoryResponse`.
- **Endpoints:** `BidController` exposes `GET /api/v1/bids/auction/{auctionId}` (public) and `GET /api/v1/bids/auction/{auctionId}/my-bids` (authenticated).
- **Public access:** the history GET is public in bidding-service's `SecurityConfig` and in the gateway's `PUBLIC_PATHS`. The auction detail page is public.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Cloud Gateway (WebFlux), Spring Data JPA, Spring Data Redis, OpenFeign, Lombok, JUnit 5, Mockito, AssertJ, standalone MockMvc, Cucumber + WireMock.

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md` (§4 bidding-service and user-service contracts). Roadmap: `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md` (Story 5). Frontend contract to match: `frontend/types/ui/auction.ui.ts`, which is `Bid { id, auctionId, bidderId, amount, placedAt, isAutoBid }`. `isCurrentUser` and `isWinning` are derived client-side.

## Global Constraints

- **Scope:** user-service (batch endpoint), bidding-service (history), api-gateway (one public path plus a test), docs. Do not touch auction-service, wallet-service, media-service or common.
- **Endpoints:**
  - `GET /api/v1/bids/auction/{auctionId}?page=&size=` is public: no `X-User-Id` needed in bidding-service, and no JWT needed at the gateway.
  - `GET /api/v1/bids/auction/{auctionId}/my-bids?page=&size=` requires `X-User-Id`, which is 401 at both layers without it.
  - Both return `BaseResponse<PageResponse<BidHistoryResponse>>`.
  - `page` defaults to 0 and must be `>= 0`. `size` defaults to 20 and must be `1..100`. Violations return `400 INVALID_INPUT`.
  - Order: `created_at DESC`, then `amount DESC` as the tie-breaker.
  - An auction with no bids returns 200 with an empty page, never 404.
- **`BidHistoryResponse` JSON:** `id, auctionId, bidderId, bidderName, bidderAvatarUrl, amount, placedAt, isAutoBid, isAntiSnipingTriggered`.
  - The boolean JSON names must be exactly `isAutoBid` and `isAntiSnipingTriggered`. Use `@JsonProperty`, because Lombok's `isX` getters would otherwise serialize as `autoBid`.
  - `placedAt` is the bid's `created_at` (a JVM-zone `LocalDateTime` from `BaseEntity`), converted with `atZone(ZoneId.systemDefault()).toOffsetDateTime()`.
- **Name resolution:**
  - Exactly one `UserSummaryCacheService.getAll` call per page, and none when the page is empty.
  - Unknown or unavailable users get `bidderName = "Unknown bidder"` and `bidderAvatarUrl = null`.
  - History never fails because of user-service or Redis.
- **user-service batch:**
  - The request is `{userIds: [UUID…]}`: `@NotEmpty`, `@Size(max = 100)`.
  - One query, `findByUserIdIn`. Unknown IDs are omitted.
  - Duplicate IDs are de-duplicated.
  - It lives under `/api/v1/users/internal/**`, which is already `permitAll` in user-service and blocked at the gateway.
- **Redis key** for summaries: reuse `bidding:user:{userId}:summary` (JSON `UserSummaryResponse`) with the same TTL property as Story 3.
- **Do not run `git commit` or `git add`.** Leave changes uncommitted on the working branch.
- **Maven** from `backend/`:
  - Unit: `mvn -q -pl <module> -am test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`
  - Full: `mvn -q -pl <module> -am test`
  - BDD `-Pbdd` only if the controller says so.

---

## File Map

Paths are relative to `backend/`.

| File | Action | Responsibility |
|---|---|---|
| `user-service/src/main/java/com/bidnow/user/dto/request/UserSummariesRequest.java` | Create | Batch request body |
| `user-service/src/main/java/com/bidnow/user/repository/UserProfileRepository.java` | Modify | `findByUserIdIn` |
| `user-service/src/main/java/com/bidnow/user/service/UserProfileService.java` | Modify | `getUserSummaries` |
| `user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java` | Modify | Implementation |
| `user-service/src/main/java/com/bidnow/user/controller/UserProfileController.java` | Modify | `POST /internal/summaries` |
| `user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java` | Modify | Service tests |
| `user-service/src/test/java/com/bidnow/user/controller/UserProfileControllerTest.java` | Modify | Controller tests |
| `bidding-service/src/main/java/com/bidnow/bidding/dto/UserSummariesQuery.java` | Create | Feign body |
| `bidding-service/src/main/java/com/bidnow/bidding/feign/UserServiceClient.java` | Modify | `getUserSummaries` |
| `bidding-service/src/main/java/com/bidnow/bidding/service/UserSummaryCacheService.java` | Modify | `getAll` |
| `bidding-service/src/test/java/com/bidnow/bidding/service/UserSummaryCacheServiceTest.java` | Modify | `getAll` tests |
| `bidding-service/src/main/java/com/bidnow/bidding/repository/BidRepository.java` | Modify | Paged queries |
| `bidding-service/src/main/java/com/bidnow/bidding/dto/request/BidHistoryQuery.java` | Create | `page` / `size` |
| `bidding-service/src/main/java/com/bidnow/bidding/dto/response/BidHistoryResponse.java` | Create | History item |
| `bidding-service/src/main/java/com/bidnow/bidding/service/BidHistoryService.java` | Create | Paging + mapping |
| `bidding-service/src/test/java/com/bidnow/bidding/service/BidHistoryServiceTest.java` | Create | Service tests |
| `bidding-service/src/main/java/com/bidnow/bidding/controller/BidController.java` | Modify | Two GET endpoints |
| `bidding-service/src/test/java/com/bidnow/bidding/controller/BidControllerTest.java` | Modify | GET tests |
| `bidding-service/src/main/java/com/bidnow/bidding/config/SecurityConfig.java` | Modify | `permitAll` public history GET |
| `api-gateway/src/main/java/com/bidnow/gateway/filter/AuthenticationFilter.java` | Modify | Public path |
| `api-gateway/src/test/java/com/bidnow/gateway/filter/AuthenticationFilterTest.java` | Create | Public vs. my-bids |
| `bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidHistorySteps.java` | Create | BDD steps |
| `bidding-service/src/test/resources/features/bid-history.feature` | Create | BDD scenarios |
| repo root: `docs/architecture.md`, roadmap | Modify | Docs |

---

### Task 1: user-service — batch user summaries

**Files:**
- Create: `user-service/src/main/java/com/bidnow/user/dto/request/UserSummariesRequest.java`
- Modify: `user-service/src/main/java/com/bidnow/user/repository/UserProfileRepository.java`
- Modify: `user-service/src/main/java/com/bidnow/user/service/UserProfileService.java`
- Modify: `user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java`
- Modify: `user-service/src/main/java/com/bidnow/user/controller/UserProfileController.java`
- Test: `user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java`
- Test: `user-service/src/test/java/com/bidnow/user/controller/UserProfileControllerTest.java`

**Interfaces:**
- Produces:
  - `UserSummariesRequest` (`@Data @NoArgsConstructor @AllArgsConstructor`): `@NotEmpty @Size(max = 100) List<UUID> userIds`
  - `UserProfileRepository.findByUserIdIn(Collection<UUID> userIds): List<UserProfile>`
  - `UserProfileService.getUserSummaries(List<UUID> userIds): List<UserSummaryResponse>`
  - `POST /api/v1/users/internal/summaries` → `BaseResponse<List<UserSummaryResponse>>`

- [ ] **Step 1: Write the failing tests**

Add to `UserProfileServiceImplTest`. Add these imports if they are missing: `com.bidnow.user.domain.entity.UserProfile`, `java.util.List`, `org.mockito.ArgumentCaptor`, `java.util.Collection`.

```java
    @Test
    void getUserSummaries_returnsOnlyExistingUsersWithOneQuery() {
        UUID alice = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(userProfileRepository.findByUserIdIn(any())).thenReturn(List.of(
                UserProfile.builder().userId(alice).displayName("Alice").avatarUrl("https://cdn/a.png").build()));

        List<UserSummaryResponse> result = userProfileService.getUserSummaries(List.of(alice, unknown, alice));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(alice);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
        assertThat(result.get(0).getAvatarUrl()).isEqualTo("https://cdn/a.png");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(userProfileRepository).findByUserIdIn(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(alice, unknown);
    }
```

If `any`, `verify` or `assertThat` are not already statically imported, add them from Mockito and AssertJ. Also add `import com.bidnow.common.dto.UserSummaryResponse;`.

Add to `UserProfileControllerTest`. Add these imports if missing: `org.springframework.http.MediaType`, `static ...MockMvcRequestBuilders.post`, `static org.mockito.Mockito.verifyNoInteractions`, `static org.mockito.ArgumentMatchers.anyList`, `java.util.List`, `java.util.UUID`, `com.bidnow.common.dto.UserSummaryResponse`.

```java
    @Test
    void getUserSummaries_returns200WithList() throws Exception {
        UUID alice = UUID.randomUUID();
        when(userProfileService.getUserSummaries(anyList())).thenReturn(List.of(
                UserSummaryResponse.builder().id(alice).name("Alice").build()));

        mockMvc.perform(post("/api/v1/users/internal/summaries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + alice + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(alice.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Alice"));
    }

    @Test
    void getUserSummaries_emptyList_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/internal/summaries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userProfileService);
    }

    @Test
    void getUserSummaries_moreThan100Ids_returns400() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/api/v1/users/internal/summaries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userProfileService);
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl user-service -am test -Dtest='UserProfileServiceImplTest,UserProfileControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`user-service/src/main/java/com/bidnow/user/dto/request/UserSummariesRequest.java`:

```java
package com.bidnow.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummariesRequest {

    @NotEmpty(message = "userIds must not be empty")
    @Size(max = 100, message = "At most 100 userIds per request")
    private List<UUID> userIds;
}
```

In `UserProfileRepository`, add `import java.util.Collection;` and `import java.util.List;`, and this method:

```java
    List<UserProfile> findByUserIdIn(Collection<UUID> userIds);
```

In `UserProfileService`, add `import java.util.List;` and this method:

```java
    List<UserSummaryResponse> getUserSummaries(List<UUID> userIds);
```

In `UserProfileServiceImpl`, add imports for `java.util.LinkedHashSet` and `java.util.List` if missing. Add this method after `getUserSummary`:

```java
    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getUserSummaries(List<UUID> userIds) {
        return userProfileRepository.findByUserIdIn(new LinkedHashSet<>(userIds)).stream()
                .map(profile -> UserSummaryResponse.builder()
                        .id(profile.getUserId())
                        .name(profile.getDisplayName())
                        .avatarUrl(profile.getAvatarUrl())
                        .build())
                .toList();
    }
```

In `UserProfileController`, add `import com.bidnow.user.dto.request.UserSummariesRequest;` and `import java.util.List;`. Add this method after `getUserSummary`:

```java
    @Operation(summary = "Get user summaries by IDs (Internal)", hidden = true)
    @PostMapping("/internal/summaries")
    public ResponseEntity<BaseResponse<List<UserSummaryResponse>>> getUserSummaries(
            @Valid @RequestBody UserSummariesRequest request) {
        return ResponseEntity.ok(BaseResponse.success(userProfileService.getUserSummaries(request.getUserIds())));
    }
```

- [ ] **Step 4: Run the tests and the user-service suite**

Run: `mvn -q -pl user-service -am test -Dtest='UserProfileServiceImplTest,UserProfileControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

Run: `mvn -q -pl user-service -am test`
Expected: BUILD SUCCESS.

---

### Task 2: bidding-service — batch summary lookup with cache

**Files:**
- Create: `bidding-service/src/main/java/com/bidnow/bidding/dto/UserSummariesQuery.java`
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/feign/UserServiceClient.java`
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/service/UserSummaryCacheService.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/service/UserSummaryCacheServiceTest.java`

**Interfaces:**
- Consumes: the Task 1 contract `POST /api/v1/users/internal/summaries`, and the existing `UserSummaryCacheService` (`key`, `ttl`, `objectMapper`, `redisTemplate`).
- Produces:
  - `record UserSummariesQuery(List<UUID> userIds)`
  - `UserServiceClient.getUserSummaries(UserSummariesQuery): BaseResponse<List<UserSummaryResponse>>`
  - `UserSummaryCacheService.getAll(Collection<UUID> userIds): Map<UUID, UserSummaryResponse>`. It never throws. Users that cannot be resolved are absent from the map.

- [ ] **Step 1: Write the failing tests**

Add to `UserSummaryCacheServiceTest`, adding these imports if missing: `java.util.List`, `java.util.Map`, `java.util.Arrays`, `com.bidnow.bidding.dto.UserSummariesQuery`, `static org.mockito.ArgumentMatchers.anyList`, `static org.mockito.ArgumentMatchers.argThat`.

```java
    private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String BOB_KEY = "bidding:user:00000000-0000-0000-0000-00000000000c:summary";

    private static UserSummaryResponse bob() {
        return UserSummaryResponse.builder().id(BOB_ID).name("Bob").build();
    }

    @Test
    void getAll_allCached_makesNoFeignCall() throws Exception {
        when(valueOps.multiGet(List.of(KEY, BOB_KEY))).thenReturn(Arrays.asList(
                objectMapper.writeValueAsString(alice()), objectMapper.writeValueAsString(bob())));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result).containsOnlyKeys(USER_ID, BOB_ID);
        assertThat(result.get(USER_ID).getName()).isEqualTo("Alice");
        verify(userServiceClient, never()).getUserSummaries(any());
    }

    @Test
    void getAll_partialHit_fetchesOnlyMissesInOneCallAndCachesThem() throws Exception {
        when(valueOps.multiGet(List.of(KEY, BOB_KEY))).thenReturn(Arrays.asList(
                objectMapper.writeValueAsString(alice()), null));
        when(userServiceClient.getUserSummaries(new UserSummariesQuery(List.of(BOB_ID))))
                .thenReturn(BaseResponse.success(List.of(bob())));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result.get(BOB_ID).getName()).isEqualTo("Bob");
        verify(userServiceClient).getUserSummaries(new UserSummariesQuery(List.of(BOB_ID)));
        verify(valueOps).set(BOB_KEY, objectMapper.writeValueAsString(bob()), Duration.ofSeconds(600));
    }

    @Test
    void getAll_userServiceDown_returnsCachedOnly() throws Exception {
        when(valueOps.multiGet(List.of(KEY, BOB_KEY))).thenReturn(Arrays.asList(
                objectMapper.writeValueAsString(alice()), null));
        when(userServiceClient.getUserSummaries(any())).thenThrow(new RuntimeException("down"));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result).containsOnlyKeys(USER_ID);
    }

    @Test
    void getAll_redisDown_fetchesAllFromUserService() {
        when(valueOps.multiGet(anyList())).thenThrow(new RedisConnectionFailureException("down"));
        when(userServiceClient.getUserSummaries(any())).thenReturn(BaseResponse.success(List.of(alice(), bob())));

        Map<UUID, UserSummaryResponse> result = service.getAll(List.of(USER_ID, BOB_ID));

        assertThat(result).containsOnlyKeys(USER_ID, BOB_ID);
    }

    @Test
    void getAll_emptyInput_touchesNothing() {
        assertThat(service.getAll(List.of())).isEmpty();
        verify(userServiceClient, never()).getUserSummaries(any());
        verify(valueOps, never()).multiGet(anyList());
    }

    @Test
    void getAll_duplicateIds_areLookedUpOnce() throws Exception {
        when(valueOps.multiGet(List.of(KEY))).thenReturn(Arrays.asList(objectMapper.writeValueAsString(alice())));

        assertThat(service.getAll(List.of(USER_ID, USER_ID))).containsOnlyKeys(USER_ID);
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl bidding-service -am test -Dtest=UserSummaryCacheServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`bidding-service/src/main/java/com/bidnow/bidding/dto/UserSummariesQuery.java`:

```java
package com.bidnow.bidding.dto;

import java.util.List;
import java.util.UUID;

/** Body of user-service's internal POST /api/v1/users/internal/summaries (max 100 ids). */
public record UserSummariesQuery(List<UUID> userIds) {
}
```

In `UserServiceClient`, add the imports `com.bidnow.bidding.dto.UserSummariesQuery`, `org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.bind.annotation.RequestBody` and `java.util.List`, then add this method:

```java
    @PostMapping("/api/v1/users/internal/summaries")
    BaseResponse<List<UserSummaryResponse>> getUserSummaries(@RequestBody UserSummariesQuery query);
```

In `UserSummaryCacheService`, add the imports `com.bidnow.bidding.dto.UserSummariesQuery`, `java.util.ArrayList`, `java.util.Collection`, `java.util.HashMap`, `java.util.LinkedHashSet`, `java.util.List` and `java.util.Map`, then add this method:

```java
    /**
     * Resolves many users at once: one Redis MGET, then one user-service batch call for the misses.
     * Never throws; users that cannot be resolved are simply absent from the result.
     */
    public Map<UUID, UserSummaryResponse> getAll(Collection<UUID> userIds) {
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(userIds));
        Map<UUID, UserSummaryResponse> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<UUID> misses = new ArrayList<>();
        List<String> cached = multiGet(ids);
        for (int i = 0; i < ids.size(); i++) {
            UserSummaryResponse summary = cached == null ? null : parse(cached.get(i));
            if (summary != null && hasName(summary)) {
                result.put(ids.get(i), summary);
            } else {
                misses.add(ids.get(i));
            }
        }
        if (misses.isEmpty()) {
            return result;
        }
        try {
            BaseResponse<List<UserSummaryResponse>> response = userServiceClient.getUserSummaries(new UserSummariesQuery(misses));
            List<UserSummaryResponse> fetched = response == null || response.getData() == null ? List.of() : response.getData();
            for (UserSummaryResponse summary : fetched) {
                if (summary.getId() != null && hasName(summary)) {
                    result.put(summary.getId(), summary);
                    writeCache(summary.getId(), summary);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("user-service batch summary lookup failed for {} users: {}", misses.size(), ex.getMessage());
        }
        return result;
    }

    private List<String> multiGet(List<UUID> ids) {
        try {
            return redisTemplate.opsForValue().multiGet(ids.stream().map(UserSummaryCacheService::key).toList());
        } catch (RuntimeException ex) {
            log.warn("User summary cache MGET failed: {}", ex.getMessage());
            return null;
        }
    }

    private UserSummaryResponse parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, UserSummaryResponse.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
```

`hasName`, `writeCache` and `key` already exist from Story 3. Reuse them.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `mvn -q -pl bidding-service -am test -Dtest=UserSummaryCacheServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (7 existing + 6 new).

---

### Task 3: `BidHistoryService` + repository queries + DTOs

**Files:**
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/repository/BidRepository.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/dto/request/BidHistoryQuery.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/dto/response/BidHistoryResponse.java`
- Create: `bidding-service/src/main/java/com/bidnow/bidding/service/BidHistoryService.java`
- Test: `bidding-service/src/test/java/com/bidnow/bidding/service/BidHistoryServiceTest.java`

**Interfaces:**
- Consumes: `UserSummaryCacheService.getAll` (Task 2), `Bid` (Story 2), common `PageResponse` / `PaginationMeta` / `PaginationUtils.toPageResponse(Page, List)`.
- Produces:
  - `BidRepository.findByAuctionId(UUID, Pageable): Page<Bid>` and `findByAuctionIdAndBidderId(UUID, UUID, Pageable): Page<Bid>`
  - `BidHistoryQuery` (`@Data`): `@Min(0) int page = 0`, `@Min(1) @Max(100) int size = 20`, and `Pageable toPageable()`
  - `BidHistoryResponse` (`@Data @Builder`), JSON-named per Global Constraints
  - `BidHistoryService.auctionHistory(UUID auctionId, BidHistoryQuery q)` and `myBids(UUID auctionId, UUID bidderId, BidHistoryQuery q)`, each returning `PageResponse<BidHistoryResponse>`

- [ ] **Step 1: Write the failing test**

`bidding-service/src/test/java/com/bidnow/bidding/service/BidHistoryServiceTest.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.request.BidHistoryQuery;
import com.bidnow.bidding.dto.response.BidHistoryResponse;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidHistoryServiceTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Mock
    private BidRepository bidRepository;
    @Mock
    private UserSummaryCacheService userSummaries;

    @InjectMocks
    private BidHistoryService service;

    private static Bid bid(UUID bidder, String amount, LocalDateTime at, boolean antiSnipe) {
        Bid bid = Bid.builder().id(UUID.randomUUID()).auctionId(AUCTION_ID).bidderId(bidder)
                .amount(new BigDecimal(amount)).antiSnipingTriggered(antiSnipe).build();
        bid.setCreatedAt(at);
        return bid;
    }

    private static BidHistoryQuery query(int page, int size) {
        BidHistoryQuery q = new BidHistoryQuery();
        q.setPage(page);
        q.setSize(size);
        return q;
    }

    @Test
    void auctionHistory_mapsBidsWithNamesFromOneBatchLookup() {
        List<Bid> bids = List.of(bid(BOB, "110.00", T0.plusMinutes(2), true),
                bid(ALICE, "105.00", T0.plusMinutes(1), false),
                bid(BOB, "100.00", T0, false));
        when(bidRepository.findByAuctionId(eq(AUCTION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(bids, PageRequest.of(0, 20), 3));
        when(userSummaries.getAll(any())).thenReturn(Map.of(
                ALICE, UserSummaryResponse.builder().id(ALICE).name("Alice").avatarUrl("https://cdn/a.png").build()));

        PageResponse<BidHistoryResponse> page = service.auctionHistory(AUCTION_ID, query(0, 20));

        assertThat(page.getData()).hasSize(3);
        BidHistoryResponse first = page.getData().get(0);
        assertThat(first.getBidderId()).isEqualTo(BOB);
        assertThat(first.getBidderName()).isEqualTo("Unknown bidder");
        assertThat(first.getBidderAvatarUrl()).isNull();
        assertThat(first.getAmount()).isEqualByComparingTo("110.00");
        assertThat(first.isAntiSnipingTriggered()).isTrue();
        assertThat(first.getPlacedAt()).isEqualTo(T0.plusMinutes(2).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        assertThat(page.getData().get(1).getBidderName()).isEqualTo("Alice");
        assertThat(page.getData().get(1).getBidderAvatarUrl()).isEqualTo("https://cdn/a.png");
        assertThat(page.getPagination().getTotal()).isEqualTo(3);
        assertThat(page.getPagination().getLimit()).isEqualTo(20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(userSummaries, times(1)).getAll(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void auctionHistory_requestsNewestFirstWithAmountTieBreak() {
        when(bidRepository.findByAuctionId(eq(AUCTION_ID), any(Pageable.class))).thenReturn(Page.empty());

        service.auctionHistory(AUCTION_ID, query(2, 50));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bidRepository).findByAuctionId(eq(AUCTION_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("amount")));
    }

    @Test
    void emptyAuction_returnsEmptyPageWithoutNameLookup() {
        when(bidRepository.findByAuctionId(eq(AUCTION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<BidHistoryResponse> page = service.auctionHistory(AUCTION_ID, query(0, 20));

        assertThat(page.getData()).isEmpty();
        assertThat(page.getPagination().getTotal()).isZero();
        verify(userSummaries, never()).getAll(any());
    }

    @Test
    void myBids_queriesByAuctionAndBidder() {
        when(bidRepository.findByAuctionIdAndBidderId(eq(AUCTION_ID), eq(ALICE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bid(ALICE, "105.00", T0, false)), PageRequest.of(0, 20), 1));
        when(userSummaries.getAll(any())).thenReturn(Map.of(
                ALICE, UserSummaryResponse.builder().id(ALICE).name("Alice").build()));

        PageResponse<BidHistoryResponse> page = service.myBids(AUCTION_ID, ALICE, query(0, 20));

        assertThat(page.getData()).extracting(BidHistoryResponse::getBidderId).containsExactly(ALICE);
        verify(bidRepository, never()).findByAuctionId(any(), any());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidHistoryServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`BidRepository`:

```java
package com.bidnow.bidding.repository;

import com.bidnow.bidding.domain.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    Page<Bid> findByAuctionId(UUID auctionId, Pageable pageable);

    Page<Bid> findByAuctionIdAndBidderId(UUID auctionId, UUID bidderId, Pageable pageable);
}
```

`bidding-service/src/main/java/com/bidnow/bidding/dto/request/BidHistoryQuery.java`:

```java
package com.bidnow.bidding.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Paging for bid history: newest first; amount breaks ties (bids on one auction only ever go up). */
@Data
public class BidHistoryQuery {

    @Min(value = 0, message = "page must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "size must be between 1 and 100")
    @Max(value = 100, message = "size must be between 1 and 100")
    private int size = 20;

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("amount")));
    }
}
```

`bidding-service/src/main/java/com/bidnow/bidding/dto/response/BidHistoryResponse.java`:

```java
package com.bidnow.bidding.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BidHistoryResponse {
    private UUID id;
    private UUID auctionId;
    private UUID bidderId;
    private String bidderName;
    private String bidderAvatarUrl;
    private BigDecimal amount;
    private OffsetDateTime placedAt;

    @JsonProperty("isAutoBid")
    private boolean autoBid;

    @JsonProperty("isAntiSnipingTriggered")
    private boolean antiSnipingTriggered;
}
```

`bidding-service/src/main/java/com/bidnow/bidding/service/BidHistoryService.java`:

```java
package com.bidnow.bidding.service;

import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.request.BidHistoryQuery;
import com.bidnow.bidding.dto.response.BidHistoryResponse;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Paged bid history; bidder names come from one batch lookup per page (never N+1). */
@Service
@RequiredArgsConstructor
public class BidHistoryService {

    private final BidRepository bidRepository;
    private final UserSummaryCacheService userSummaries;

    @Transactional(readOnly = true)
    public PageResponse<BidHistoryResponse> auctionHistory(UUID auctionId, BidHistoryQuery query) {
        return toResponse(bidRepository.findByAuctionId(auctionId, query.toPageable()));
    }

    @Transactional(readOnly = true)
    public PageResponse<BidHistoryResponse> myBids(UUID auctionId, UUID bidderId, BidHistoryQuery query) {
        return toResponse(bidRepository.findByAuctionIdAndBidderId(auctionId, bidderId, query.toPageable()));
    }

    private PageResponse<BidHistoryResponse> toResponse(Page<Bid> page) {
        if (page.isEmpty()) {
            return PaginationUtils.toPageResponse(page, List.of());
        }
        Map<UUID, UserSummaryResponse> bidders =
                userSummaries.getAll(page.getContent().stream().map(Bid::getBidderId).distinct().toList());
        List<BidHistoryResponse> items = page.getContent().stream().map(bid -> {
            UserSummaryResponse bidder = bidders.get(bid.getBidderId());
            return BidHistoryResponse.builder()
                    .id(bid.getId())
                    .auctionId(bid.getAuctionId())
                    .bidderId(bid.getBidderId())
                    .bidderName(bidder == null ? UserSummaryCacheService.UNKNOWN_BIDDER : bidder.getName())
                    .bidderAvatarUrl(bidder == null ? null : bidder.getAvatarUrl())
                    .amount(bid.getAmount())
                    .placedAt(bid.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime())
                    .autoBid(bid.isAutoBid())
                    .antiSnipingTriggered(bid.isAntiSnipingTriggered())
                    .build();
        }).toList();
        return PaginationUtils.toPageResponse(page, items);
    }
}
```

`UserSummaryCacheService.UNKNOWN_BIDDER` is package-private, and `BidHistoryService` is in the same package (`service`), so the reference compiles.

- [ ] **Step 4: Run the test and confirm it passes**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidHistoryServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 tests).

---

### Task 4: GET endpoints, security and the gateway public path

**Files:**
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/controller/BidController.java`
- Modify: `bidding-service/src/test/java/com/bidnow/bidding/controller/BidControllerTest.java`
- Modify: `bidding-service/src/main/java/com/bidnow/bidding/config/SecurityConfig.java`
- Modify: `api-gateway/src/main/java/com/bidnow/gateway/filter/AuthenticationFilter.java`
- Test: `api-gateway/src/test/java/com/bidnow/gateway/filter/AuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `BidHistoryService` (Task 3).
- Produces:
  - `GET /api/v1/bids/auction/{auctionId}`
  - `GET /api/v1/bids/auction/{auctionId}/my-bids`
  - The gateway path `/api/v1/bids/auction/*` becomes public (a single segment only, so `/my-bids` stays protected).

- [ ] **Step 1: Write the failing tests**

In `BidControllerTest`:

- Change the setup to also mock `BidHistoryService`:

  ```java
      @Mock
      private BidHistoryService bidHistoryService;
  ```

  and construct the controller as `new BidController(bidService, bidHistoryService)`.
- Add imports: `com.bidnow.bidding.service.BidHistoryService`, `com.bidnow.bidding.dto.request.BidHistoryQuery`, `com.bidnow.bidding.dto.response.BidHistoryResponse`, `com.bidnow.common.dto.PageResponse`, `com.bidnow.common.dto.PaginationMeta`, `java.util.List`, `static ...MockMvcRequestBuilders.get`.

Then add these tests:

```java
    private static PageResponse<BidHistoryResponse> onePage() {
        return PageResponse.<BidHistoryResponse>builder()
                .data(List.of(BidHistoryResponse.builder()
                        .id(UUID.randomUUID()).auctionId(AUCTION_ID).bidderId(BIDDER_ID).bidderName("Bob")
                        .amount(new BigDecimal("105.00")).placedAt(OffsetDateTime.parse("2026-10-01T12:00:00Z"))
                        .autoBid(false).antiSnipingTriggered(true).build()))
                .pagination(PaginationMeta.builder().page(0).limit(20).total(1).totalPages(1).build())
                .build();
    }

    @Test
    void auctionHistory_isPublicAndReturnsPage() throws Exception {
        when(bidHistoryService.auctionHistory(eq(AUCTION_ID), any(BidHistoryQuery.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].bidderName").value("Bob"))
                .andExpect(jsonPath("$.data.data[0].isAutoBid").value(false))
                .andExpect(jsonPath("$.data.data[0].isAntiSnipingTriggered").value(true))
                .andExpect(jsonPath("$.data.pagination.total").value(1));
    }

    @Test
    void auctionHistory_bindsPageAndSize() throws Exception {
        when(bidHistoryService.auctionHistory(eq(AUCTION_ID), any(BidHistoryQuery.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID).param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<BidHistoryQuery> query = ArgumentCaptor.forClass(BidHistoryQuery.class);
        verify(bidHistoryService).auctionHistory(eq(AUCTION_ID), query.capture());
        assertThat(query.getValue().getPage()).isEqualTo(2);
        assertThat(query.getValue().getSize()).isEqualTo(50);
    }

    @Test
    void auctionHistory_sizeOver100_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verifyNoInteractions(bidHistoryService);
    }

    @Test
    void auctionHistory_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID).param("page", "-1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bidHistoryService);
    }

    @Test
    void myBids_usesCallerFromHeader() throws Exception {
        when(bidHistoryService.myBids(eq(AUCTION_ID), eq(BIDDER_ID), any(BidHistoryQuery.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/v1/bids/auction/" + AUCTION_ID + "/my-bids").header("X-User-Id", BIDDER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].bidderId").value(BIDDER_ID.toString()));
    }
```

Create `api-gateway/src/test/java/com/bidnow/gateway/filter/AuthenticationFilterTest.java`:

```java
package com.bidnow.gateway.filter;

import com.bidnow.gateway.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private final AuthenticationFilter filter = new AuthenticationFilter(mock(JwtUtil.class));

    private static GatewayFilterChain passingChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    @Test
    void auctionBidHistory_isPublic() {
        GatewayFilterChain chain = passingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bids/auction/" + UUID.randomUUID()).build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void myBids_requiresToken() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bids/auction/" + UUID.randomUUID() + "/my-bids").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void placingABid_requiresToken() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/bids").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (the two-argument constructor doesn't exist yet).

Run: `mvn -q -pl api-gateway -am test -Dtest=AuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `auctionBidHistory_isPublic` FAILS with 401. The other two tests pass.

- [ ] **Step 3: Implement**

`BidController`:
- Add `private final BidHistoryService bidHistoryService;` after `bidService`.
- Add imports: `BidHistoryService`, `BidHistoryQuery`, `BidHistoryResponse`, `com.bidnow.common.dto.PageResponse`, `org.springframework.web.bind.annotation.GetMapping`, `org.springframework.web.bind.annotation.ModelAttribute`, `org.springframework.web.bind.annotation.PathVariable`.
- Add these methods:

```java
    @Operation(summary = "Bid history of an auction", description = "Public. Newest first, paginated (size ≤ 100).")
    @GetMapping("/auction/{auctionId}")
    public ResponseEntity<BaseResponse<PageResponse<BidHistoryResponse>>> auctionHistory(
            @PathVariable UUID auctionId, @Valid @ModelAttribute BidHistoryQuery query) {
        return ResponseEntity.ok(BaseResponse.success(bidHistoryService.auctionHistory(auctionId, query)));
    }

    @Operation(summary = "My bids on an auction", description = "The caller's own bids, newest first.")
    @GetMapping("/auction/{auctionId}/my-bids")
    public ResponseEntity<BaseResponse<PageResponse<BidHistoryResponse>>> myBids(
            @AuthenticatedUserId UUID bidderId, @PathVariable UUID auctionId,
            @Valid @ModelAttribute BidHistoryQuery query) {
        return ResponseEntity.ok(BaseResponse.success(bidHistoryService.myBids(auctionId, bidderId, query)));
    }
```

`SecurityConfig` (bidding-service): add `import org.springframework.http.HttpMethod;`. After the `PUBLIC_ENDPOINTS` matcher, add:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/bids/auction/*").permitAll()
```

`AuthenticationFilter` (api-gateway): in `PUBLIC_PATHS`, add `"/api/v1/bids/auction/*",` after `"/api/v1/auctions/public/**",`. Also add the comment `// public bid history (single segment only; /my-bids stays authenticated)` on the line above it.

- [ ] **Step 4: Run the tests and all three suites**

Run: `mvn -q -pl bidding-service -am test -Dtest=BidControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (13 existing + 5 new).

Run: `mvn -q -pl api-gateway -am test`
Expected: BUILD SUCCESS.

Run: `mvn -q -pl bidding-service -am test`
Expected: BUILD SUCCESS.

---

### Task 5: BDD — history end-to-end (write and compile)

**Files:**
- Create: `bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidHistorySteps.java`
- Create: `bidding-service/src/test/resources/features/bid-history.feature`

**Interfaces:**
- Consumes: the bidding BDD harness from Stories 2–3 (`Hooks`, `CommonSteps`, WireMock user-service URL, `JdbcTemplate`).
- Produces: none.

- [ ] **Step 1: Steps**

`bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidHistorySteps.java`:

```java
// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidHistorySteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class BidHistorySteps {

    private static final String SUMMARIES_PATH = "/api/v1/users/internal/summaries";

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Given("bidder {string} placed a bid of {string} on auction {string} {int} minutes ago")
    public void storedBid(String bidderId, String amount, String auctionId, int minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO bids (id, auction_id, bidder_id, amount, is_auto_bid, is_anti_sniping_triggered, created_at, updated_at)
                VALUES (?, ?::uuid, ?::uuid, ?, false, false, NOW() - (? * INTERVAL '1 minute'), NOW() - (? * INTERVAL '1 minute'))
                """, UUID.randomUUID(), auctionId, bidderId, new BigDecimal(amount), minutesAgo, minutesAgo);
    }

    @Given("user-service resolves user {string} as {string} in batch")
    public void userServiceBatch(String userId, String name) {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(SUMMARIES_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":200,\"message\":\"Success\",\"data\":[{\"id\":\"" + userId
                                + "\",\"name\":\"" + name + "\",\"avatarUrl\":null}]}")));
    }

    @When("an anonymous user requests the bid history of auction {string}")
    public void anonymousHistory(String auctionId) {
        ctx.setLastResponse(client.given().get("/api/v1/bids/auction/" + auctionId));
    }

    @When("user {string} requests their bids on auction {string}")
    public void myBids(String userId, String auctionId) {
        ctx.setLastResponse(client.given().header("X-User-Id", userId)
                .get("/api/v1/bids/auction/" + auctionId + "/my-bids"));
    }

    @When("an anonymous user requests their bids on auction {string}")
    public void anonymousMyBids(String auctionId) {
        ctx.setLastResponse(client.given().get("/api/v1/bids/auction/" + auctionId + "/my-bids"));
    }

    @Then("the history amounts should be {string}")
    public void historyAmounts(String csv) {
        List<String> amounts = ctx.getLastResponse().jsonPath().getList("data.data.amount", Object.class)
                .stream().map(a -> new BigDecimal(a.toString()).setScale(2).toPlainString()).toList();
        assertThat(amounts).containsExactly(csv.split(","));
    }

    @Then("user-service should have received {int} batch summary request(s)")
    public void batchCalls(int count) {
        WireMockSupport.SERVER.verify(count, postRequestedFor(urlEqualTo(SUMMARIES_PATH)));
    }
}
```

- [ ] **Step 2: Feature**

`bidding-service/src/test/resources/features/bid-history.feature`:

```gherkin
@bidding @history @regression
Feature: Bid history

  Scenario: Anyone can read an auction's bid history, newest first, with one name lookup
    Given bidder "550e8400-e29b-41d4-a716-446655440010" placed a bid of "100.00" on auction "e0000000-0000-0000-0000-000000000001" 3 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440011" placed a bid of "105.00" on auction "e0000000-0000-0000-0000-000000000001" 2 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440010" placed a bid of "110.00" on auction "e0000000-0000-0000-0000-000000000001" 1 minutes ago
    And user-service resolves user "550e8400-e29b-41d4-a716-446655440010" as "Bob" in batch
    When an anonymous user requests the bid history of auction "e0000000-0000-0000-0000-000000000001"
    Then the response status should be 200
    And the history amounts should be "110.00,105.00,100.00"
    And the response field "data.data[0].bidderName" should equal "Bob"
    And the response field "data.data[1].bidderName" should equal "Unknown bidder"
    And the response field "data.pagination.total" should equal "3"
    And user-service should have received 1 batch summary request

  Scenario: An auction without bids returns an empty page
    When an anonymous user requests the bid history of auction "e0000000-0000-0000-0000-000000000002"
    Then the response status should be 200
    And the response field "data.pagination.total" should equal "0"
    And user-service should have received 0 batch summary requests

  Scenario: My bids returns only the caller's bids
    Given bidder "550e8400-e29b-41d4-a716-446655440010" placed a bid of "100.00" on auction "e0000000-0000-0000-0000-000000000003" 2 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440011" placed a bid of "105.00" on auction "e0000000-0000-0000-0000-000000000003" 1 minutes ago
    And user-service resolves user "550e8400-e29b-41d4-a716-446655440010" as "Bob" in batch
    When user "550e8400-e29b-41d4-a716-446655440010" requests their bids on auction "e0000000-0000-0000-0000-000000000003"
    Then the response status should be 200
    And the history amounts should be "100.00"

  @negative
  Scenario: My bids requires the gateway user header
    When an anonymous user requests their bids on auction "e0000000-0000-0000-0000-000000000003"
    Then the response status should be 401
```

- [ ] **Step 3: Compile**

Run: `mvn -q -pl bidding-service -am test-compile`
Expected: BUILD SUCCESS. Run `-Pbdd` only if the controller says so.

---

### Task 6: Documentation

**Files** (repo root): `docs/architecture.md`, `docs/superpowers/plans/2026-09-29-bidding-epic-roadmap.md`

- [ ] **Step 1: architecture.md**

In the "Service-to-Service Internal APIs" table, add this row after the other user or auction rows (where it fits best):

```markdown
| User | `POST /api/v1/users/internal/summaries` `{userIds: [≤100]}` | Bidding | Batch display name + avatar for bid history (one query; unknown ids omitted) |
```

In the "Bidding Service (public API & events)" endpoint table, add:

```markdown
| `GET /api/v1/bids/auction/{auctionId}?page=&size=` | public (gateway + service) | Bid history, newest first, `size` ≤ 100; bidder names batch-resolved (fallback "Unknown bidder") |
| `GET /api/v1/bids/auction/{auctionId}/my-bids?page=&size=` | `X-User-Id` | The caller's bids on the auction |
```

- [ ] **Step 2: Roadmap**

- In Story 5, tick 5.1, 5.2, 5.3 and 5.4.
- Under 5.4, append: `Gateway PUBLIC_PATHS gains /api/v1/bids/auction/* (single segment; /my-bids stays authenticated). BDD bid-history.feature written.`
- Under the "Story 5 notes" risk bullet, append: `placedAt uses bids.created_at in the JVM zone (converted via ZoneId.systemDefault()); the negative cache for "Unknown bidder" is still deferred.`

---

## Self-review notes

- **Coverage:**
  - Roadmap 5.1 is Task 1.
  - 5.2 is Task 2.
  - 5.3 is Task 3.
  - 5.4 is Task 4, plus the gateway path the roadmap did not list. It is needed, because the auction page is public and the gateway would otherwise return 401 for anonymous history reads.
- **Issue #123 scenarios:**
  - 1 (paginated history): Tasks 3–5.
  - 2 (my bids): Tasks 3–5.
  - 3 (batch name resolution, no N+1): Tasks 1–3.
  - 4 (empty history): Task 3 and BDD.
- **JSON naming trap:** Lombok `isX` booleans serialize as `x`. `@JsonProperty` pins `isAutoBid` and `isAntiSnipingTriggered`, and the controller test asserts both.
- **Conflicts with Story 4 when run in parallel:**
  - The only shared files are the docs (`architecture.md` and the roadmap), in different sections.
  - Story 4 touches bidding's `AuctionLifecycleConsumer`. Story 5 touches `BidController`, `SecurityConfig`, `BidRepository` and `UserSummaryCacheService`. These are separate files.
  - Both add new BDD files in different directories and services, except that bidding BDD shares `Hooks` and `CommonSteps`, which this plan does not modify.
