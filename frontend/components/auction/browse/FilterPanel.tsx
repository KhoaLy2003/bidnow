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
