'use client'

import { useMemo, useState } from 'react'
import { Clock, LayoutGrid, List, SearchX, Tag } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { SortButton }          from './SortButton'
import { AuctionBrowseGrid }   from './AuctionBrowseGrid'
import { AuctionBrowseList }   from './AuctionBrowseList'
import { BrowsePagination }    from './BrowsePagination'
import { BrowseSearchField }   from './BrowseSearchField'
import { CategoryChips }       from './CategoryChips'
import { FilterToggle }        from './FilterToggle'
import { PriceFilter }         from './PriceFilter'
import { ActiveFilters }       from './ActiveFilters'
import { BrowseFiltersSheet }  from './BrowseFiltersSheet'
import { useBrowseQuery }      from '@/hooks/useBrowseQuery'
import { getActiveFilterChips, parseBrowseFilters } from '@/lib/browse-utils'
import { DEFAULT_MAX_PRICE }   from '@/types/ui/browse.ui'
import type { AuctionBrowseItem, CategoryCount } from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

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
  const { query, setFilters, setSort, clearOne, clearAll, flush, isUpdating } = useBrowseQuery(searchParams)
  const { filters, sort } = query
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')

  // Chips and counts describe the results on screen, so they follow the URL rather than the draft.
  const applied = useMemo(() => parseBrowseFilters(searchParams), [searchParams])
  const chips = useMemo(() => {
    const names = new Map(categoryCounts.map((c) => [c.slug, c.categoryName]))
    return getActiveFilterChips(applied, (slug) => names.get(slug) ?? slug)
  }, [applied, categoryCounts])

  const liveCount = categoryCounts.reduce((sum, c) => sum + c.count, 0)

  return (
    <div className="flex flex-col">
      <div>
        <h1 className="font-display font-medium text-[length:var(--font-size-2xl)]">Browse auctions</h1>
        {liveCount > 0 && (
          <p className="mt-1.5 flex items-center gap-2 text-sm text-muted-foreground">
            <span aria-hidden className="size-2 animate-pulse rounded-full bg-[var(--color-success-default)]" />
            <span><span className="font-mono">{liveCount.toLocaleString()}</span> live auctions</span>
          </p>
        )}
      </div>

      <BrowseSearchField
        className="mt-6"
        value={filters.q}
        onChange={(q) => setFilters({ q }, { debounced: true })}
        onSubmit={flush}
        updating={isUpdating}
      />

      <div className="mt-5">
        <CategoryChips
          value={filters.categorySlug}
          categoryCounts={categoryCounts}
          onChange={(categorySlug) => setFilters({ categorySlug })}
        />
      </div>

      {/* Filter bar */}
      <div className="relative mt-4 flex flex-wrap items-center gap-2 border-t border-border pt-4">
        <div
          aria-hidden
          className={cn(
            '-top-px absolute inset-x-0 h-0.5 overflow-hidden transition-opacity duration-[var(--duration-tesla)]',
            isUpdating ? 'opacity-100' : 'opacity-0',
          )}
        >
          <div className="h-full w-full animate-[progress-slide_0.6s_var(--ease-tesla)_infinite] bg-brand-600" />
        </div>

        <BrowseFiltersSheet
          filters={filters}
          total={total}
          onPrice={(priceRange, options) => setFilters({ priceRange }, options)}
          onEndingSoon={(endingSoon) => setFilters({ endingSoon })}
          onBuyNow={(buyNow) => setFilters({ buyNow })}
          onReset={() => setFilters({
            priceRange: { min: 0, max: DEFAULT_MAX_PRICE },
            endingSoon: false,
            buyNow:     false,
          })}
        />
        <div className="hidden md:block">
          <PriceFilter
            value={filters.priceRange}
            onChange={(priceRange, options) => setFilters({ priceRange }, options)}
          />
        </div>
        <FilterToggle
          pressed={filters.endingSoon}
          onPressedChange={(endingSoon) => setFilters({ endingSoon })}
          icon={<Clock aria-hidden className="size-4" />}
          title="Ends within 1 hour"
        >
          Ending soon
        </FilterToggle>
        <FilterToggle
          pressed={filters.buyNow}
          onPressedChange={(buyNow) => setFilters({ buyNow })}
          icon={<Tag aria-hidden className="size-4" />}
        >
          Buy Now<span className="hidden sm:inline"> available</span>
        </FilterToggle>

        <div className="ml-auto flex items-center gap-2">
          <SortButton value={sort} onChange={setSort} />
          <div role="group" aria-label="View" className="hidden overflow-hidden rounded-md border border-border sm:flex">
            <Button
              variant={viewMode === 'grid' ? 'secondary' : 'ghost'}
              size="icon-lg"
              className="size-10 rounded-none"
              aria-pressed={viewMode === 'grid'}
              onClick={() => setViewMode('grid')}
              aria-label="Grid view"
            >
              <LayoutGrid className="size-[18px]" />
            </Button>
            <Button
              variant={viewMode === 'list' ? 'secondary' : 'ghost'}
              size="icon-lg"
              className="size-10 rounded-none border-l border-border"
              aria-pressed={viewMode === 'list'}
              onClick={() => setViewMode('list')}
              aria-label="List view"
            >
              <List className="size-[18px]" />
            </Button>
          </div>
        </div>
      </div>

      <ActiveFilters
        total={total}
        chips={chips}
        updating={isUpdating}
        onRemove={clearOne}
        onClearAll={clearAll}
      />

      {/* Results */}
      <div
        aria-busy={isUpdating}
        className={cn(
          'mt-4 transition-opacity duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
          isUpdating && 'opacity-60',
        )}
      >
        {items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-[var(--color-border-strong)] px-6 py-16 text-center">
            <SearchX aria-hidden className="size-8 text-muted-foreground" />
            <h2 className="font-display text-lg font-medium">No auctions match these filters</h2>
            <p className="text-sm text-muted-foreground">
              {chips.length > 0 ? 'Loosen one of these to see more results.' : 'Check back soon, new lots are listed every day.'}
            </p>
            {chips.length > 0 && (
              <>
                <div className="flex flex-wrap justify-center gap-2">
                  {chips.map((chip) => (
                    <Button key={chip.key} variant="outline" className="h-9 rounded-full px-3.5" onClick={() => clearOne(chip.key)}>
                      Remove {chip.label}
                    </Button>
                  ))}
                </div>
                <Button variant="brand" size="lg" className="h-10 px-4" onClick={clearAll}>
                  Clear all filters
                </Button>
              </>
            )}
          </div>
        ) : viewMode === 'grid' ? (
          <AuctionBrowseGrid items={items} />
        ) : (
          <AuctionBrowseList items={items} />
        )}

        <BrowsePagination
          currentPage={currentPage}
          totalPages={totalPages}
          searchParams={searchParams}
        />
      </div>
    </div>
  )
}
