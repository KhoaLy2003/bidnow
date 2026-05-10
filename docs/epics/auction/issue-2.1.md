### **Issue 2.1: Public Auction Listing & Discovery**

**Story ID:** `AUCTION-201`
**Issue Type:** Feature
**Priority:** P0 - Critical

**As a** guest or registered user
**I want to** browse all active auctions with filtering and sorting
**So that** I can discover items I'm interested in bidding on

#### Acceptance Criteria

**Scenario 1: Browse All Active Auctions (Default View)**

- **Given** I visit the auction listing page
- **When** I make a GET request to `/api/v1/auctions/public` without any filters
- **Then** the system should:
  - Return only auctions where status = `ACTIVE`
  - Exclude soft-deleted records (deleted_at IS NULL)
  - Default sort by end_time ascending (ending soonest first)
  - Include per auction:
    - Auction ID, title, primary image thumbnail (300x300px)
    - Current price, starting price
    - Total bids count
    - Time remaining (calculated from end_time)
    - Category name
    - Seller display name (from User Service - cached or joined)
  - Paginate with default page size = 24
  - Return pagination metadata (current page, total pages, total items)

**Response Format:**

```json
{
  "data": [
    {
      "id": 123,
      "title": "iPhone 15 Pro - Unlocked",
      "imageUrl": "https://res.cloudinary.com/bidnow/image/upload/c_thumb,w_300/...",
      "currentPrice": 850.0,
      "startingPrice": 500.0,
      "totalBids": 23,
      "timeRemaining": "2h 15m",
      "endTime": "2026-04-29T16:30:00Z",
      "category": {
        "id": 1,
        "name": "Electronics",
        "slug": "electronics"
      },
      "seller": {
        "id": 456,
        "displayName": "JohnDoe123"
      }
    }
  ],
  "pagination": {
    "currentPage": 1,
    "pageSize": 24,
    "totalPages": 12,
    "totalItems": 287
  },
  "filters": {
    "availableCategories": [
      { "id": 1, "name": "Electronics", "count": 145 },
      { "id": 2, "name": "Fashion", "count": 89 }
    ]
  }
}
```

---

**Scenario 2: Filter by Category**

- **Given** I am on the auction listing page
- **When** I select a category filter (e.g., "Electronics")
- **Then** the system should:
  - Make GET request: `/api/v1/auctions/public?category=electronics`
  - Return only auctions where category_id matches
  - Maintain all other default behaviors (pagination, sorting, active only)
  - Update `availableCategories` counts dynamically

**Multiple Category Filter:**

- Support: `/api/v1/auctions/public?category=electronics,fashion` (OR logic)

---

**Scenario 3: Filter by Price Range**

- **Given** I want to find auctions within my budget
- **When** I apply price filters
- **Then** the system should:
  - Accept query params: `?minPrice=100&maxPrice=500`
  - Filter where current_price BETWEEN minPrice AND maxPrice
  - Return matching auctions

**Validation:**

- minPrice ≥ 0
- maxPrice > minPrice
- Return `400 Bad Request` if validation fails

---

**Scenario 4: Filter by Time Range (Ending Soon)**

- **Given** I want to see auctions ending soon
- **When** I apply time filter
- **Then** the system should:
  - Accept query param: `?endingSoon=true`
  - Filter where end_time BETWEEN now AND (now + 24 hours)
  - Default sort by end_time ascending (most urgent first)

**Alternative Time Filters:**

- `?endingIn=1h` - Ending within 1 hour
- `?endingIn=6h` - Ending within 6 hours
- `?endingIn=24h` - Ending within 24 hours

---

**Scenario 5: Sort Options**

- **Given** I want to sort auctions differently
- **When** I select a sort option
- **Then** the system should support:

| Sort Value              | SQL ORDER BY         | Use Case                     |
| ----------------------- | -------------------- | ---------------------------- |
| `ending-soon` (default) | `end_time ASC`       | Find auctions about to close |
| `newly-listed`          | `created_at DESC`    | Find newest listings         |
| `price-low-high`        | `current_price ASC`  | Find bargains                |
| `price-high-low`        | `current_price DESC` | Find premium items           |
| `most-bids`             | `total_bids DESC`    | Find popular items           |

**Query Param:** `?sort=price-low-high`

---

**Scenario 6: Combined Filters**

- **Given** I want precise results
- **When** I apply multiple filters and sort
- **Then** the system should:
  - Support chaining: `/api/v1/auctions/public?category=electronics&minPrice=100&maxPrice=500&endingSoon=true&sort=price-low-high&page=2`
  - Apply ALL filters with AND logic
  - Return matching results

---

**Scenario 7: Empty Results**

- **Given** my filters yield no results
- **When** I apply filters that match 0 auctions
- **Then** the system should:
  - Return `200 OK` with empty `data` array
  - Return pagination: `{"totalItems": 0, "totalPages": 0}`
  - Frontend shows: "No auctions found. Try adjusting your filters."

---

**Scenario 8: Performance Requirements**

- **Given** the listing page is a high-traffic endpoint
- **Then** the system must:
  - Respond within 300ms for queries returning <100 results
  - Use database indexes for all filter columns
  - Cache category counts in Redis (TTL 5 minutes)
  - Implement pagination cursor (offset/limit) efficiently

---

#### Technical Implementation Notes

**API Endpoint:**

```
GET /api/v1/auctions/public
```

**Query Parameters:**

| Parameter    | Type         | Default       | Description                       |
| ------------ | ------------ | ------------- | --------------------------------- |
| `page`       | Integer      | 1             | Page number (1-indexed)           |
| `pageSize`   | Integer      | 24            | Items per page (max 100)          |
| `category`   | String/Array | null          | Category slug(s), comma-separated |
| `minPrice`   | Decimal      | null          | Minimum current price             |
| `maxPrice`   | Decimal      | null          | Maximum current price             |
| `endingSoon` | Boolean      | false         | Show auctions ending in 24h       |
| `endingIn`   | String       | null          | `1h`, `6h`, `24h`                 |
| `sort`       | String       | `ending-soon` | Sort order                        |
| `q`          | String       | null          | Search query (used in Issue 5.2)  |

**Caching Strategy:**

```java
@Cacheable(value = "auction-listings", key = "#criteria", unless = "#result.isEmpty()")
public Page<AuctionListingDTO> getPublicAuctions(AuctionSearchCriteria criteria) {
    // ... implementation
}
```

**Cache Invalidation:**

- Evict cache when new auction is created
- Evict cache when bid is placed (updates current_price and total_bids)
- TTL: 2 minutes (balance between freshness and performance)

---

#### Testing Requirements

**Unit Tests:**

- ✅ Test each filter independently (category, price, time)
- ✅ Test combined filters (all possible combinations)
- ✅ Test sort orders (verify SQL ORDER BY clause)
- ✅ Test pagination edge cases (first page, last page, out of bounds)
- ✅ Test empty results scenario

**Integration Tests:**

- ✅ Full API request/response with test database
- ✅ Verify index usage with EXPLAIN ANALYZE
- ✅ Test cache hit/miss scenarios

**Performance Tests:**

- ✅ Load test with 10,000 active auctions
- ✅ Verify response time <300ms for typical queries
- ✅ Stress test with 1000 concurrent requests

---

#### UI/UX Considerations

**Desktop Layout:**

- Grid view: 4 columns on large screens, 3 on medium
- Card design: Image (60% height), Title (2 lines max, truncate), Price (bold, large), Bids count (icon + number), Time remaining (countdown, red if <1h)

**Mobile Layout:**

- List view: 1 column, horizontal card layout
- Image on left (square thumbnail), details on right

**Filters Sidebar (Desktop) / Drawer (Mobile):**

- Collapsible sections: Category, Price Range, Ending Soon
- "Apply Filters" button (updates URL params)
- "Clear All" button

**Sort Dropdown:**

- Position: Top right of listing area
- Options: Ending Soon (default), Newly Listed, Price: Low to High, Price: High to Low, Most Bids

**Empty State:**

- Illustration or icon
- "No auctions found matching your criteria"
- "Clear Filters" button
- Suggested actions: Browse all categories, Sign up to create alerts

---

#### SEO Optimization

**Server-Side Rendering (Next.js):**

- Render initial page with auctions data on server
- Meta tags: `<title>`, `<meta name="description">`, Open Graph tags
- Canonical URLs for filtered pages

**Structured Data (JSON-LD):**

```json
{
  "@context": "https://schema.org",
  "@type": "ItemList",
  "itemListElement": [
    {
      "@type": "Product",
      "name": "iPhone 15 Pro - Unlocked",
      "image": "https://...",
      "offers": {
        "@type": "Offer",
        "price": "850.00",
        "priceCurrency": "USD",
        "availability": "https://schema.org/InStock"
      }
    }
  ]
}
```

**Sitemap:**

- Generate dynamic sitemap.xml with all active auction URLs
- Update daily via cron job

---

#### Dependencies

- ✅ Database: `auction_items`, `auction_categories`, `auction_images` tables
- ⚠️ Redis: For caching (optional but recommended)
- ⚠️ CDN: Cloudinary for image delivery optimization

---

#### Definition of Done

- [ ] API endpoint implemented with all filters and sort options
- [ ] Pagination logic correct (offset/limit)
- [ ] Database indexes created and verified
- [ ] Caching layer implemented (Redis)
- [ ] Response time <300ms verified via performance test
- [ ] Unit test coverage ≥ 80%
- [ ] Integration tests passing
- [ ] Load testing completed (1000 concurrent users)
- [ ] API documentation (Swagger) generated
- [ ] Frontend integration tested (Next.js SSR)
- [ ] SEO tags and structured data implemented
- [ ] Code reviewed and approved
- [ ] Deployed to staging environment
- [ ] Manual QA completed (cross-browser, mobile responsive)

---

#### Estimated Effort

**Story Points:** 8 (Medium-Large)
**Development:** 4 days
**Testing:** 2 days
**Frontend Integration:** 2 days
**Total:** 8 days
