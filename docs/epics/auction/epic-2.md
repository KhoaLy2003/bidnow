# Epic 2: Public Auction Discovery & Search

**Epic ID:** `EPIC-002`
**Epic Title:** Public Auction Discovery & Search
**Priority:** P0 (Critical - Core User Experience)

---

## Epic Overview

This epic delivers the public-facing auction browsing experience, enabling both guests (unauthenticated users) and registered users to discover active auctions through listing, filtering, and search capabilities. This is the primary entry point for buyers to find items they want to bid on.

---

## Business Value

- **For Buyers:** Enables discovery of auctions matching their interests, increasing bid participation
- **For Sellers:** Drives traffic to their listings, increasing visibility and final sale prices
- **For Platform:** Creates a marketplace effect where more listings attract more buyers, and vice versa
- **Conversion Funnel:** Guest browsing → Registration → Bidding → Transaction

---

## Success Metrics

- **Traffic:** 10,000+ auction page views in first month
- **Performance:** Page load time <500ms for listing page, <300ms for API responses
- **Search Quality:** 80%+ of searches return relevant results (user surveys)
- **Conversion:** 15%+ of guests who browse register within the session
- **Engagement:** Average 5 auctions viewed per session

---

## Epic Scope

### In Scope

✅ Public listing of all active auctions (no authentication required)
✅ Pagination with configurable page size
✅ Filtering by category, price range, auction ending time
✅ Full-text search on auction title and description
✅ Sort options (ending soon, price low-to-high, most bids, newest)
✅ Responsive grid/list view layout
✅ SEO optimization for auction pages

### Out of Scope

❌ Personalized recommendations based on user history
❌ Saved searches and wishlist functionality
❌ Advanced filters (brand, condition, seller rating) - future iteration
❌ Real-time bid updates on listing page (only on individual auction detail page)
❌ Auction analytics/statistics dashboard

---

## Technical Architecture

### Services Involved

- **Primary:** Auction Service
- **Dependencies:**
  - None (fully independent for public browsing)
  - Optional: Redis for caching popular searches

### Key Components

1. **Public API Endpoints:** RESTful APIs with no authentication requirement
2. **Search Engine:** PostgreSQL full-text search or Elasticsearch (future)
3. **Cache Layer:** Redis for frequently accessed auction lists
4. **CDN:** Cloudinary for image delivery optimization

### Data Flow

```
Client (Guest/User) → API Gateway → Auction Service → PostgreSQL
                                           ↓
                                       Redis Cache (optional)
```

---

## Database Schema Reference

See [Database Schema Document](./database-schema.md) - Primary tables:

- `auction_items` - Main listing data
- `auction_categories` - Filter by category
- `auction_images` - Display primary image thumbnails
