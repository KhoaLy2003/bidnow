'use client'

import { ArrowUpDown, ChevronDown, Search, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SELLER_SORTS, SELLER_SORT_LABELS, SELLER_STATUS_FILTERS, SELLER_STATUS_LABELS,
  type SellerFilterState, type SellerSort, type SellerStatusFilter,
} from '@/lib/seller-auctions'
import { cn } from '@/lib/utils'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'

const STATUS_DOT: Record<SellerStatusFilter, string | null> = {
  all:       null,
  live:      'bg-[var(--color-auction-active-accent)]',
  scheduled: 'bg-[var(--color-auction-scheduled-text)]',
  draft:     'bg-[var(--color-neutral-400)]',
  sold:      'bg-[var(--color-success-default)]',
  unsold:    'bg-[var(--color-danger-default)]',
}

interface AuctionFiltersProps {
  filters:    SellerFilterState
  categories: AuctionCategoryResponse[]
  /** Chip counts, already narrowed by the search and category. */
  counts:     Record<SellerStatusFilter, number>
  onChange(patch: Partial<SellerFilterState>): void
}

/** Live filtering: every control applies as it changes, there is no Apply button. */
export function AuctionFilters({ filters, categories, counts, onChange }: AuctionFiltersProps) {
  const categoryName = categories.find((c) => c.id === filters.categoryId)?.name

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-0 flex-[1_1_280px]">
          <label htmlFor="seller-search" className="sr-only">Search your auctions by title</label>
          <Search aria-hidden className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="seller-search"
            type="search"
            autoComplete="off"
            value={filters.query}
            onChange={(e) => onChange({ query: e.target.value })}
            placeholder="Search your auctions by title"
            className="h-11 pl-10 pr-11 text-sm [&::-webkit-search-cancel-button]:hidden"
          />
          {filters.query && (
            <button
              type="button"
              aria-label="Clear search"
              onClick={() => onChange({ query: '' })}
              className="absolute right-1 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition-colors duration-[var(--duration-tesla)] hover:bg-muted hover:text-foreground"
            >
              <X aria-hidden className="size-4" />
            </button>
          )}
        </div>

        <Select value={filters.categoryId} onValueChange={(v) => { if (v !== null) onChange({ categoryId: v }) }}>
          <SelectTrigger aria-label="Category" className="h-11 w-48 text-sm">
            <SelectValue placeholder="Category">
              {categoryName ?? 'All categories'}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">All categories</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <Button variant="outline" className="h-11 gap-2 px-3.5" aria-label={`Sort: ${SELLER_SORT_LABELS[filters.sort]}`} />
            }
          >
            <ArrowUpDown aria-hidden className="size-4 text-muted-foreground" />
            <span className="font-normal text-muted-foreground">Sort</span>
            <span>{SELLER_SORT_LABELS[filters.sort]}</span>
            <ChevronDown aria-hidden className="size-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuRadioGroup
              value={filters.sort}
              onValueChange={(v) => onChange({ sort: v as SellerSort })}
            >
              {SELLER_SORTS.map((s) => (
                <DropdownMenuRadioItem key={s} value={s}>{SELLER_SORT_LABELS[s]}</DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <div
        role="group"
        aria-label="Filter by status"
        className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden md:flex-wrap"
      >
        {SELLER_STATUS_FILTERS.map((key) => {
          const active = filters.status === key
          const dot = STATUS_DOT[key]
          return (
            <button
              key={key}
              type="button"
              aria-pressed={active}
              // Picking the chip that is already on goes back to All.
              onClick={() => onChange({ status: active && key !== 'all' ? 'all' : key })}
              className={cn(
                'flex h-10 shrink-0 items-center gap-2 rounded-md border px-3.5 text-[13px] font-medium',
                'transition-[background-color,border-color,color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
                'outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                active
                  ? 'border-foreground bg-foreground text-background'
                  : 'border-[var(--color-border-strong)] bg-background text-foreground hover:border-muted-foreground',
              )}
            >
              {dot && <span aria-hidden className={cn('size-2 rounded-full', dot)} />}
              {SELLER_STATUS_LABELS[key]}
              <span className={cn('font-mono text-xs font-normal', active ? 'opacity-70' : 'text-muted-foreground')}>
                {counts[key]}
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

interface ResultsBarProps {
  shown:      number
  total:      number
  filters:    SellerFilterState
  categories: AuctionCategoryResponse[]
  onChange(patch: Partial<SellerFilterState>): void
  onClearAll(): void
}

/** "Showing 9 of 14" plus one removable chip per applied filter. */
export function AuctionResultsBar({ shown, total, filters, categories, onChange, onClearAll }: ResultsBarProps) {
  const chips: Array<{ key: string; label: string; remove(): void }> = []
  if (filters.query.trim()) chips.push({ key: 'query', label: `“${filters.query.trim()}”`, remove: () => onChange({ query: '' }) })
  if (filters.categoryId) {
    chips.push({
      key: 'category',
      label: categories.find((c) => c.id === filters.categoryId)?.name ?? 'Category',
      remove: () => onChange({ categoryId: '' }),
    })
  }
  if (filters.status !== 'all') chips.push({ key: 'status', label: SELLER_STATUS_LABELS[filters.status], remove: () => onChange({ status: 'all' }) })

  return (
    <div className="flex min-h-8 flex-wrap items-center gap-2">
      <p aria-live="polite" className="mr-1 text-[13px] text-muted-foreground">
        Showing <span className="font-mono text-foreground">{shown}</span> of <span className="font-mono">{total}</span> {total === 1 ? 'auction' : 'auctions'}
      </p>
      {chips.map((chip) => (
        <button
          key={chip.key}
          type="button"
          aria-label={`Remove filter ${chip.label}`}
          onClick={chip.remove}
          className="flex h-8 items-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-bg-elevated)] pl-2.5 pr-2 text-xs font-medium transition-colors duration-[var(--duration-tesla)] hover:bg-muted"
        >
          {chip.label}
          <X aria-hidden className="size-3 text-muted-foreground" />
        </button>
      ))}
      {chips.length > 0 && (
        <button type="button" onClick={onClearAll} className="px-1 text-xs font-medium text-[var(--color-text-link)] hover:underline">
          Clear all
        </button>
      )}
    </div>
  )
}
