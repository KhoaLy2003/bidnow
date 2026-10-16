'use client'

import { Loader2, Search, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface BrowseSearchFieldProps {
  value:     string
  onChange:  (value: string) => void
  /** Enter skips the debounce. */
  onSubmit:  () => void
  /** Results are on their way: shows a spinner in place of the clear button's neighbour. */
  updating?: boolean
  className?: string
}

export function BrowseSearchField({ value, onChange, onSubmit, updating, className }: BrowseSearchFieldProps) {
  return (
    <form
      role="search"
      className={cn('relative', className)}
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit()
      }}
    >
      <label htmlFor="browse-search" className="sr-only">Search auctions</label>
      <Search
        aria-hidden
        className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-muted-foreground"
      />
      <Input
        id="browse-search"
        type="search"
        autoComplete="off"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Search auctions by item, brand or keyword"
        className="h-12 rounded-lg pl-12 pr-20 text-base [&::-webkit-search-cancel-button]:hidden"
      />
      <div className="absolute right-1.5 top-1/2 flex -translate-y-1/2 items-center">
        {updating && (
          <Loader2 role="img" aria-label="Updating results" className="mr-1 size-4 animate-spin text-[var(--color-text-brand)]" />
        )}
        {value && (
          <button
            type="button"
            aria-label="Clear search"
            onClick={() => onChange('')}
            className="flex size-9 items-center justify-center rounded-full text-muted-foreground transition-colors duration-[var(--duration-tesla)] hover:bg-muted hover:text-foreground"
          >
            <X className="size-4" />
          </button>
        )}
      </div>
    </form>
  )
}
