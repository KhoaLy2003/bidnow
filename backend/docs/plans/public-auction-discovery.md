# Feature Plan: Public Auction Listing & Discovery

**Issue:** [#31 [STORY][AUCTION] Public Auction Listing & Discovery](https://github.com/KhoaLy2003/bidnow/issues/31)
**Milestone:** v0.2-auction-wallet
**Assignee:** hiepnguyen06

---

## Context

Buyers (guests and registered users) currently have no way to browse the auction catalogue. The only existing public
endpoint is `GET /api/v1/auctions/public/{id}`, which requires knowing an auction ID in advance. This feature implements
the public discovery layer — a browsable, filterable, sortable listing of all ACTIVE auctions — forming the primary
entry point for buyers on the platform.

---

## Spec Requirements (Issue #31)

### Scenario 1 — Public Listing

- Return `ACTIVE` auctions only.
- Each result includes: primary image, current price, bid count, time remaining.
- Default sort: `end_time ASC` (soonest ending first).
- Paginated.

### Scenario 2 — Filtering

| Filter                        | Notes                           |
|-------------------------------|---------------------------------|
| `categoryId` / `categorySlug` | Either identifier accepted      |
| `minPrice` / `maxPrice`       | Based on `current_price`        |
| `endingSoon=true`             | Auctions ending within 24 hours |

### Scenario 3 — Sorting

| Sort key         | Behaviour            |
|------------------|----------------------|
| *(default)*      | `end_time ASC`       |
| `newly-listed`   | `created_at DESC`    |
| `price-low-high` | `current_price ASC`  |
| `price-high-low` | `current_price DESC` |
| `most-bids`      | `total_bids DESC`    |

### Scenario 4 — Performance

- p95 response time < 300 ms.
- Redis for category count caching.

### Technical Notes

- **Endpoint:** `GET /api/v1/auctions/public`
- SEO / SSR and JSON-LD are frontend concerns; backend exposes all data needed for those features.

---

## What Already Exists (No Duplication Needed)

| Component                                                                                     | Status   |
|-----------------------------------------------------------------------------------------------|----------|
| `AuctionItem` entity with all required fields                                                 | ✅ exists |
| `AuctionSummaryResponse` DTO (compact, with category + primaryImageUrl)                       | ✅ exists |
| `JpaSpecificationExecutor` on `AuctionItemRepository`                                         | ✅ exists |
| `SpecificationBuilder` utility (`withIfPresent`, `withLikeIfPresent`, `withIn`, `withIsNull`) | ✅ exists |
| Indexes: `(category_id)`, `(end_time WHERE status='ACTIVE')`, `(status)`, `(created_at DESC)` | ✅ exists |
| SecurityConfig: `GET /api/v1/auctions/public/**` is already permit-all                        | ✅ exists |
| `GET /api/v1/auctions/public/{id}` single-auction endpoint                                    | ✅ exists |
| `AuctionCategory` entity with `slug`, `isActive`, `displayOrder`                              | ✅ exists |
| `AuctionCategoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()`                        | ✅ exists |

---

## Proposed API

### Browse endpoint

```
GET /api/v1/auctions/public
```

**Query parameters:**

| Param             | Type    | Default        | Description                                                     |
|-------------------|---------|----------------|-----------------------------------------------------------------|
| `categoryId`      | UUID    | —              | Filter by category UUID                                         |
| `categorySlug`    | String  | —              | Filter by category slug (alternative to categoryId)             |
| `minPrice`        | Decimal | —              | Lower bound on `current_price`                                  |
| `maxPrice`        | Decimal | —              | Upper bound on `current_price`                                  |
| `endingSoon`      | Boolean | false          | Limit to auctions ending within 24 h                            |
| `keyword`         | String  | —              | Full-text match on `title`                                      |
| `buyNowAvailable` | Boolean | —              | When `true`, only return auctions that have a buy-now price set |
| `sortBy`          | Enum    | `END_TIME_ASC` | One of the values in the sort table above                       |
| `page`            | Int     | 0              | Zero-based page number                                          |
| `size`            | Int     | 20             | Page size (max 50)                                              |

**Response:** `PageResponse<AuctionSummaryResponse>` (existing wrapper + DTO, no new types needed).

`AuctionSummaryResponse` already exposes `endTime`, so clients can compute time-remaining client-side. Avoid adding a
`timeRemainingSeconds` field to the DTO — it would become stale inside any cached response.

### Category counts endpoint *(enhancement)*

```
GET /api/v1/auctions/public/category-counts
```

Returns the number of ACTIVE auctions per category. Cached in Redis. Used by the filter sidebar.

---

## Enhancements (Beyond Spec)

### E1 — Keyword / Title Search

**Why:** Buyers arrive via search intent ("vintage watch", "iPhone 15"). Without text search, the filter sidebar is the
only discovery path. Every major auction site (eBay, Catawiki) has a keyword box.

**Implementation:** Add optional `keyword` param. Use `SpecificationBuilder.withLikeIfPresent("title", keyword)`. The
`title` column is indexed via `idx_auction_items_status` scans; a `LIKE '%x%'` is acceptable at current scale. At higher
scale, migrate to PostgreSQL `tsvector` full-text search or Elasticsearch.

### E2 — Category Count Endpoint (Sidebar Facets)

**Why:** Filter UIs show counts per facet ("Electronics (42)"). Without this, the buyer doesn't know whether a filter
will return results, leading to empty-page frustration.

**Implementation:** `GET /api/v1/auctions/public/category-counts` returns
`List<{categoryId, categoryName, slug, count}>`. JPQL constructor expression query groups by category and counts ACTIVE,
non-deleted auctions. Result cached in Redis (TTL = 60 s).

### E3 — Category List Caching

**Why:** `GET /api/v1/categories` is called on every page load but categories almost never change. Currently uncached.

**Implementation:** `@Cacheable("categories")` on `AuctionCategoryServiceImpl.getCategories()`. TTL = 5 minutes.

### E4 — `buyNowAvailable` Filter

**Why:** Some buyers prefer certainty over bidding. Filtering to auctions with a buy-now price is a common UX pattern on
bidding platforms.

**Implementation:** Optional boolean `buyNowAvailable=true` param. Spec: `buyNowPrice IS NOT NULL`. Applied as a JPA
lambda predicate ANDed to the spec.

### E5 — Max Page Size Cap

**Why:** Without a cap, a caller can request `size=10000`, scanning the full table and timing out or exposing all
auction data in one call.

**Implementation:** `@Max(50)` on `size` in `PublicAuctionFilterRequest`. Returns 400 if exceeded.

---

## Edge Cases

| Case                                                 | Expected Behaviour                                                                  |
|------------------------------------------------------|-------------------------------------------------------------------------------------|
| No auctions match the filters                        | Return empty page (`content: []`, `totalElements: 0`) — never 404                   |
| `minPrice > maxPrice`                                | Return 400 Bad Request with validation error                                        |
| `categoryId` references a non-existent category      | Return empty page (treat as valid filter with zero results)                         |
| `categorySlug` AND `categoryId` both supplied        | Use `categoryId`; ignore `categorySlug` (precedence documented in OpenAPI)          |
| `categorySlug` references non-existent slug          | Return empty page                                                                   |
| `endingSoon=true` with no auctions ending in 24h     | Return empty page                                                                   |
| Auction transitions out of ACTIVE mid-response       | Safe — query is a DB snapshot; the auction disappears from next request             |
| `page` beyond total pages                            | Return empty page with correct `totalPages` meta                                    |
| `size=0`                                             | Return 400 (`@Min(1)` validation)                                                   |
| `size > 50`                                          | Return 400 (`@Max(50)` validation)                                                  |
| `keyword` is a single character or very short        | Allow; LIKE query handles it (may return many results, which is fine)               |
| `keyword` contains SQL special chars (`%`, `_`)      | SpecificationBuilder escapes wildcards before wrapping in `%…%`                     |
| Redis unavailable                                    | Browse endpoint still works — `@Cacheable` degrades gracefully, queries DB directly |
| Cache returns stale count after auction cancellation | Acceptable within TTL window (60 s); eventual consistency                           |
| `sortBy` value not in enum                           | Return 400 from Spring's `@RequestParam` binding                                    |

---

## Implementation Plan

### Files Created

| File                                                              | Purpose                                                                                    |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `auction-service/.../dto/request/PublicAuctionFilterRequest.java` | Filter DTO with validation constraints                                                     |
| `auction-service/.../domain/enums/AuctionSortBy.java`             | Sort enum: `END_TIME_ASC`, `NEWLY_LISTED`, `PRICE_LOW_HIGH`, `PRICE_HIGH_LOW`, `MOST_BIDS` |
| `auction-service/.../dto/response/CategoryCountResponse.java`     | `{categoryId, categoryName, slug, count}` — all-args constructor required for JPQL         |
| `auction-service/.../config/CacheConfig.java`                     | `@EnableCaching` + `RedisCacheManager` with per-cache TTLs                                 |

### Files Modified

| File                                                               | Change                                                                  |
|--------------------------------------------------------------------|-------------------------------------------------------------------------|
| `auction-service/.../service/AuctionService.java`                  | Added `browseAuctions` and `getCategoryAuctionCounts` method signatures |
| `auction-service/.../service/impl/AuctionServiceImpl.java`         | Implemented both methods                                                |
| `auction-service/.../controller/AuctionController.java`            | Added `GET /public` and `GET /public/category-counts` endpoints         |
| `auction-service/.../dto/response/AuctionSummaryResponse.java`     | Added `buyNowPrice` field                                               |
| `auction-service/.../mapper/AuctionMapper.java`                    | Added `buyNowPrice` mapping in `toSummaryResponse`                      |
| `auction-service/.../repository/AuctionCategoryRepository.java`    | Added `findBySlugAndIsActiveTrue`                                       |
| `auction-service/.../repository/AuctionItemRepository.java`        | Added JPQL `countByStatusGroupByCategory`                               |
| `auction-service/pom.xml`                                          | Added `spring-boot-starter-data-redis`                                  |
| `auction-service/src/main/resources/application.yml`               | Added Redis and cache config                                            |
| `auction-service/.../service/impl/AuctionCategoryServiceImpl.java` | Added `@Cacheable("categories")` to `getCategories()`                   |

### Key Implementation Details

**Sort resolver:**

```java
Sort sort = switch (sortBy) {
    case NEWLY_LISTED   -> Sort.by("createdAt").descending();
    case PRICE_LOW_HIGH -> Sort.by("currentPrice").ascending();
    case PRICE_HIGH_LOW -> Sort.by("currentPrice").descending();
    case MOST_BIDS      -> Sort.by("totalBids").descending();
    default             -> Sort.by("endTime").ascending();
};
```

**Specification building:**

```java
Specification<AuctionItem> spec = SpecificationBuilder.<AuctionItem>forEntity()
    .with("status", SearchOperator.EQUAL, AuctionStatus.ACTIVE)
    .withIsNull("deletedAt")
    .withIfPresent("category.id", SearchOperator.EQUAL, resolvedCategoryId)
    .withIfPresent("currentPrice", SearchOperator.GREATER_THAN_OR_EQUAL, filter.getMinPrice())
    .withIfPresent("currentPrice", SearchOperator.LESS_THAN_OR_EQUAL, filter.getMaxPrice())
    .withLikeIfPresent("title", filter.getKeyword())
    .withBetweenIfPresent("endTime", endingSoonFrom, endingSoonTo)
    .build();
// buyNowAvailable applied separately as a lambda predicate
```

**Redis cache names and TTLs:**
| Cache name | TTL | Content |
|------------|-----|---------|
| `categories` | 5 min | Full category list |
| `category-auction-counts` | 60 s | Active auction count per category |

**Category slug resolution:** When `categorySlug` is provided and no matching active category is found, the method
returns an empty `PageImpl` immediately — avoiding a query that would return all auctions due to a null category ID
filter.

**Image loading:** Images are batch-loaded via `findByAuctionIdInOrderByDisplayOrderAsc` after the main page query to
avoid N+1 queries. Only the first image per auction is used as the primary image.

---

## Verification

```bash
# Build
mvn clean install -pl common,auction-service -DskipTests

# Smoke tests (service running):
# 1. GET /api/v1/auctions/public                           → page of ACTIVE auctions, sorted end_time ASC
# 2. GET /api/v1/auctions/public?endingSoon=true           → only auctions ending < 24h
# 3. GET /api/v1/auctions/public?minPrice=100&maxPrice=500 → price-filtered results
# 4. GET /api/v1/auctions/public?categorySlug=electronics  → category-filtered results
# 5. GET /api/v1/auctions/public?sortBy=MOST_BIDS          → highest bid count first
# 6. GET /api/v1/auctions/public?keyword=watch             → title-match results
# 7. GET /api/v1/auctions/public?minPrice=500&maxPrice=100 → 400 Bad Request
# 8. GET /api/v1/auctions/public?size=200                  → 400 Bad Request (> 50 cap)
# 9. GET /api/v1/auctions/public/category-counts           → [{categoryId, count}], cached in Redis
# 10. No auth header required on any of the above
```
