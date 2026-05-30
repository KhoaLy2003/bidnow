'use client'

import { useState, useMemo } from 'react'
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
import { FilterPanel }       from './FilterPanel'
import { SortButton }        from './SortButton'
import { AuctionBrowseGrid } from './AuctionBrowseGrid'
import { AuctionBrowseList } from './AuctionBrowseList'
import {
  applyFilters,
  applySort,
  deriveMaxPrice,
  countActiveFilters,
  computeCategoryCounts,
} from '@/lib/browse-utils'
import {
  DEFAULT_FILTERS,
  DEFAULT_SORT,
  type BrowseFilters,
  type SortOption,
} from '@/types/ui/browse.ui'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

interface BrowseClientProps {
  items:        AuctionBrowseItem[]
  searchQuery?: string
}

export function BrowseClient({ items, searchQuery }: BrowseClientProps) {
  const maxPrice       = useMemo(() => deriveMaxPrice(items), [items])
  const categoryCounts = useMemo(() => computeCategoryCounts(items), [items])

  const initial: BrowseFilters = useMemo(
    () => ({ ...DEFAULT_FILTERS, priceRange: { min: 0, max: maxPrice } }),
    [maxPrice],
  )

  const [filters,    setFilters]    = useState<BrowseFilters>(initial)
  const [pending,    setPending]    = useState<BrowseFilters>(initial)
  const [sort,       setSort]       = useState<SortOption>(DEFAULT_SORT)
  const [viewMode,   setViewMode]   = useState<'grid' | 'list'>('grid')
  const [mobileOpen, setMobileOpen] = useState(false)

  const displayed = useMemo(
    () => applySort(applyFilters(items, filters), sort),
    [items, filters, sort],
  )

  const activeCount = useMemo(
    () => countActiveFilters(filters, maxPrice),
    [filters, maxPrice],
  )

  function handlePendingChange(partial: Partial<BrowseFilters>) {
    setPending((prev) => ({ ...prev, ...partial }))
  }

  function handleApply() {
    setFilters(pending)
    setMobileOpen(false)
  }

  function handleClearAll() {
    setFilters(initial)
    setPending(initial)
    setMobileOpen(false)
  }

  const panelProps = {
    maxPrice,
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
        <div className="mb-6 flex items-center justify-between gap-4">
          <div>
            <h1 className="font-display font-medium text-[length:var(--font-size-2xl)]">
              {searchQuery ? `Results for "${searchQuery}"` : 'Browse Auctions'}
            </h1>
            <p className="text-sm text-muted-foreground">
              {displayed.length} listing{displayed.length !== 1 ? 's' : ''}
            </p>
          </div>

          <div className="flex items-center gap-2">
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

            <SortButton value={sort} onChange={setSort} />

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

        {/* Grid or empty state */}
        {displayed.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-24 text-center">
            <p className="text-sm text-muted-foreground">
              No auctions match the current filters.
            </p>
            <Button variant="ghost" size="sm" onClick={handleClearAll}>
              Clear filters
            </Button>
          </div>
        ) : viewMode === 'grid'
            ? <AuctionBrowseGrid items={displayed} />
            : <AuctionBrowseList items={displayed} />
        }
      </div>
    </div>
  )
}
