# Browse Auction API Integration

**Date:** 2026-05-31
**Branch:** feature/public-auction-discovery
**Scope:** Public auction browse page — search, filter, sort, pagination wired to real API

## Problem

The browse page (`app/auctions/page.tsx`) is entirely client-side filtered. After the initial keyword fetch, all category filtering, price filtering, sorting, and toggle filtering are done in the browser against a static in-memory list. This causes:

1. **Wrong category counts** — counts derived from the current result page, not real catalogue totals.
2. **No pagination** — all items fetched at once; will not scale.
3. **Ephemeral filter state** — filters are React state only; lost on refresh and not shareable via URL.
4. **Inconsistent interaction** — sort is instant, filters require Apply, making the page feel inconsistent.
5. **Incorrect price slider range** — max derived from the filtered set, not the full catalogue.

## Decisions

| Question | Decision |
|---|---|
| Filter interaction model | **Staged + Apply** — user tweaks filters freely, Apply fires the navigation. Sort remains instant. |
| Search independence | **Search is standalone** — submits independently, never blocked by Apply. Preserves active filters on the browse page. |
| Pagination | **Numbered pages** — `?page=N` in URL, prev/next + page numbers, hidden when `totalPages <= 1`. |
| Category counts source | **Dedicated API endpoint** (`/api/v1/auctions/public/category-counts`) — reflects full catalogue, not filtered results. |
| Filter execution | **Server-side via API** — all filtering and sorting delegated to `PublicAuctionFilterRequest`; client-side `applyFilters`/`applySort` removed. |
| Listing count | **`total` from API** — shows full filtered count across all pages, not current page item count. |

## URL Param Schema

All browse state lives in URL search params. The server component reads all params and passes them to the API in a single call.

| Param | API field | Type | Default |
|---|---|---|---|
| `q` | `keyword` | string | — |
| `category` | `categorySlug` | string | — (omitted = all) |
| `minPrice` | `minPrice` | number | — |
| `maxPrice` | `maxPrice` | number | — |
| `endingSoon` | `endingSoon` | `"true"` | — |
| `buyNow` | `buyNowAvailable` | `"true"` | — |
| `sort` | `sortBy` | `AuctionSortBy` string | `END_TIME_ASC` |
| `page` | `page` | number (0-based) | `0` |

**Rules:**
- Applying filters always resets `page` to 0.
- `endingSoon`/`buyNow` activate only on the literal string `"true"`.
- Bad param values (non-numeric page, invalid sort) are silently ignored/clamped — never 500.
- `category=all` is treated the same as omitting the param.

---

## Type Layer

### `types/api/auction.api.ts` — add

```ts
export interface AuctionBrowseItemResponse {
  id:              string;
  title:           string;
  primaryImageUrl: string | null;
  currentPrice:    number;
  totalBids:       number;
  endTime:         string;
  status:          string;
  buyNowPrice:     number | null;
  categoryName:    string;
}

export interface CategoryCountResponse {
  categoryName: string;
  count:        number;
}

export interface BrowseAuctionParams {
  keyword?:         string
  categorySlug?:    string
  minPrice?:        number
  maxPrice?:        number
  endingSoon?:      boolean
  buyNowAvailable?: boolean
  sortBy?:          string
  page?:            number
  size?:            number
}
```

### `types/ui/auction-browse.ui.ts` — add

```ts
export interface CategoryCount {
  categoryName: string
  count:        number
}
```

`AuctionBrowseItem` (existing) is unchanged.

### `types/mappers/auction.mapper.ts` — add

```ts
// Maps AuctionBrowseItemResponse → AuctionBrowseItem
export function mapAuctionBrowseItem(dto: AuctionBrowseItemResponse): AuctionBrowseItem

// Maps CategoryCountResponse → CategoryCount
export function mapCategoryCount(dto: CategoryCountResponse): CategoryCount
```

`mapAuctionBrowseItem` parses `endTime` string → `Date` and maps `status` string → `AuctionStatus` (same switch logic as `parseDetailStatus`).

---

## Service Layer

### `getBrowseAuctions` — updated signature

```ts
async getBrowseAuctions(params: BrowseAuctionParams): Promise<{
  items:      AuctionBrowseItem[]
  total:      number
  totalPages: number
  page:       number
}>
```

**Real API call (commented-out, ready to swap):**
```
GET /api/v1/auctions/public?keyword=&categorySlug=&minPrice=&maxPrice=&endingSoon=&buyNowAvailable=&sortBy=&page=&size=
→ ApiResponse<PageResponse<AuctionBrowseItemResponse>>
→ items mapped via mapAuctionBrowseItem
→ total/totalPages from PageResponse.pagination
```

Mock implementation: filter `MOCK_DETAIL_RESPONSES` in memory against params, map to `AuctionBrowseItem`, return with fake pagination.

### `getCategoryCounts` — new method

```ts
async getCategoryCounts(): Promise<CategoryCount[]>
```

**Real API call (commented-out, ready to swap):**
```
GET /api/v1/auctions/public/category-counts
→ ApiResponse<CategoryCountResponse[]>
→ mapped via mapCategoryCount
```

Mock implementation: derive counts from `MOCK_DETAIL_RESPONSES`.

---

## Page Component (`app/auctions/page.tsx`)

```ts
interface BrowsePageProps {
  searchParams: Promise<{
    q?:          string
    category?:   string
    minPrice?:   string
    maxPrice?:   string
    endingSoon?: string
    buyNow?:     string
    sort?:       string
    page?:       string
  }>
}
```

**Data fetching:**

```ts
const params = await searchParams
const page   = Math.max(0, parseInt(params.page ?? '0', 10) || 0)

const [{ items, total, totalPages }, categoryCounts] = await Promise.all([
  auctionService.getBrowseAuctions({
    keyword:         params.q,
    categorySlug:    params.category !== 'all' ? params.category : undefined,
    minPrice:        params.minPrice  ? Number(params.minPrice)  : undefined,
    maxPrice:        params.maxPrice  ? Number(params.maxPrice)  : undefined,
    endingSoon:      params.endingSoon === 'true' || undefined,
    buyNowAvailable: params.buyNow    === 'true' || undefined,
    sortBy:          params.sort,
    page,
    size: 20,
  }),
  auctionService.getCategoryCounts(),
])
```

`Promise.all` — both calls fire in parallel. `getCategoryCounts` is independent of search/filter state.

**Props passed to `BrowseClient`:**

```ts
<BrowseClient
  items={items}
  categoryCounts={categoryCounts}
  total={total}
  totalPages={totalPages}
  currentPage={page}
  searchParams={params}
/>
```

---

## `BrowseClient` Changes

### New props

```ts
interface BrowseClientProps {
  items:          AuctionBrowseItem[]
  categoryCounts: CategoryCount[]
  total:          number
  totalPages:     number
  currentPage:    number
  searchParams:   Record<string, string | undefined>
}
```

### Pending state — seeded from URL

```ts
const [pending, setPending] = useState<BrowseFilters>(
  () => parseBrowseFilters(searchParams)
)
```

The pending state is always initialised from the current URL, so the filter panel reflects what the server actually applied.

### Apply → `router.push`

```ts
function handleApply() {
  router.push(buildBrowseUrl(pending, sort))
}
```

Filter changes update `pending` state only. Nothing is fetched until Apply is clicked.

### Sort — still instant

```ts
function handleSortChange(newSort: SortOption) {
  setSort(newSort)
  router.push(buildBrowseUrl(pending, newSort))
}
```

Sort updates the URL immediately, bypassing Apply.

### Listing count label

```tsx
<p className="text-sm text-muted-foreground">
  {total.toLocaleString()} listing{total !== 1 ? 's' : ''}
</p>
```

Reflects the API's full filtered total across all pages, not the current page's item count.

### Removed state / memos

- `displayed` memo — removed (`items` comes pre-filtered from server)
- `maxPrice` / `categoryCounts` derived state — removed (computed in the service layer)

### `FilterPanel` — receives `categoryCounts: CategoryCount[]`

`CategoryFilter` updated to render from `CategoryCount[]` instead of `Record<string, number>`.

---

## `browse-utils.ts` Changes

### Removed

| Function | Reason |
|---|---|
| `applyFilters` | Server handles filtering |
| `applySort` | Server handles sorting |
| `deriveMaxPrice` | No longer needed |
| `computeCategoryCounts` | Replaced by API endpoint |

### Added

```ts
// Parse raw URL string params → BrowseFilters
export function parseBrowseFilters(
  params: Record<string, string | undefined>
): BrowseFilters

// Serialise BrowseFilters + sort + optional page → URL string
export function buildBrowseUrl(
  filters: BrowseFilters,
  sort:    SortOption,
  page?:   number        // omitting resets to 0
): string
```

### Kept

```ts
// Still used for the active-filter badge count on the mobile trigger button.
// Signature simplified — maxPrice param removed (no longer derived from data).
export function countActiveFilters(filters: BrowseFilters): number
```

---

## Standalone Search Box (`AuctionSearchInput`)

**New component:** `components/auction/browse/AuctionSearchInput.tsx`

```ts
interface AuctionSearchInputProps {
  defaultValue?:   string                          // pre-populated from ?q
  preserveParams?: Record<string, string>          // hidden inputs (browse page only)
  placeholder?:    string
}
```

**Behaviour:**

- A `<form action="/auctions">` with `<input name="q">`.
- On submit, navigates to `/auctions?q=<keyword>`.
- On the **browse page**: `preserveParams` carries active filter/sort params as hidden inputs, so submitting a new search preserves active filters: `?category=watches&sort=PRICE_LOW_HIGH` → `?category=watches&sort=PRICE_LOW_HIGH&q=seamaster`. Page is NOT included in `preserveParams` — a new search always starts at page 0.
- On the **landing page** (and anywhere else): no `preserveParams`, so submit navigates to a clean `/auctions?q=<keyword>`.
- Works without JavaScript (native form submit). Also supports `router.push` on the client for instant navigation if JS is available.

**Usage on browse page** — rendered in `BrowseClient` toolbar area, seeded from `searchParams.q`.

**Usage on landing page** — rendered standalone with no `preserveParams`, empty `defaultValue`.

---

## Pagination Component (`BrowsePagination`)

**New component:** `components/auction/browse/BrowsePagination.tsx`

```ts
interface BrowsePaginationProps {
  currentPage:  number                             // 0-based
  totalPages:   number
  searchParams: Record<string, string | undefined> // active URL params (not pending)
}
```

**Behaviour:**

- Hidden when `totalPages <= 1`.
- Shows up to 7 controls: `‹ 1 2 3 … 8 9 ›`. Ellipsis collapses middle pages when `totalPages > 7`.
- Page numbers are `<Link>` elements that produce `/auctions?<current-params>&page=N` — preserves all active filter/sort state by forwarding `searchParams` with only the `page` key overridden.
- Uses the **applied** `searchParams` (from server), never the `pending` filter state inside `BrowseClient`.
- `currentPage` displayed as 1-based to users (page 0 = "Page 1").
- Prev arrow disabled on page 0; next arrow disabled on last page.

---

## New Files

| File | Purpose |
|---|---|
| `components/auction/browse/AuctionSearchInput.tsx` | Standalone keyword search form |
| `components/auction/browse/BrowsePagination.tsx` | Numbered page controls |

## Modified Files

| File | Change |
|---|---|
| `types/api/auction.api.ts` | Add `AuctionBrowseItemResponse`, `CategoryCountResponse`, `BrowseAuctionParams` |
| `types/ui/auction-browse.ui.ts` | Add `CategoryCount` |
| `types/mappers/auction.mapper.ts` | Add `mapAuctionBrowseItem`, `mapCategoryCount` |
| `services/auction.service.ts` | Update `getBrowseAuctions`, add `getCategoryCounts` |
| `lib/browse-utils.ts` | Remove client-side filter/sort/count fns; add `parseBrowseFilters`, `buildBrowseUrl` |
| `app/auctions/page.tsx` | Read all URL params; parallel fetch; pass full props to `BrowseClient` |
| `components/auction/browse/BrowseClient.tsx` | Seed from URL; Apply → `router.push`; remove derived state |
| `components/auction/browse/FilterPanel.tsx` | Accept `CategoryCount[]` instead of `Record<string, number>` |
| `components/auction/browse/CategoryFilter.tsx` | Render from `CategoryCount[]` |

## Out of Scope

- Search input in the global Header (already works via `?q`)
- Mobile filter sheet layout changes
- Saved/persisted filters
- Infinite scroll
