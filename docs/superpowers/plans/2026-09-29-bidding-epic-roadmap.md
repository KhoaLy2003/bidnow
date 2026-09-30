# Epic #120 — Bid Placement & Real-Time Auction Bidding: Implementation Plan (Roadmap)

> **For agentic workers:** This is the **epic-level** plan. It fixes the story order, the contracts between stories, and the task breakdown of each story. Before a story starts, expand it into its own detailed TDD plan at `docs/superpowers/plans/YYYY-MM-DD-<story>.md`, following the WALLET-30x plans (full code, one checkbox per step). Then execute that plan with superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Deliver end-to-end manual bidding. A bid is validated, backed by a deposit, applied atomically to the auction (including anti-sniping), recorded in the history, and pushed live to every open auction page.

**Architecture:** auction-service is the single source of truth for price, winner and end time, via one row-locked internal `apply-bid` command that also performs anti-snipe extension. bidding-service owns the `bids` ledger, orchestrates validation → wallet deposit lock → apply-bid, and publishes `BidPlacedEvent`. media-service turns Kafka events into STOMP messages on `/topic/auctions/{id}`. The frontend submits bids over REST and listens over STOMP.

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.0 (OpenFeign, Gateway, Eureka), PostgreSQL + Liquibase, Redis (spring-data-redis), Kafka, JobRunr, STOMP/SockJS. Frontend: Next.js 14 + TypeScript, Zustand, `@stomp/stompjs`, `sockjs-client`. Tests: JUnit 5, Mockito, AssertJ, standalone MockMvc, Cucumber (auction-service BDD).

**Spec:** `docs/superpowers/specs/2026-07-04-bidding-service-design.md`. Read §2 (why apply-bid is synchronous) and §8 (how each GitHub story changes) first.

## Global Constraints

- Each service keeps its own DB. There are no cross-DB queries. bidding-service never reads `auction_db`, it calls auction-service.
- auction-service is authoritative for `current_price`, `current_winner_id`, `total_bids`, `end_time`. bidding-service's Redis context is a pre-filter only.
- Every write to `auction_items` that depends on `status` or `end_time` (apply-bid, closure, seller cancel, admin cancel) must first load the row with `PESSIMISTIC_WRITE` (`findByIdForUpdate`).
- Internal endpoints live under `/api/v1/internal/**` (for users, `/api/v1/users/internal/**`), are `permitAll` in the owning service, and are never added to gateway routes.
- `bidderId` always comes from `@AuthenticatedUserId` (`X-User-Id`), never from a request body.
- Money is `BigDecimal` with scale 2 in the backend. The frontend decides and documents a single unit (see FE-101 Task 1).
- Kafka events are published in `afterCommit()` hooks, following the `AuctionClosureService` pattern.
- New/changed event DTOs go in `common/dto/event`. Fields are only added, never removed or renamed.
- Responses are `ResponseEntity<BaseResponse<T>>`, and errors are `BaseException` subclasses rendered as common `ErrorResponse`.
- Anti-snipe window/extension are config (`auction.anti-snipe.window-seconds: 120`, `extension-seconds: 300`) and never hardcoded.
- Conventional commits `feat(bidding): …`, `feat(auction): …`, etc. **Agents do not commit.** The user commits.
- Maven from `backend/`: `mvn -q -pl <service> -am test`.
- Update `/docs` when an API or the architecture changes (AGENTS.md directive 4). That work is in Story 8.

---

## Story map & sequencing

| # | Story | GitHub | Service(s) | Depends on | Status of original story |
|---|---|---|---|---|---|
| 1 | AUC-BID — auction internal bid-context + row-locked apply-bid + closure/cancel locking | #125 (amend) | auction-service, common | — | **Changed**: `extend` endpoint and `bid-placed-topic` consumer replaced |
| 2 | BID-101 — bidding bootstrap, schema, validation, context cache | #121 (amend) | bidding-service | — (Feign mocked) | Minor changes |
| 3 | BID-102 — place bid (deposit lock + apply-bid + event) | #122 (amend) | bidding-service, common | 1, 2, WALLET-303 ✅ | **Changed** flow |
| 4 | BID-104 — anti-sniping inside apply-bid + closure reschedule | #124 (amend) | auction-service, bidding-service, common | 1, 3 | **Changed**: extension moves to auction-service |
| 5 | BID-103 — bid history + user batch summaries | #123 (amend) | bidding-service, user-service | 3 | Open question resolved |
| 6 | RT-101 — real-time STOMP broadcast + gateway WS route | **new** | media-service, api-gateway | 3 (4 for extended events) | Missing from epic |
| 7 | FE-101 — frontend bid submit, history API, STOMP hook | **new** | frontend | 3, 5, 6 | Missing from epic |
| 8 | DOC — architecture, diagram 03, API docs | fold into DoD | docs | all | Epic DoD item |

```
 [1 AUC-BID] ──┐
               ├─► [3 BID-102] ─┬─► [4 BID-104] ─┐
 [2 BID-101] ──┘                ├─► [5 BID-103] ─┼─► [7 FE-101] ─► [8 DOC]
                                └─► [6 RT-101] ──┘
```

Stories 1 and 2 run in parallel, and so do 4, 5 and 6 once 3 has landed.

**Branching:** All stories are on one branch, `feature/bidding`, cut from `feature/wallet-deposit-lock` (WALLET-303..307 are not yet on `main`). Commit per story with conventional commits.

**GitHub housekeeping (the user does this, or asks me to):** amend #121–#125 with §8 of the spec, create issues for RT-101 and FE-101 under #120, and remove the "extend endpoint"/"BID_PLACED consumer" DoD items from #125.

---

## Story 1 — AUC-BID: auction-service internal bid API + locking (#125 amended)

**Deliverable:** bidding-service can read the bid context and atomically apply a bid. Closure and cancel can no longer interleave with a bid. There is no anti-snipe yet (Story 4).

**Files** (under `backend/auction-service/`):
- Create `src/main/resources/db/changelog/migrations/004-bid-tracking.sql`, which adds `last_bid_id UUID` to `auction_items`. Modify `db.changelog-master.xml`.
- Modify `domain/entity/AuctionItem.java` to add `lastBidId`.
- Modify `repository/AuctionItemRepository.java` to add `@Lock(PESSIMISTIC_WRITE) @Query … findByIdForUpdate(UUID id): Optional<AuctionItem>` (with `deletedAt IS NULL`).
- Create `dto/response/BidContextResponse.java`, `dto/request/ApplyBidRequest.java` and `dto/response/ApplyBidResponse.java` (fields per spec §4).
- Create `constant/AuctionErrorCodes.java` (`AUCTION_NOT_FOUND`, `AUCTION_NOT_OPEN`, `BID_TOO_LOW`, `BID_OWN_AUCTION`) and `exception/ConflictException.java` (409).
- Create `service/AuctionBidService.java` with `getBidContext(UUID)` and `applyBid(UUID, ApplyBidRequest)`, both `@Transactional`.
- Create `controller/AuctionInternalController.java` at `/api/v1/internal/auctions`.
- Modify `config/SecurityConfig.java` to `permitAll` on `/api/v1/internal/**`.
- Modify `service/AuctionClosureService.java`: `close()` uses `findByIdForUpdate`.
- Modify `service/impl/AuctionServiceImpl.java` (seller cancel) and `service/impl/AdminAuctionServiceImpl.java` (admin cancel) to use `findByIdForUpdate`.
- Tests: `AuctionBidServiceTest`, `AuctionInternalControllerTest`, a `AuctionClosureServiceTest` update, and a new BDD feature `features/internal-bid-api.feature` with steps.

**Interfaces produced:**
- `BidContextResponse { UUID auctionId; String title; UUID sellerId; AuctionStatus status; BigDecimal currentPrice, bidIncrement, depositAmount; UUID currentWinnerId; int totalBids; OffsetDateTime endTime; }`
- `ApplyBidRequest { @NotNull UUID bidId; @NotNull UUID bidderId; @NotNull @DecimalMin("0.01") BigDecimal amount; }`
- `ApplyBidResponse { UUID auctionId; BigDecimal currentPrice; UUID currentWinnerId; UUID previousWinnerId; int totalBids; OffsetDateTime endTime; boolean extended; int extensionCount; }`. `extended` is always false until Story 4.

**Tasks:**
- [x] **1.1 Persistence.** Add the migration, the entity field and `findByIdForUpdate`. Check: `mvn -pl auction-service -am test` stays green, and the BDD suite applies the migration.
- [x] **1.2 `AuctionBidService.getBidContext`.** Tests: returns mapped context; unknown/deleted → `NotFoundException(AUCTION_NOT_FOUND)`.
- [x] **1.3 `AuctionBidService.applyBid` validation under lock.** Tests:
  - uses `findByIdForUpdate`, not `findById`
  - status ≠ ACTIVE → 409 `AUCTION_NOT_OPEN`
  - `now >= endTime` → 409 (inject `Clock` so tests control time)
  - bidder == seller → 403 `BID_OWN_AUCTION`
  - first bid (`totalBids==0`) `amount == currentPrice` accepted, `amount < currentPrice` → 400
  - later bid `amount < currentPrice + increment` → 400 `BID_TOO_LOW` with `minimumBid`, and `==` is accepted
  - success updates price/winner/totalBids/lastBidId and returns `previousWinnerId`
  - replay with the same `bidId` as `lastBidId` returns the current state without incrementing
- [x] **1.4 `AuctionInternalController`.** Standalone MockMvc: GET 200/404, POST 200/400 (validation)/403/409, and error bodies carry `errorCode`.
- [x] **1.5 Lock closure and cancels.** Switch `close()`, seller cancel and admin cancel to `findByIdForUpdate`. Update the existing tests' stubs so they verify the lock method is used.
- [ ] **1.6 Concurrency integration test** (BDD or `@SpringBootTest` with a real Postgres from BDD support): two threads apply bids at the same price, so exactly one succeeds. Closure and apply-bid at `endTime` run concurrently, and the auction's winner equals the last *accepted* bid.
- [ ] **1.7** Run the full auction-service suite plus BDD.

---

## Story 2 — BID-101: bidding-service bootstrap, schema, validation, context cache (#121 amended)

**Deliverable:** bidding-service boots with DB, Redis, Feign and security. `BidValidationService` and `AuctionContextCacheService` are fully unit-tested. The `POST /api/v1/bids` skeleton performs pre-validation only and returns 501 until Story 3.

**Files** (under `backend/bidding-service/`):
- Modify `pom.xml`: add `spring-cloud-starter-openfeign`, `spring-boot-starter-data-redis`, and the test deps inherited from the parent.
- Modify `src/main/resources/application.yml`: add `spring.data.redis`, Feign timeouts (`spring.cloud.openfeign.client.config.default.connect-timeout: 1000`, `read-timeout: 2000`), and `bidding.cache.context-ttl-seconds: 600`.
- Modify `BiddingApplication.java` to add `@EnableFeignClients`.
- Create `db/changelog/db.changelog-master.xml` and `migrations/001-init-bids.sql` (spec §5).
- Create `domain/entity/Bid.java` (extends `BaseEntity`) and `repository/BidRepository.java`.
- Create `config/SecurityConfig.java` (`RoleHeaderFilter`; shared `PUBLIC_ENDPOINTS` only — history-GET `permitAll` deferred to Story 5).
- Create `constant/BiddingErrorCodes.java` and `exception/ConflictException.java`, `ServiceUnavailableException.java` (503).
- Create `feign/AuctionServiceClient.java`, with `getBidContext` for now (`applyBid` comes in Story 3).
- Create `dto/BidContext.java` (the cached record mirroring `BidContextResponse`) and `dto/request/PlaceBidRequest.java` (`auctionId`, `amount`).
- Create `service/AuctionContextCacheService.java` with `get(UUID)`, `put(BidContext)` and `evict(UUID)`.
- Create `service/BidValidationService.java` with `preValidate(BidContext, UUID bidderId, BigDecimal amount, Instant now)`.
- Create `controller/BidController.java` (`POST /api/v1/bids` skeleton).
- Kafka config deferred to Story 3; no `RedisConfig` — Boot's `StringRedisTemplate` + `ObjectMapper` are used; `PlaceBidResponse` deferred to Story 3; the BDD harness (Testcontainers Postgres/Redis + WireMock auction-service) is added here and reused by Stories 3–5.
- Modify the repo-root `docker-compose.yml` to replace `SPRING_REDIS_HOST` with `REDIS_HOST` for bidding-service (read by `application.yml` as `${REDIS_HOST:localhost}`); `bidding_db` already exists in `docker/postgres/init-db.sh`.

**Tasks:**
- [ ] **2.1 Dependencies + config + schema + entity/repo.** Check: `mvn -q -pl bidding-service -am test` compiles and passes, and the service starts against docker-compose Postgres/Redis with the Liquibase changelog applied.
- [x] **2.2 `BidValidationService`.** Boundary tests:
  - seller → 403 `BID_OWN_AUCTION`
  - SCHEDULED/COMPLETED/FAILED/CANCELLED → 409
  - `now == endTime` → 409, `now = endTime - 1ms` → OK
  - first bid `< currentPrice` → 400, `== currentPrice` → OK
  - `currentPrice=100, inc=5`: `104` → 400, `105` → OK
- [x] **2.3 `AuctionContextCacheService`.** Tests:
  - miss → Feign → `SET` with TTL
  - hit → no Feign call
  - `evict` deletes the key
  - Redis exception on read → falls through to Feign and does not fail
  - Feign 404 → `NotFoundException(AUCTION_NOT_FOUND)`
  - Feign 5xx/timeout → `ServiceUnavailableException`
- [x] **2.4 `BidController` skeleton + `SecurityConfig`.** MockMvc: validation (`amount` ≤ 0, null `auctionId`, >2 decimals) → 400; pre-validation errors map to the correct status. Missing `X-User-Id` → 401 is covered by the BDD scenario (not yet run).

---

## Story 3 — BID-102: place bid end-to-end (#122 amended)

**Deliverable:** `POST /api/v1/bids` accepts a bid through the spec §3 flow, persists it, updates the cache, and publishes `BidPlacedEvent`.

**Files:**
- `common/dto/event/BidPlacedEvent.java`: add `UUID bidId`, `Integer totalBids`, `OffsetDateTime endTime`. This is additive only, so rebuild `common` and check that media-service still compiles.
- bidding-service:
  - `feign/WalletServiceClient.java` (`POST /api/v1/internal/wallet/deposit-lock`)
  - `feign/UserServiceClient.java` (`GET /api/v1/users/internal/{id}/summary`)
  - `feign/FeignErrorDecoderConfig.java`, which maps downstream `ErrorResponse.errorCode` + status to bidding exceptions per spec §3 step 3/4
  - `AuctionServiceClient.applyBid`
  - `service/DepositGate.java`
  - `service/UserSummaryCacheService.java`
  - `service/BidService.java` (`placeBid(UUID bidderId, PlaceBidRequest)`)
  - `kafka/BidEventPublisher.java` (topic `bid-placed-topic`, key auctionId, CRITICAL log on send failure)
  - `kafka/AuctionLifecycleConsumer.java` (evicts the context on ended/cancelled)
  - `config/KafkaConsumerConfig.java` (copy the wallet-service pattern)
  - `BidController` wired to return 201

**Tasks:**
- [x] **3.0 Move in-transaction Kafka publishes** in seller cancel, admin cancel and admin force-close (auction-service) to afterCommit hooks, so they never run while holding the auction row lock; update AdminAuctionServiceImplTest/AuctionServiceImplTest to trigger afterCommit. Also applied to `AuctionActivationService.activate`.
- [x] **3.0a Carry-overs from Story 2.**
  - Move pre-validation, the stale-409 refresh, and the `clock.instant()` read from `BidController` into `BidService`.
  - Move the Cucumber `@Before` hook from `BidPlacementSteps` into a dedicated `bdd/steps/Hooks` class.
  - Because `Bid` is `Persistable` with `isNew = true`, a replayed `bidId` will throw `DataIntegrityViolationException` on INSERT. Handle it, or look up the bid before inserting.
  - Consider a single-flight lock for concurrent context-cache misses on hot auctions.
  - Add a `@WebMvcTest` security slice test (401 without `X-User-Id`, api-docs public) if feasible with `@EnableFeignClients`.
  - Replay handling not needed (bidId is per-request, never retried); single-flight and the @WebMvcTest security slice deferred — 401 covered by BDD.
- [x] **3.1 Feign clients + error decoder.** Tests per mapping: wallet 400 `INSUFFICIENT_BALANCE` → 403 `BID_INSUFFICIENT_BALANCE` with `errors` preserved; wallet 403 → 403; wallet 409 → 409; auction 409/400/403/404 pass through; any 5xx or `RetryableException` → 503.
- [x] **3.2 `DepositGate`.** Tests: flag present → no wallet call; flag absent → POST → flag set with TTL `endTime + 24h`; Redis down → still POSTs; wallet error propagates and leaves no flag.
- [x] **3.3 `BidService.placeBid`** (mock all collaborators). Tests:
  - happy path, in this order: pre-validate → gate → insert → applyBid → event after commit → cache overwrite with the returned state
  - `applyBid` 409 → exception, cache evicted, no event, no bid row (rollback asserted with a `TransactionTemplate`-backed test or verified `save` never flushed)
  - wallet 503 → no insert, no applyBid
  - event carries `previousHighestBidderId = previousWinnerId`, `bidderName` from `UserSummaryCacheService` (fallback "Unknown bidder"), and `auctionTitle` from the context
- [x] **3.4 `AuctionLifecycleConsumer`.** Ended/cancelled → `evict(auctionId)`.
- [x] **3.5 Controller.** 201 body shape and status mapping for all spec §4 error codes.
- [ ] **3.6 Contract test against real wallet + auction** (docker-compose): first bid locks the deposit, a second bid skips the wallet (verify via the wallet transactions count), insufficient balance → 403 with no bid row, wallet stopped → 503.

---

## Story 4 — BID-104: anti-sniping (#124 amended)

**Deliverable:** a bid inside the window extends the auction atomically. The closure job never closes an extended auction early. Events and flags reflect the extension.

**Files:**
- common: `dto/event/AuctionExtendedEvent.java` (new).
- auction-service:
  - `config/AntiSnipeProperties.java` (`@ConfigurationProperties("auction.anti-snipe")`) and `application.yml` entries
  - `repository/AuctionExtensionRepository.java`
  - `AuctionBidService.applyBid`: extension branch
  - `kafka/AuctionKafkaProducer.publishAuctionExtended` (`auction-extended-topic`)
  - `AuctionClosureService`:
    - `close()` re-checks `now < endTime` → reschedule
    - `closureJobId(auctionId, endTime)` becomes name-based on `auctionId + endTime.toEpochMilli()`, so the rescheduled job gets a fresh ID. Also update `scheduleClosureJob` callers: activation, startup recovery, and edit.
- bidding-service: `BidService` sets `bid.isAntiSnipingTriggered = result.extended()` before commit. `AuctionLifecycleConsumer` also listens to `auction-extended-topic` → evict.

**Tasks:**
- [x] **4.1 `AntiSnipeProperties` + extension in `applyBid`.** Boundary tests with a fixed `Clock`:
  - `endTime - now = 119s` → extended, `end = old + 300s`, `extension_count+1`, one `AuctionExtension` row with `previous/new/triggeredBy*`
  - `= 120s` → not extended (strictly less than the window)
  - `= 121s` → not extended
  - a second in-window bid after an extension extends again
  - `AuctionExtendedEvent` is registered afterCommit only when extended
- [x] **4.2 Closure reschedule.** Tests: `close()` at the original end time on an extended auction → no status change, and a new job is scheduled at the new `endTime` with a different ID; `close()` at/after `endTime` → closes as before; the job-ID derivation is deterministic per `(auctionId, endTime)`.
- [x] **4.3 bidding-service flag + consumer.** Tests: extended response → persisted bid flag true and event `isAntiSnipingTriggered=true`, `endTime` = new end; `auction-extended-topic` → evict.
- [ ] **4.4 Integration.** A bid inside the window moves `end_time` in auction_db and the closure fires at the new time, not the old. A bid outside the window leaves it unchanged. Extend the Story 1 concurrency test: closure racing an in-window bid → the auction stays ACTIVE and is extended. BDD scenarios written (auction-service internal-bid-api.feature @anti-snipe); close-race scenario now races admin force-close because close() defers before endTime.

---

## Story 5 — BID-103: bid history (#123 amended)

**Deliverable:** the paginated public history and "my bids" endpoints, with bidder names and avatars resolved without N+1 calls.

**Files:**
- user-service:
  - `dto/request/UserSummariesRequest.java` (`@NotEmpty @Size(max=100) List<UUID> userIds`)
  - `UserProfileController`: `POST /internal/summaries`
  - service method `getSummaries(List<UUID>)`, backed by a single `findAllById`
  - the user-service `SecurityConfig` must `permitAll` it (check the existing internal-endpoint matcher)
- bidding-service:
  - `UserServiceClient.getSummaries`
  - `UserSummaryCacheService.getAll(Set<UUID>)` (multi-get from Redis, batch-fetch misses)
  - `repository/BidRepository`: `findByAuctionId(UUID, Pageable)` and `findByAuctionIdAndBidderId(UUID, UUID, Pageable)`, sorted `createdAt DESC`
  - `dto/response/BidHistoryResponse.java`
  - `service/BidHistoryService.java`
  - `BidController` GET endpoints; `size` capped at 100, default 20

**Tasks:**
- [x] **5.1 user-service batch endpoint.** Tests: returns only existing users; 101 IDs → 400; empty → 400.
- [x] **5.2 `UserSummaryCacheService.getAll`.** Tests: all cached → zero Feign calls; partial → exactly one batch call for the misses; user-service down → names fall back to "Unknown bidder" (history must not 503).
- [x] **5.3 `BidHistoryService`.** Tests: sort order; my-bids filters by the caller; empty auction → empty `PageResponse`, not 404; exactly one batch lookup per page.
- [x] **5.4 Controller.** The public GET works without `X-User-Id`; my-bids without it → 401; the `PageResponse` shape matches `PaginationMeta`. Gateway PUBLIC_PATHS gains /api/v1/bids/auction/* (single segment; /my-bids stays authenticated). BDD bid-history.feature written. (test-compiled only; not yet executed with -Pbdd).

---

## Story 6 — RT-101: real-time STOMP broadcast (new)

**Deliverable:** browsers subscribed to `/topic/auctions/{id}` receive `BID_PLACED`, `AUCTION_EXTENDED`, `AUCTION_ENDED` and `AUCTION_CANCELLED` within about a second of commit. The outbid user gets a private `/user/queue/notifications` message.

**Files:**
- media-service:
  - `realtime/AuctionRealtimeMessage.java` (`type`, `payload`)
  - `realtime/AuctionRealtimeBroadcaster.java` (`SimpMessagingTemplate`)
  - `kafka/AuctionRealtimeConsumer.java`, with its own `groupId = "media-realtime-${random.uuid}"` and `auto-offset-reset: latest`. Keep it separate from `NotificationKafkaConsumer` so email/notification processing keeps its shared group.
  - `config/WebSocketConfig.java`: add a `HandshakeHandler` that builds a `Principal` from `X-User-Id` so `/user` destinations work
- api-gateway:
  - route `media-websocket`: `Path=/ws-notifications/**` → `lb://media-service (auto-upgraded to ws)`
  - CORS for the SockJS info endpoint
  - confirm the JWT filter reads the token from the `access_token` query param for WebSocket upgrades, since browsers cannot set headers on WebSockets. Add this if it is missing.

**Tasks:**
- [x] **6.1 Broadcaster.** Unit tests: each event maps to the right destination and payload, and an outbid message is sent only when `previousHighestBidderId != null && != bidderId`.
- [x] **6.2 Consumer.** Tests delegate per topic. A per-instance group ID is configured (assert on the annotation/property).
- [x] **6.3 Handshake principal.** Test: `X-User-Id` present → principal name = the userId; absent → anonymous, which can still subscribe to `/topic`.
- [ ] **6.4 Gateway route + WS auth.** Test with `WebTestClient` or a manual smoke run that `/ws-notifications/info` is reachable through 8080. (route + auth unit-tested; reachability through 8080 pending smoke 6.5)
- [ ] **6.5 Manual smoke:** two browser tabs, place a bid through Swagger, and both tabs receive `BID_PLACED`.

  Smoke steps (requires the full stack via docker-compose):
  1. Two browser tabs, each running `new SockJS('http://localhost:8080/ws-notifications?access_token=<jwt>')` with `@stomp/stompjs`, subscribing to `/topic/auctions/<id>` (one tab also to `/user/queue/notifications`).
  2. Place a bid via Swagger / `POST /api/v1/bids` as another user → both tabs receive `BID_PLACED`; the previous leader's tab receives `OUTBID`.
  3. Place a bid within the last 2 minutes → `AUCTION_EXTENDED` arrives. Admin cancel → `AUCTION_CANCELLED`.

---

## Story 7 — FE-101: frontend integration (new)

**Deliverable:** the auction detail page places real bids, shows the real history, and updates live over STOMP. Errors lead the user to the right action.

**Files** (under `frontend/`):
- `types/api/bid.api.ts` (`PlaceBidRequest`, `PlaceBidResponse`, `BidHistoryResponse`, `PageResponse<T>` reuse from `common.api.ts`, `BidErrorCode` union)
- `types/mappers/bid.mapper.ts` (API → `Bid`/`BidHistoryItem`, derives `isCurrentUser`/`isWinning`)
- `services/bid.service.ts` (`placeBid`, `getAuctionBids`, `getMyBids`, via `lib/apiClient.ts`)
- `services/auction.service.ts`: remove the mock `getBidHistory` and point callers at `bid.service`
- `components/auction/BidForm.tsx`: real submit, error UX
- `components/auction/BidHistory.tsx` (load more)
- `app/auctions/[id]/page.tsx`
- `hooks/useAuctionSocket.ts`: connects with `@stomp/stompjs` over native WebSocket (`/ws-notifications/websocket`)
- `store/auctionStore.ts`: add `setEndTime`/`setTotalBids` if they are missing
- `package.json`: add `@stomp/stompjs`, `vitest`
- `.env.example`: `NEXT_PUBLIC_WS_URL`

**Plan:** `docs/superpowers/plans/2026-09-30-frontend-bidding.md`

**Not done:** My-bids tab / dashboard (needs a cross-auction endpoint); auto-bid UI disabled until auto-bid ships.

**Tasks:**
- [x] **7.1 Settle money units.** `BidForm` documents cents, but `auction.mapper.ts` passes `dto.currentPrice` (dollars) straight through. Pick one unit (recommended: keep dollars end-to-end to match the API, and drop the ×100 in `BidForm`). Fix the mapper, `BidForm` and `formatCurrency` usage together, with a unit test on the mapper.
- [x] **7.2 Types + `bid.service` + mapper**, with tests for the mapper.
- [x] **7.3 `BidForm` submit.** Success → optimistic `setBid`. `BID_INSUFFICIENT_BALANCE` → inline message with a "Top up wallet" link to the wallet page, showing `required`/`availableBalance`. `BID_TOO_LOW` → refresh the minimum from `errors.minimumBid`. `AUCTION_NOT_OPEN` → disable the form. 503 → "Bidding temporarily unavailable, try again". 401 → login redirect.
- [x] **7.4 History.** Wire the page and "load more" to the real API, and add a "My bids" tab if the design has one.
- [x] **7.5 STOMP hook.** Subscribe to `/topic/auctions/{id}` and `/user/queue/notifications`. Map `BID_PLACED` → `setBid` + `addBidToHistory` + `setEndTime`, `AUCTION_EXTENDED` → `setEndTime` + a toast, and `AUCTION_ENDED`/`CANCELLED` → `setStatus(Closed)`. Reconnect with backoff, and unsubscribe on unmount.
- [ ] **7.6** `npm run lint && npm run build`. Manual E2E: two users bid against each other, one of them in the final two minutes, then top-up flow with an empty wallet.

---

## Story 8 — Documentation (epic DoD)

- [ ] `docs/architecture.md`: bidding-service responsibilities, the apply-bid synchronous call, new topics (`auction-extended-topic`), and the STOMP destinations.
- [ ] `docs/diagrams/03-bidding-antisniping-flow.md`: redraw per spec §3. Deposit lock replaces "Check Registration", apply-bid includes the extension, and the notification participant is media-service.
- [ ] `docs/functional.md` / `docs/business-clarifications.md`: first-bid minimum = starting price; the anti-snipe window is strictly less than 120s and extends by 300s.
- [ ] Mark the spec status as Approved and link the per-story plans from this roadmap.

---

## Risks carried forward

- **Per-instance media consumer groups** (Story 6). `media-realtime-<uuid>` accumulate four groups per instance start (one per listener) until Kafka's `offsets.retention` expires them — harmless, but visible in tooling. media-service's consumer now uses `ErrorHandlingDeserializer` (fixed in Story 6), so a poison message is logged and skipped.
- **SockJS XHR fallback needs sticky routing** before scaling media-service beyond one instance. The frontend uses the raw WebSocket transport, so this only affects non-browser SockJS clients.
- **Story 7 notes:** build the SockJS URL with a fresh token in `webSocketFactory` and `deactivate()` on logout; merge BID_PLACED/AUCTION_EXTENDED monotonically (max endTime / amount) and dedupe history by `bidId` (topics are not mutually ordered); WS `placedAt` is UTC while REST uses the JVM offset — compare as instants; STOMP heartbeats are not configured.
- **DB connection held across the apply-bid Feign call** (Story 3). Mitigated by the 2s read timeout. Watch the Hikari pool under load and move the insert after apply-bid (idempotent on `bidId`) if the pool saturates.
- **Kafka outage after commit:** the bid is correct, but the live push is lost. Clients resync on reload. #19 may add a replay.
- Remaining unlocked writers to `auction_items` (update/delete/publish/reject) can overwrite at the start-time boundary. Consider a `@Version` column (optimistic locking) as defence in depth.
- **Orphan deposit lock (cross-service):** a bid can pass pre-validation on a stale ACTIVE context after an admin cancel/force-close, lock a deposit, then be rejected by apply-bid; wallet has already settled the auction, so that lock is never released. Needs a wallet-side guard (settled-auction tombstone -> `DEPOSIT_LOCK_CLOSED`, or a sweep). bidding-service logs a WARN breadcrumb.
- **Unknown apply-bid outcome on timeout:** logged `CRITICAL: apply-bid outcome unknown`; a single idempotent replay with the same `bidId` (auction-service `last_bid_id`) could resolve most cases - deferred (plan: never retry).
- **Story 5 notes:** unify bid timestamps (one `Instant` per request; `bids.created_at` uses JVM-zone `BaseEntity`); negative-cache 'Unknown bidder' to avoid user-service latency on every bid when it is down. placedAt uses bids.created_at in the JVM zone (converted via ZoneId.systemDefault()); the negative cache for "Unknown bidder" is still deferred.
- Internal endpoints are protected at the gateway: `AuthenticationFilter` blocks `/api/v1/**/internal/**` and `/*/api/v1/**/internal/**` (service-name-prefixed discovery routes), so the new `/api/v1/internal/auctions/**` and `/api/v1/users/internal/summaries` endpoints need no gateway changes.
