### **Issue 2.2: Full-Text Search**

**Story ID:** `AUCTION-202`
**Issue Type:** Feature
**Priority:** P1 - High

**As a** guest or registered user
**I want to** search auctions by keyword
**So that** I can quickly find specific items I'm looking for

#### Acceptance Criteria

**Scenario 1: Basic Keyword Search**

- **Given** I want to find auctions related to a keyword
- **When** I enter a search query (e.g., "iPhone 15")
- **Then** the system should:
  - Make GET request: `/api/v1/auctions/public?q=iPhone+15`
  - Search in both `title` and `description` fields
  - Return auctions where ANY word matches (OR logic within words)
  - Rank results by relevance (exact title match > partial title match > description match)
  - Default sort by relevance (most relevant first)
  - Support all existing filters (category, price, time)

**Search Behavior:**

- Case-insensitive matching
- Partial word matching (e.g., "iPho" matches "iPhone")
- Ignore common stop words ("the", "a", "in", "on")
- Trim whitespace from query

---

**Scenario 2: Multi-Word Search**

- **Given** I search for multiple keywords
- **When** I enter "iPhone 15 Pro Max"
- **Then** the system should:
  - Split query into words: ["iPhone", "15", "Pro", "Max"]
  - Find auctions matching ANY of the words (OR logic)
  - Boost results matching ALL words higher in ranking
  - Return sorted by relevance score

**Ranking Logic:**

1. Exact title match (highest)
2. Title contains all words
3. Title contains some words
4. Description contains all words
5. Description contains some words

---

**Scenario 3: Search with Filters**

- **Given** I want to narrow search results
- **When** I combine search with filters
- **Then** the system should:
  - Support: `/api/v1/auctions/public?q=laptop&category=electronics&minPrice=500&maxPrice=1000`
  - Apply search first (find matching auctions)
  - Then apply filters to search results
  - Return paginated results

---

**Scenario 4: Empty Search Query**

- **Given** I submit an empty search
- **When** query parameter `q` is empty or whitespace only
- **Then** the system should:
  - Ignore the search parameter
  - Return all active auctions (same as no search)
  - Apply filters if any

---

**Scenario 5: No Results Found**

- **Given** my search yields no matches
- **When** I search for a rare term (e.g., "xyzabc123")
- **Then** the system should:
  - Return `200 OK` with empty `data` array
  - Return `{"totalItems": 0}`
  - Frontend shows: "No auctions found for 'xyzabc123'. Try different keywords or browse all auctions."

---

**Scenario 6: Performance Requirements**

- **Given** search is a critical UX feature
- **Then** the system must:
  - Respond within 300ms for queries on <10,000 active auctions
  - Use full-text search indexes (PostgreSQL `gin` or `gist`)
  - Cache popular search queries in Redis (TTL 10 minutes)

---

#### Technical Implementation Notes

**PostgreSQL Full-Text Search:**

**Enable Trigram Extension (for partial matching):**

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

**Add Full-Text Search Indexes:**

```sql
-- Trigram indexes for partial word matching
CREATE INDEX idx_auction_items_title_trgm
    ON auction_items USING gin(title gin_trgm_ops);

CREATE INDEX idx_auction_items_description_trgm
    ON auction_items USING gin(description gin_trgm_ops);

-- Optional: GIN index for full-text search (alternative approach)
ALTER TABLE auction_items ADD COLUMN search_vector tsvector;

CREATE INDEX idx_auction_items_search_vector
    ON auction_items USING gin(search_vector);

-- Populate search_vector column
UPDATE auction_items
SET search_vector = to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''));

-- Trigger to keep search_vector updated
CREATE TRIGGER tsvector_update BEFORE INSERT OR UPDATE ON auction_items
FOR EACH ROW EXECUTE FUNCTION
    tsvector_update_trigger(search_vector, 'pg_catalog.english', title, description);
```

**Java Implementation (Using Trigram Similarity):**

```java
public Page<AuctionListingDTO> searchAuctions(String query, AuctionSearchCriteria criteria) {
    if (query == null || query.trim().isEmpty()) {
        return getPublicAuctions(criteria); // Fallback to no search
    }

    String cleanQuery = query.trim().toLowerCase();

    Specification<Auction> spec = Specification
        .where(AuctionSpecifications.isActive())
        .and(AuctionSpecifications.notDeleted())
        .and(AuctionSpecifications.searchByKeyword(cleanQuery));

    // Apply additional filters
    if (criteria.getCategories() != null) {
        spec = spec.and(AuctionSpecifications.hasCategory(criteria.getCategories()));
    }
    // ... other filters

    // Sort by relevance (similarity score)
    Sort sort = Sort.by(
        Sort.Order.desc("titleSimilarity"),
        Sort.Order.desc("descriptionSimilarity"),
        Sort.Order.desc("created_at")
    );

    Pageable pageable = PageRequest.of(criteria.getPage() - 1, criteria.getPageSize(), sort);

    Page<Auction> auctions = auctionRepository.findAll(spec, pageable);
    return auctions.map(this::toListingDTO);
}
```

**Custom JPA Specification for Search:**

```java
public static Specification<Auction> searchByKeyword(String query) {
    return (root, cq, cb) -> {
        String pattern = "%" + query + "%";
        return cb.or(
            cb.like(cb.lower(root.get("title")), pattern),
            cb.like(cb.lower(root.get("description")), pattern)
        );
    };
}
```

**Alternative: Native Query with Similarity Ranking:**

```sql
SELECT
    a.*,
    similarity(a.title, :query) as title_similarity,
    similarity(a.description, :query) as desc_similarity,
    (similarity(a.title, :query) * 2 + similarity(a.description, :query)) as relevance_score
FROM auction_items a
WHERE
    a.status = 'ACTIVE'
    AND a.deleted_at IS NULL
    AND (
        a.title ILIKE '%' || :query || '%'
        OR a.description ILIKE '%' || :query || '%'
    )
ORDER BY relevance_score DESC, a.created_at DESC
LIMIT :pageSize OFFSET :offset;
```

---

**Caching Popular Searches:**

```java
@Cacheable(value = "search-results", key = "#query + '-' + #criteria")
public Page<AuctionListingDTO> searchAuctions(String query, AuctionSearchCriteria criteria) {
    // ... implementation
}
```

---

#### Testing Requirements

**Unit Tests:**

- ✅ Test single-word search
- ✅ Test multi-word search (OR logic)
- ✅ Test empty query (ignore search)
- ✅ Test search with special characters (sanitize input)
- ✅ Test search + filter combination

**Integration Tests:**

- ✅ Full API request/response with test database
- ✅ Verify index usage with EXPLAIN ANALYZE
- ✅ Test relevance ranking (manually verify top results)

**Performance Tests:**

- ✅ Search on database with 10,000 auctions
- ✅ Verify response time <300ms
- ✅ Test cache hit rate (>70% for popular queries)

**Security Tests:**

- ✅ Test SQL injection in search query (expect sanitization)
- ✅ Test XSS prevention in query parameter

---

#### UI/UX Considerations

**Search Bar:**

- Position: Top center of header (always visible)
- Placeholder: "Search auctions... (e.g., iPhone, vintage watch)"
- Auto-focus on page load (desktop only)
- Enter key triggers search
- Clear button (X icon) to reset search

**Search Suggestions (Future Enhancement):**

- As-you-type suggestions based on popular searches
- Typeahead dropdown with recent searches

**Search Results Page:**

- Display query: "Results for 'iPhone 15'" (33 items)
- Highlight matched keywords in title/description (bold)
- "No results" message with suggestions (browse categories, check spelling)

---

#### Future Enhancements (Out of Scope for MVP)

- **Elasticsearch Integration:** For advanced features like fuzzy matching, synonyms, typo tolerance
- **Search Analytics:** Track popular searches, no-result searches (improve category/tags)
- **Voice Search:** Speech-to-text integration (mobile)
- **Image Search:** Reverse image search (upload photo to find similar items)

---

#### Dependencies

- ✅ PostgreSQL: `pg_trgm` extension enabled
- ✅ Database indexes: Trigram or full-text search indexes created
- ⚠️ Redis: For caching popular queries

---

#### Definition of Done

- [ ] Search API endpoint implemented
- [ ] PostgreSQL trigram indexes created and verified
- [ ] Relevance ranking logic working (manual verification)
- [ ] Search + filter combination tested
- [ ] Input sanitization for SQL injection/XSS
- [ ] Caching layer implemented (Redis)
- [ ] Response time <300ms verified
- [ ] Unit test coverage ≥ 80%
- [ ] Integration tests passing
- [ ] API documentation (Swagger) updated
- [ ] Frontend search bar integrated (Next.js)
- [ ] Code reviewed and approved
- [ ] Deployed to staging environment
- [ ] Manual QA completed (various search queries)

---

#### Estimated Effort

**Story Points:** 5 (Medium)
**Development:** 3 days
**Testing:** 1 day
**Frontend Integration:** 1 day
**Total:** 5 days
