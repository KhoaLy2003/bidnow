'use client'

import { useState } from 'react'
import { SlidersHorizontal, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Sheet, SheetClose, SheetContent, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import { PriceFilterBody } from './PriceFilter'
import { filterPillClass } from './FilterToggle'
import { countSheetFilters } from '@/lib/browse-utils'
import type { BrowseFilters, PriceRange } from '@/types/ui/browse.ui'

interface BrowseFiltersSheetProps {
  filters:     BrowseFilters
  /** Results for the filters the page is showing, for the confirm button. */
  total:       number
  onPrice:     (range: PriceRange, options: { debounced: boolean }) => void
  onEndingSoon: (value: boolean) => void
  onBuyNow:    (value: boolean) => void
  /** Clears price, ending soon and buy now (not the search or category). */
  onReset:     () => void
}

/** Mobile home for price and status filters. Every change still applies live behind the sheet. */
export function BrowseFiltersSheet({
  filters,
  total,
  onPrice,
  onEndingSoon,
  onBuyNow,
  onReset,
}: BrowseFiltersSheetProps) {
  const [open, setOpen] = useState(false)
  const count = countSheetFilters(filters)

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={<button type="button" className={filterPillClass(count > 0, 'md:hidden')} />}
      >
        <SlidersHorizontal aria-hidden className="size-4" />
        <span>Filters</span>
        {count > 0 && (
          <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-600 px-1.5 font-mono text-xs text-white">
            {count}
          </span>
        )}
      </SheetTrigger>

      <SheetContent
        side="bottom"
        showCloseButton={false}
        className="max-h-[88dvh] gap-0 rounded-t-2xl p-0"
      >
        <div className="flex items-center justify-between px-4 pt-3">
          <SheetTitle className="font-display text-xl">Filters</SheetTitle>
          <SheetClose
            render={
              <Button variant="ghost" size="icon" aria-label="Close filters" className="-mr-2 size-11" />
            }
          >
            <X className="size-5" />
          </SheetClose>
        </div>

        <div className="flex-1 overflow-y-auto px-4 pb-4 pt-2">
          <h3 className="mb-3 text-sm font-medium">Price</h3>
          <PriceFilterBody value={filters.priceRange} onChange={onPrice} touch />

          <h3 className="mb-1 mt-6 text-sm font-medium">Status</h3>
          <div className="flex min-h-14 items-center justify-between gap-3 border-b border-border">
            <div>
              <Label htmlFor="sheet-ending-soon" className="text-sm font-medium">Ending soon</Label>
              <p className="text-[13px] text-muted-foreground">Ends within the next hour</p>
            </div>
            <Switch id="sheet-ending-soon" checked={filters.endingSoon} onCheckedChange={onEndingSoon} />
          </div>
          <div className="flex min-h-14 items-center justify-between gap-3">
            <div>
              <Label htmlFor="sheet-buy-now" className="text-sm font-medium">Buy Now available</Label>
              <p className="text-[13px] text-muted-foreground">Skip the bidding at a fixed price</p>
            </div>
            <Switch id="sheet-buy-now" checked={filters.buyNow} onCheckedChange={onBuyNow} />
          </div>
        </div>

        <div className="flex items-center gap-3 border-t border-border px-4 pb-[calc(1rem+env(safe-area-inset-bottom))] pt-3">
          <Button variant="ghost" size="xl" disabled={count === 0} onClick={onReset} className="text-[var(--color-text-link)]">
            Reset
          </Button>
          <Button variant="brand" size="xl" className="flex-1" onClick={() => setOpen(false)} aria-live="polite">
            Show <span className="font-mono">{total.toLocaleString()}</span> {total === 1 ? 'auction' : 'auctions'}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  )
}
