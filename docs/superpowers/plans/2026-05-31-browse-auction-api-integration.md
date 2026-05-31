# Browse Auction API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all client-side browse filtering/sorting with real API calls, add numbered pagination, standalone search input, and correct listing count.

**Architecture:** All browse state (keyword, category, price, toggles, sort, page) lives in URL search params. The Next.js server component reads params, fires `getBrowseAuctions` + `getCategoryCounts` in parallel, and passes pre-filtered results to `BrowseClient`. `BrowseClient` manages local pending state for the Apply button; clicking Apply calls `router.push` with the serialised URL. Sort and search navigate instantly without Apply.

**Tech Stack:** Next.js 16.2 App Router, TypeScript strict mode, Tailwind CSS v4. Verification via `npx tsc --noEmit` (no test runner configured).

---

## File Map

| File | Change |
|---|---|
| `frontend/types/api/auction.api.ts` | Add `AuctionBrowseItemResponse`, `CategoryCountResponse`, `BrowseAuctionParams` |
| `frontend/types/ui/auction-browse.ui.ts` | Add `CategoryCount` |
| `frontend/types/ui/browse.ui.ts` | Add `DEFAULT_MAX_PRICE`; update `DEFAULT_FILTERS` |
| `frontend/types/mappers/auction.mapper.ts` | Add `mapAuctionBrowseItem`, `mapCategoryCount` |
| `frontend/services/auction.service.ts` | Update `getBrowseAuctions`; add `getCategoryCounts`; remove `GetBrowseAuctionsParams` |
| `frontend/lib/browse-utils.ts` | Remove `applyFilters`, `applySort`, `deriveMaxPrice`, `computeCategoryCounts`; add `parseBrowseFilters`, `buildBrowseUrl`; update `countActiveFilters` |
| `frontend/components/auction/browse/AuctionSearchInput.tsx` | **Create** — standalone search form |
| `frontend/components/auction/browse/BrowsePagination.tsx` | **Create** — numbered page controls |
| `frontend/components/auction/browse/CategoryFilter.tsx` | Accept `CategoryCount[]` instead of `Record<string, number>` |
| `frontend/components/auction/browse/FilterPanel.tsx` | Accept `CategoryCount[]`; remove `maxPrice` prop; import `DEFAULT_MAX_PRICE` |
| `frontend/components/auction/browse/BrowseClient.tsx` | Rewrite: URL-seeded state; Apply → `router.push`; instant sort; `total` count |
| `frontend/components/auction/browse/index.ts` | Export new components |
| `frontend/app/auctions/page.tsx` | Read all URL params; parallel fetch; pass full props to `BrowseClient` |

---

## Task 1: Type additions

**Files:**
- Modify: `frontend/types/api/auction.api.ts`
- Modify: `frontend/types/ui/auction-browse.ui.ts`
- Modify: `frontend/types/ui/browse.ui.ts`

- [ ] **Step 1: Add API types to `frontend/types/api/auction.api.ts`**

Append after the last existing interface:

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

- [ ] **Step 2: Add `CategoryCount` to `frontend/types/ui/auction-browse.ui.ts`**

Append after the existing `AuctionBrowseItem` interface:

```ts
export interface CategoryCount {
  categoryName: string
  count:        number
}
```

- [ ] **Step 3: Add `DEFAULT_MAX_PRICE` to `frontend/types/ui/browse.ui.ts` and update `DEFAULT_FILTERS`**

Add `DEFAULT_MAX_PRICE` constant and update the default price range. Replace the existing `DEFAULT_FILTERS` export:

```ts
export const DEFAULT_MAX_PRICE = 100_000

export const DEFAULT_FILTERS: BrowseFilters = {
  categoryName: 'all',
  priceRange:   { min: 0, max: DEFAULT_MAX_PRICE },
  endingSoon:   false,
  buyNow:       false,
}
```

Also remove the `// cents` comment from `PriceRange` (it was inaccurate):

```ts
export interface PriceRange {
  min: number
  max: number
}
```

- [ ] **Step 4: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/types/api/auction.api.ts frontend/types/ui/auction-browse.ui.ts frontend/types/ui/browse.ui.ts
git commit -m "feat(types): add browse API types, CategoryCount, DEFAULT_MAX_PRICE"
```

---

## Task 2: Mapper additions

**Files:**
- Modify: `frontend/types/mappers/auction.mapper.ts`

- [ ] **Step 1: Add `mapAuctionBrowseItem` and `mapCategoryCount` to the mapper**

Open `frontend/types/mappers/auction.mapper.ts`. Add to the imports at the top:

```ts
import type {
  AuctionSummaryResponse,
  AuctionDetailResponse,
  AuctionBrowseItemResponse,
  CategoryCountResponse,
} from '@/types/api/auction.api'
import type {
  AuctionDetail,
  AuctionDetailSeller,
  AuctionImage,
} from '@/types/ui/auction.ui'
import type { AuctionBrowseItem, CategoryCount } from '@/types/ui/auction-browse.ui'
```

Then append after the existing `mapAuctionDetailResponse` function:

```ts
function parseBrowseStatus(status: string): AuctionStatus {
  switch (status?.toUpperCase()) {
    case 'ACTIVE':    return AuctionStatus.Active
    case 'SCHEDULED': return AuctionStatus.Scheduled
    case 'COMPLETED':
    case 'CANCELLED':
    case 'FAILED':    return AuctionStatus.Closed
    default:          return AuctionStatus.Active
  }
}

export function mapAuctionBrowseItem(dto: AuctionBrowseItemResponse): AuctionBrowseItem {
  return {
    id:              dto.id,
    title:           dto.title,
    primaryImageUrl: dto.primaryImageUrl,
    currentPrice:    dto.currentPrice,
    totalBids:       dto.totalBids,
    endTime:         new Date(dto.endTime),
    status:          parseBrowseStatus(dto.status),
    buyNowPrice:     dto.buyNowPrice,
    categoryName:    dto.categoryName,
  }
}

export function mapCategoryCount(dto: CategoryCountResponse): CategoryCount {
  return {
    categoryName: dto.categoryName,
    count:        dto.count,
  }
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/types/mappers/auction.mapper.ts
git commit -m "feat(mapper): add mapAuctionBrowseItem and mapCategoryCount"
```

---

## Task 3: Refactor `browse-utils.ts`

**Files:**
- Modify: `frontend/lib/browse-utils.ts`

- [ ] **Step 1: Replace the entire file**

The old functions (`applyFilters`, `applySort`, `deriveMaxPrice`, `computeCategoryCounts`) are replaced by `parseBrowseFilters`, `buildBrowseUrl`, and a simplified `countActiveFilters`.

Replace the entire file content with:

```ts
import { BROWSE_CATEGORIES, DEFAULT_FILTERS, DEFAULT_MAX_PRICE, DEFAULT_SORT } from '@/types/ui/browse.ui'
import type { BrowseFilters, SortOption } from '@/types/ui/browse.ui'

/**
 * Converts raw URL search params to a typed BrowseFilters object.
 * Category name is matched case-insensitively against BROWSE_CATEGORIES.
 */
export function parseBrowseFilters(
  params: Record<string, string | undefined>,
): BrowseFilters {
  const matchedCategory = BROWSE_CATEGORIES.find(
    (c) => c.id.toLowerCase() === (params.category ?? '').toLowerCase(),
  )

  return {
    categoryName: matchedCategory?.id ?? 'all',
    priceRange: {
      min: params.minPrice ? Number(params.minPrice) : 0,
      max: params.maxPrice ? Number(params.maxPrice) : DEFAULT_MAX_PRICE,
    },
    endingSoon: params.endingSoon === 'true',
    buyNow:     params.buyNow === 'true',
  }
}

/**
 * Serialises BrowseFilters + sort + optional page to a /auctions URL string.
 * Omitting page resets to page 0. Only non-default values are included.
 */
export function buildBrowseUrl(
  filters: BrowseFilters,
  sort: SortOption,
  page?: number,
): string {
  const params = new URLSearchParams()

  if (filters.categoryName !== 'all') {
    params.set('category', filters.categoryName.toLowerCase())
  }
  if (filters.priceRange.min > 0) {
    params.set('minPrice', String(filters.priceRange.min))
  }
  if (filters.priceRange.max < DEFAULT_MAX_PRICE) {
    params.set('maxPrice', String(filters.priceRange.max))
  }
  if (filters.endingSoon) params.set('endingSoon', 'true')
  if (filters.buyNow)     params.set('buyNow', 'true')
  if (sort !== DEFAULT_SORT) params.set('sort', sort)
  if (page && page > 0)   params.set('page', String(page))

  const qs = params.toString()
  return `/auctions${qs ? '?' + qs : ''}`
}

/**
 * Counts active (non-default) filter selections.
 * Used for the badge count on the mobile filter trigger button.
 */
export function countActiveFilters(filters: BrowseFilters): number {
  let count = 0
  if (filters.categoryName !== 'all') count++
  if (filters.priceRange.min > 0 || filters.priceRange.max < DEFAULT_MAX_PRICE) count++
  if (filters.endingSoon) count++
  if (filters.buyNow) count++
  return count
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expect type errors in `BrowseClient.tsx` (still uses old functions) — those are fixed in Task 9.

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/browse-utils.ts
git commit -m "refactor(browse-utils): replace client-side filter/sort/count with parseBrowseFilters and buildBrowseUrl"
```

---

## Task 4: Service layer

**Files:**
- Modify: `frontend/services/auction.service.ts`

- [ ] **Step 1: Add new imports and update the browse-related types**

Open `frontend/services/auction.service.ts`.

Update the API types import to include the new types:

```ts
import type {
  CreateAuctionRequest,
  UpdateAuctionRequest,
  AuctionResponse,
  AuctionSummaryResponse,
  AuctionCategoryResponse,
  AuctionDetailResponse,
  AuctionBrowseItemResponse,
  CategoryCountResponse,
  BrowseAuctionParams,
} from '@/types/api/auction.api'
```

Update the mapper import:

```ts
import { mapAuctionDetailResponse, mapAuctionBrowseItem, mapCategoryCount } from '@/types/mappers/auction.mapper'
```

Update the UI types import:

```ts
import type { Auction, BidHistoryItem, AuctionDetail } from '@/types/ui/auction.ui'
import type { AuctionBrowseItem, CategoryCount } from '@/types/ui/auction-browse.ui'
```

- [ ] **Step 2: Remove `GetBrowseAuctionsParams` and update `getBrowseAuctions`**

Delete the old `GetBrowseAuctionsParams` interface and `CATEGORY_NAMES` constant (no longer needed).

Replace the existing `getBrowseAuctions` method with:

```ts
async getBrowseAuctions(params: BrowseAuctionParams): Promise<{
  items:      AuctionBrowseItem[]
  total:      number
  totalPages: number
  page:       number
}> {
  await delay(300)
  // TODO: replace with real API call:
  // const query = new URLSearchParams()
  // if (params.keyword)         query.set('keyword', params.keyword)
  // if (params.categorySlug)    query.set('categorySlug', params.categorySlug)
  // if (params.minPrice !== undefined) query.set('minPrice', String(params.minPrice))
  // if (params.maxPrice !== undefined) query.set('maxPrice', String(params.maxPrice))
  // if (params.endingSoon)      query.set('endingSoon', 'true')
  // if (params.buyNowAvailable) query.set('buyNowAvailable', 'true')
  // if (params.sortBy)          query.set('sortBy', params.sortBy)
  // query.set('page', String(params.page ?? 0))
  // query.set('size', String(params.size ?? 20))
  // const response = await fetch(`${API_URL}/api/v1/auctions/public?${query}`, { cache: 'no-store' })
  // if (!response.ok) return { items: [], total: 0, totalPages: 0, page: 0 }
  // const body: ApiResponse<PageResponse<AuctionBrowseItemResponse>> = await response.json()
  // return {
  //   items:      body.data.data.map(mapAuctionBrowseItem),
  //   total:      body.data.pagination.total,
  //   totalPages: body.data.pagination.totalPages,
  //   page:       body.data.pagination.page,
  // }

  // Mock: derive browse items from MOCK_DETAIL_RESPONSES
  let results: AuctionBrowseItemResponse[] = MOCK_DETAIL_RESPONSES.map((d) => ({
    id:              d.id,
    title:           d.title,
    primaryImageUrl: d.images[0]?.imageUrl ?? null,
    currentPrice:    d.currentPrice,
    totalBids:       d.totalBids,
    endTime:         d.endTime,
    status:          d.status,
    buyNowPrice:     d.buyNowPrice ?? null,
    categoryName:    d.category.name,
  }))

  // Apply mock filtering
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    results = results.filter((r) => r.title.toLowerCase().includes(kw))
  }
  if (params.categorySlug) {
    const slug = params.categorySlug.toLowerCase()
    results = results.filter((r) => r.categoryName.toLowerCase() === slug)
  }
  if (params.minPrice !== undefined) {
    results = results.filter((r) => r.currentPrice >= params.minPrice!)
  }
  if (params.maxPrice !== undefined) {
    results = results.filter((r) => r.currentPrice <= params.maxPrice!)
  }
  if (params.endingSoon) {
    results = results.filter((r) => {
      const ms = new Date(r.endTime).getTime() - Date.now()
      return ms > 0 && ms <= 24 * 60 * 60 * 1_000
    })
  }
  if (params.buyNowAvailable) {
    results = results.filter((r) => r.buyNowPrice !== null)
  }

  // Apply mock sorting
  const sort = params.sortBy ?? 'END_TIME_ASC'
  results = [...results].sort((a, b) => {
    switch (sort) {
      case 'END_TIME_ASC':   return new Date(a.endTime).getTime() - new Date(b.endTime).getTime()
      case 'NEWLY_LISTED':   return new Date(b.endTime).getTime() - new Date(a.endTime).getTime()
      case 'PRICE_LOW_HIGH': return a.currentPrice - b.currentPrice
      case 'PRICE_HIGH_LOW': return b.currentPrice - a.currentPrice
      case 'MOST_BIDS':      return b.totalBids - a.totalBids
      default:               return 0
    }
  })

  // Mock pagination
  const page       = params.page ?? 0
  const size       = params.size ?? 20
  const total      = results.length
  const totalPages = Math.max(1, Math.ceil(total / size))
  const items      = results.slice(page * size, (page + 1) * size).map(mapAuctionBrowseItem)

  return { items, total, totalPages, page }
},
```

- [ ] **Step 3: Add `getCategoryCounts` method**

Add after `getBrowseAuctions`:

```ts
async getCategoryCounts(): Promise<CategoryCount[]> {
  await delay(100)
  // TODO: replace with real API call:
  // const response = await fetch(`${API_URL}/api/v1/auctions/public/category-counts`, { cache: 'no-store' })
  // if (!response.ok) return []
  // const body: ApiResponse<CategoryCountResponse[]> = await response.json()
  // return body.data.map(mapCategoryCount)

  // Mock: count by category from MOCK_DETAIL_RESPONSES
  const counts: Record<string, number> = {}
  for (const d of MOCK_DETAIL_RESPONSES) {
    const name = d.category.name
    counts[name] = (counts[name] ?? 0) + 1
  }
  return Object.entries(counts).map(([categoryName, count]) => ({ categoryName, count }))
},
```

- [ ] **Step 4: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expect errors in `BrowseClient.tsx` and `app/auctions/page.tsx` (old interface) — those are fixed in Tasks 9 and 10.

- [ ] **Step 5: Commit**

```bash
git add frontend/services/auction.service.ts
git commit -m "feat(service): wire getBrowseAuctions and getCategoryCounts through mock + mapper"
```

---

## Task 5: `AuctionSearchInput` component

**Files:**
- Create: `frontend/components/auction/browse/AuctionSearchInput.tsx`

- [ ] **Step 1: Create the component**

```tsx
import { Search } from 'lucide-react'
import { Input }  from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface AuctionSearchInputProps {
  defaultValue?:   string
  preserveParams?: Record<string, string>
  placeholder?:    string
  className?:      string
}

export function AuctionSearchInput({
  defaultValue,
  preserveParams,
  placeholder = 'Search auctions…',
  className,
}: AuctionSearchInputProps) {
  return (
    <form action="/auctions" method="GET" className={cn('relative flex items-center', className)}>
      {preserveParams &&
        Object.entries(preserveParams).map(([key, val]) => (
          <input key={key} type="hidden" name={key} value={val} />
        ))}
      <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
      <Input
        name="q"
        type="search"
        defaultValue={defaultValue}
        placeholder={placeholder}
        className="pl-9 h-9"
      />
    </form>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors from the new file.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/browse/AuctionSearchInput.tsx
git commit -m "feat(browse): add AuctionSearchInput standalone search form"
```

---

## Task 6: `BrowsePagination` component

**Files:**
- Create: `frontend/components/auction/browse/BrowsePagination.tsx`

- [ ] **Step 1: Create the component**

```tsx
import Link from 'next/link'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'
import { DEFAULT_SORT, SORT_OPTIONS, type SortOption } from '@/types/ui/browse.ui'
import { parseBrowseFilters, buildBrowseUrl } from '@/lib/browse-utils'

interface BrowsePaginationProps {
  currentPage:  number                              // 0-based
  totalPages:   number
  searchParams: Record<string, string | undefined>  // active URL params (not pending)
}

const NAV_CLASSES =
  'flex items-center justify-center size-8 rounded border border-border text-sm transition-colors duration-[var(--duration-tesla)] hover:border-foreground hover:text-foreground'

const DISABLED_NAV_CLASSES =
  'flex items-center justify-center size-8 rounded border border-border text-sm opacity-40 pointer-events-none'

const PAGE_CLASSES = cn(NAV_CLASSES, 'font-mono text-muted-foreground')

const ACTIVE_PAGE_CLASSES =
  'flex items-center justify-center size-8 rounded text-sm font-mono font-medium bg-foreground text-background pointer-events-none'

export function BrowsePagination({
  currentPage,
  totalPages,
  searchParams,
}: BrowsePaginationProps) {
  if (totalPages <= 1) return null

  const filters = parseBrowseFilters(searchParams)
  const sort    = SORT_OPTIONS.includes(searchParams.sort as SortOption)
    ? (searchParams.sort as SortOption)
    : DEFAULT_SORT

  function pageUrl(page: number) {
    return buildBrowseUrl(filters, sort, page)
  }

  function getPageNumbers(): (number | '...')[] {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i)
    if (currentPage < 4) {
      return [0, 1, 2, 3, '...', totalPages - 2, totalPages - 1]
    }
    if (currentPage > totalPages - 5) {
      return [0, 1, '...', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1]
    }
    return [0, '...', currentPage - 1, currentPage, currentPage + 1, '...', totalPages - 1]
  }

  const pages = getPageNumbers()

  return (
    <div className="flex flex-col items-center gap-2 mt-8">
      <div className="flex items-center gap-1">
        {currentPage > 0 ? (
          <Link href={pageUrl(currentPage - 1)} className={NAV_CLASSES} aria-label="Previous page">
            <ChevronLeft className="size-4" />
          </Link>
        ) : (
          <span className={DISABLED_NAV_CLASSES} aria-disabled>
            <ChevronLeft className="size-4" />
          </span>
        )}

        {pages.map((p, i) =>
          p === '...' ? (
            <span key={`ellipsis-${i}`} className="flex items-center justify-center size-8 text-sm text-muted-foreground">
              …
            </span>
          ) : (
            <Link
              key={p}
              href={pageUrl(p)}
              aria-current={p === currentPage ? 'page' : undefined}
              className={p === currentPage ? ACTIVE_PAGE_CLASSES : PAGE_CLASSES}
            >
              {p + 1}
            </Link>
          ),
        )}

        {currentPage < totalPages - 1 ? (
          <Link href={pageUrl(currentPage + 1)} className={NAV_CLASSES} aria-label="Next page">
            <ChevronRight className="size-4" />
          </Link>
        ) : (
          <span className={DISABLED_NAV_CLASSES} aria-disabled>
            <ChevronRight className="size-4" />
          </span>
        )}
      </div>

      <p className="text-xs text-muted-foreground font-mono">
        Page {currentPage + 1} of {totalPages}
      </p>
    </div>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors from the new file.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/browse/BrowsePagination.tsx
git commit -m "feat(browse): add BrowsePagination numbered page controls"
```

---

## Task 7: Update `CategoryFilter`

**Files:**
- Modify: `frontend/components/auction/browse/CategoryFilter.tsx`

- [ ] **Step 1: Update prop type and count lookup**

Replace the entire file content:

```tsx
'use client'

import { cn } from '@/lib/utils'
import { BROWSE_CATEGORIES } from '@/types/ui/browse.ui'
import type { CategoryCount } from '@/types/ui/auction-browse.ui'

interface CategoryFilterProps {
  value:          string
  categoryCounts: CategoryCount[]
  onChange:       (categoryName: string) => void
}

export function CategoryFilter({ value, categoryCounts, onChange }: CategoryFilterProps) {
  const allCount = categoryCounts.reduce((sum, c) => sum + c.count, 0)

  function getCount(categoryId: string): number {
    if (categoryId === 'all') return allCount
    return categoryCounts.find((c) => c.categoryName === categoryId)?.count ?? 0
  }

  return (
    <ul className="flex flex-col gap-0.5" role="listbox" aria-label="Category">
      {BROWSE_CATEGORIES.map(({ id, label }) => {
        const isActive = value === id
        const count    = getCount(id)

        return (
          <li key={id} role="option" aria-selected={isActive}>
            <button
              type="button"
              onClick={() => onChange(id)}
              className={cn(
                'w-full rounded-md px-3 py-1.5 text-left text-sm transition-colors duration-[var(--duration-tesla)] flex items-center justify-between gap-2',
                isActive
                  ? 'bg-brand-600 text-white font-medium'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
            >
              <span>{label}</span>
              <span
                className={cn(
                  'text-xs tabular-nums shrink-0',
                  isActive ? 'text-white/80' : 'text-muted-foreground',
                )}
              >
                {count}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: error in `FilterPanel.tsx` (still passes old prop type) — fixed in Task 8.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/browse/CategoryFilter.tsx
git commit -m "feat(browse): update CategoryFilter to accept CategoryCount[]"
```

---

## Task 8: Update `FilterPanel`

**Files:**
- Modify: `frontend/components/auction/browse/FilterPanel.tsx`

- [ ] **Step 1: Replace prop type, remove `maxPrice`, import `DEFAULT_MAX_PRICE`**

Replace the entire file content:

```tsx
'use client'

import { Button } from '@/components/ui/button'
import {
  Accordion,
  AccordionItem,
  AccordionTrigger,
  AccordionContent,
} from '@/components/ui/accordion'
import { CategoryFilter }   from './CategoryFilter'
import { PriceRangeFilter } from './PriceRangeFilter'
import { QuickFilters }     from './QuickFilters'
import { DEFAULT_MAX_PRICE } from '@/types/ui/browse.ui'
import type { BrowseFilters, PriceRange } from '@/types/ui/browse.ui'
import type { CategoryCount } from '@/types/ui/auction-browse.ui'

interface FilterPanelProps {
  pendingFilters:  BrowseFilters
  categoryCounts:  CategoryCount[]
  onPendingChange: (partial: Partial<BrowseFilters>) => void
  onApply:         () => void
  onClearAll:      () => void
  hideHeader?:     boolean
}

export function FilterPanel({
  pendingFilters,
  categoryCounts,
  onPendingChange,
  onApply,
  onClearAll,
  hideHeader = false,
}: FilterPanelProps) {
  return (
    <div className="flex flex-col h-full bg-[var(--color-bg-sidebar)]">
      {!hideHeader && (
        <div className="flex items-center justify-between px-4 py-3 border-b border-border">
          <span className="text-sm font-medium">Filters</span>
          <button
            type="button"
            onClick={onClearAll}
            className="text-xs text-[var(--color-text-link)] hover:underline transition-colors"
          >
            Clear All
          </button>
        </div>
      )}

      <div className="flex-1 overflow-y-auto px-2 py-1">
        <Accordion multiple defaultValue={['categories', 'price', 'status']}>
          <AccordionItem value="categories">
            <AccordionTrigger className="text-xs font-semibold uppercase tracking-wider text-muted-foreground hover:no-underline">
              Categories
            </AccordionTrigger>
            <AccordionContent>
              <div className="pb-1">
                <CategoryFilter
                  value={pendingFilters.categoryName}
                  categoryCounts={categoryCounts}
                  onChange={(name: string) => onPendingChange({ categoryName: name })}
                />
              </div>
            </AccordionContent>
          </AccordionItem>

          <AccordionItem value="price">
            <AccordionTrigger className="text-xs font-semibold uppercase tracking-wider text-muted-foreground hover:no-underline">
              Price Range
            </AccordionTrigger>
            <AccordionContent>
              <div className="pb-1 pt-2">
                <PriceRangeFilter
                  value={pendingFilters.priceRange}
                  max={DEFAULT_MAX_PRICE}
                  onChange={(range: PriceRange) => onPendingChange({ priceRange: range })}
                />
              </div>
            </AccordionContent>
          </AccordionItem>

          <AccordionItem value="status">
            <AccordionTrigger className="text-xs font-semibold uppercase tracking-wider text-muted-foreground hover:no-underline">
              Status
            </AccordionTrigger>
            <AccordionContent>
              <div className="pb-1 pt-2">
                <QuickFilters
                  endingSoon={pendingFilters.endingSoon}
                  buyNow={pendingFilters.buyNow}
                  onChange={(partial) =>
                    onPendingChange(
                      partial as Partial<Pick<BrowseFilters, 'endingSoon' | 'buyNow'>>,
                    )
                  }
                />
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </div>

      <div className="px-4 py-3 border-t border-border shrink-0">
        <Button variant="brand" className="w-full" onClick={onApply}>
          Apply Filters
        </Button>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: error in `BrowseClient.tsx` (still uses old props) — fixed in Task 9.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/browse/FilterPanel.tsx
git commit -m "feat(browse): update FilterPanel to accept CategoryCount[], remove maxPrice prop"
```

---

## Task 9: Rewrite `BrowseClient`

**Files:**
- Modify: `frontend/components/auction/browse/BrowseClient.tsx`

- [ ] **Step 1: Replace the entire file**

```tsx
'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { SlidersHorizontal, LayoutGrid, List } from 'lucide-react'
import { Button }  from '@/components/ui/button'
import { Badge }   from '@/components/ui/badge'
import {
  Sheet,
  SheetTrigger,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
} from '@/components/ui/sheet'
import { FilterPanel }        from './FilterPanel'
import { SortButton }         from './SortButton'
import { AuctionBrowseGrid }  from './AuctionBrowseGrid'
import { AuctionBrowseList }  from './AuctionBrowseList'
import { AuctionSearchInput } from './AuctionSearchInput'
import { BrowsePagination }   from './BrowsePagination'
import {
  parseBrowseFilters,
  buildBrowseUrl,
  countActiveFilters,
} from '@/lib/browse-utils'
import {
  DEFAULT_SORT,
  SORT_OPTIONS,
  type BrowseFilters,
  type SortOption,
} from '@/types/ui/browse.ui'
import type { AuctionBrowseItem, CategoryCount } from '@/types/ui/auction-browse.ui'

interface BrowseClientProps {
  items:          AuctionBrowseItem[]
  categoryCounts: CategoryCount[]
  total:          number
  totalPages:     number
  currentPage:    number
  searchParams:   Record<string, string | undefined>
}

export function BrowseClient({
  items,
  categoryCounts,
  total,
  totalPages,
  currentPage,
  searchParams,
}: BrowseClientProps) {
  const router = useRouter()

  const [pending,    setPending]    = useState<BrowseFilters>(() => parseBrowseFilters(searchParams))
  const [sort,       setSort]       = useState<SortOption>(
    SORT_OPTIONS.includes(searchParams.sort as SortOption)
      ? (searchParams.sort as SortOption)
      : DEFAULT_SORT,
  )
  const [viewMode,   setViewMode]   = useState<'grid' | 'list'>('grid')
  const [mobileOpen, setMobileOpen] = useState(false)

  const activeCount = countActiveFilters(pending)

  // preserveParams carries active filter/sort state as hidden inputs in the search form
  // so searching while filters are active preserves them. Page is excluded (new search = page 0).
  const preserveParams = Object.fromEntries(
    Object.entries(searchParams).filter(
      ([key, val]) => val !== undefined && key !== 'q' && key !== 'page',
    ),
  ) as Record<string, string>

  function handlePendingChange(partial: Partial<BrowseFilters>) {
    setPending((prev) => ({ ...prev, ...partial }))
  }

  function handleApply() {
    router.push(buildBrowseUrl(pending, sort))
    setMobileOpen(false)
  }

  function handleClearAll() {
    router.push('/auctions')
    setMobileOpen(false)
  }

  function handleSortChange(newSort: SortOption) {
    setSort(newSort)
    router.push(buildBrowseUrl(pending, newSort))
  }

  const panelProps = {
    categoryCounts,
    pendingFilters:  pending,
    onPendingChange: handlePendingChange,
    onApply:         handleApply,
    onClearAll:      handleClearAll,
  }

  return (
    <div className="flex gap-6 items-start">
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex lg:flex-col w-60 shrink-0 sticky top-20 self-start rounded-xl border border-border overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-border">
          <span className="text-sm font-medium">Filters</span>
          <button
            type="button"
            onClick={handleClearAll}
            className="text-xs text-[var(--color-text-link)] hover:underline transition-colors"
          >
            Clear All
          </button>
        </div>
        <FilterPanel {...panelProps} hideHeader />
      </aside>

      {/* Main content */}
      <div className="flex-1 min-w-0">
        {/* Toolbar */}
        <div className="mb-6 flex items-start justify-between gap-4 flex-wrap">
          <div>
            <h1 className="font-display font-medium text-[length:var(--font-size-2xl)]">
              {searchParams.q ? `Results for "${searchParams.q}"` : 'Browse Auctions'}
            </h1>
            <p className="text-sm text-muted-foreground">
              {total.toLocaleString()} listing{total !== 1 ? 's' : ''}
            </p>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            {/* Search input — preserves active filters */}
            <AuctionSearchInput
              defaultValue={searchParams.q}
              preserveParams={preserveParams}
              className="w-48 sm:w-64"
            />

            {/* Mobile filter trigger */}
            <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
              <SheetTrigger
                render={
                  <Button
                    variant="outline"
                    size="sm"
                    className="lg:hidden relative gap-1.5"
                  />
                }
              >
                <SlidersHorizontal className="size-4" />
                Filters
                {activeCount > 0 && (
                  <Badge className="absolute -top-1.5 -right-1.5 size-4 rounded-full p-0 flex items-center justify-center text-[10px] leading-none">
                    {activeCount}
                  </Badge>
                )}
              </SheetTrigger>

              <SheetContent side="left" className="w-72 p-0 flex flex-col gap-0">
                <SheetHeader className="flex flex-row items-center justify-between px-4 py-3 border-b border-border shrink-0">
                  <SheetTitle className="text-sm font-medium">Filters</SheetTitle>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={handleClearAll}
                      className="text-xs text-[var(--color-text-link)] hover:underline transition-colors"
                    >
                      Clear All
                    </button>
                    <SheetClose />
                  </div>
                </SheetHeader>
                <div className="flex-1 overflow-y-auto">
                  <FilterPanel {...panelProps} hideHeader />
                </div>
              </SheetContent>
            </Sheet>

            <SortButton value={sort} onChange={handleSortChange} />

            <Button
              variant={viewMode === 'grid' ? 'secondary' : 'ghost'}
              size="icon-sm"
              onClick={() => setViewMode('grid')}
              aria-label="Grid view"
            >
              <LayoutGrid className="size-4" />
            </Button>
            <Button
              variant={viewMode === 'list' ? 'secondary' : 'ghost'}
              size="icon-sm"
              onClick={() => setViewMode('list')}
              aria-label="List view"
            >
              <List className="size-4" />
            </Button>
          </div>
        </div>

        {/* Results */}
        {items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-24 text-center">
            <p className="text-sm text-muted-foreground">
              No auctions match the current filters.
            </p>
            <Button variant="ghost" size="sm" onClick={handleClearAll}>
              Clear filters
            </Button>
          </div>
        ) : viewMode === 'grid'
            ? <AuctionBrowseGrid items={items} />
            : <AuctionBrowseList items={items} />
        }

        {/* Pagination */}
        <BrowsePagination
          currentPage={currentPage}
          totalPages={totalPages}
          searchParams={searchParams}
        />
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: error in `app/auctions/page.tsx` (still passes old props) — fixed in Task 10.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/auction/browse/BrowseClient.tsx
git commit -m "feat(browse): rewrite BrowseClient with URL-seeded state, Apply→router.push, server-driven results"
```

---

## Task 10: Update page component and barrel exports

**Files:**
- Modify: `frontend/app/auctions/page.tsx`
- Modify: `frontend/components/auction/browse/index.ts`

- [ ] **Step 1: Replace `app/auctions/page.tsx`**

```tsx
import type { Metadata } from 'next'
import { Header }         from '@/components/layout/Header'
import { Footer }         from '@/components/layout/Footer'
import { BottomNav }      from '@/components/layout/BottomNav'
import { BrowseClient }   from '@/components/auction/browse'
import { auctionService } from '@/services/auction.service'

export const metadata: Metadata = { title: 'Browse Auctions' }

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

export default async function BrowsePage({ searchParams }: BrowsePageProps) {
  const params      = await searchParams
  const currentPage = Math.max(0, parseInt(params.page ?? '0', 10) || 0)

  const [{ items, total, totalPages }, categoryCounts] = await Promise.all([
    auctionService.getBrowseAuctions({
      keyword:         params.q,
      categorySlug:    params.category && params.category !== 'all' ? params.category : undefined,
      minPrice:        params.minPrice  ? Number(params.minPrice)  : undefined,
      maxPrice:        params.maxPrice  ? Number(params.maxPrice)  : undefined,
      endingSoon:      params.endingSoon === 'true' || undefined,
      buyNowAvailable: params.buyNow    === 'true' || undefined,
      sortBy:          params.sort,
      page:            currentPage,
      size:            20,
    }),
    auctionService.getCategoryCounts(),
  ])

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-grid)] px-4 py-8 pb-14 md:pb-8">
        <BrowseClient
          items={items}
          categoryCounts={categoryCounts}
          total={total}
          totalPages={totalPages}
          currentPage={currentPage}
          searchParams={params as Record<string, string | undefined>}
        />
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
```

- [ ] **Step 2: Update `frontend/components/auction/browse/index.ts`**

Add the two new component exports:

```ts
export { BrowseClient }               from './BrowseClient'
export { FilterPanel }                from './FilterPanel'
export { SortButton }                 from './SortButton'
export { CategoryFilter }             from './CategoryFilter'
export { PriceRangeFilter }           from './PriceRangeFilter'
export { QuickFilters }               from './QuickFilters'
export { AuctionBrowseCard }          from './AuctionBrowseCard'
export { AuctionBrowseGrid }          from './AuctionBrowseGrid'
export { AuctionBrowseCardHorizontal } from './AuctionBrowseCardHorizontal'
export { AuctionBrowseList }          from './AuctionBrowseList'
export { AuctionSearchInput }         from './AuctionSearchInput'
export { BrowsePagination }           from './BrowsePagination'
```

- [ ] **Step 3: Final type check — must be zero errors**

```bash
cd frontend && npx tsc --noEmit
```

Expected: **zero errors**.

- [ ] **Step 4: Commit**

```bash
git add frontend/app/auctions/page.tsx frontend/components/auction/browse/index.ts
git commit -m "feat(browse): wire page to parallel API fetches, pass full props to BrowseClient"
```

---

## Verification

After all tasks complete, start the dev server and test the following:

```bash
cd frontend && npm run dev
```

Open `http://localhost:3000/auctions` and verify:

- [ ] Page loads, shows listing count from `total` (e.g. "9 listings" for all mock data)
- [ ] Search box visible in toolbar; typing and submitting updates the `?q=` param and re-fetches
- [ ] Category filter changes pending state; clicking Apply updates URL with `?category=watches`
- [ ] Price range, Ending Soon, Buy Now update pending state; Apply fires navigation
- [ ] Sort dropdown changes URL instantly without requiring Apply
- [ ] Clear All navigates to `/auctions` (no params)
- [ ] Active filter count badge appears on mobile filter button when filters are active
- [ ] Pagination controls render when `totalPages > 1`; clicking a page number preserves filters
- [ ] Search from landing page (no `preserveParams`) navigates to `/auctions?q=keyword` with no other params
- [ ] Search from browse page with active category filter preserves `?category=watches&q=keyword`
- [ ] TypeScript: `npx tsc --noEmit` still reports zero errors
