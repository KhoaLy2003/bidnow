'use client'

import { cn } from '@/lib/utils'
import type { CategoryCount } from '@/types/ui/auction-browse.ui'

interface CategoryChipsProps {
  value:          string
  categoryCounts: CategoryCount[]
  onChange:       (slug: string) => void
}

/** One-tap category row. Counts are live auctions per category across the whole catalogue. */
export function CategoryChips({ value, categoryCounts, onChange }: CategoryChipsProps) {
  const allCount = categoryCounts.reduce((sum, c) => sum + c.count, 0)
  const chips = [
    { slug: 'all', name: 'All', count: allCount },
    ...categoryCounts.map((c) => ({ slug: c.slug, name: c.categoryName, count: c.count })),
  ]

  return (
    <div
      role="group"
      aria-label="Category"
      className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    >
      {chips.map((chip) => {
        const active = value === chip.slug
        return (
          <button
            key={chip.slug}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(chip.slug)}
            className={cn(
              'flex h-10 shrink-0 items-center gap-2 rounded-full border px-4 text-sm font-medium',
              'transition-[background-color,border-color,color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
              'outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
              active
                ? 'border-brand-600 bg-brand-600 text-white'
                : 'border-border bg-background text-foreground hover:border-[var(--color-border-strong)]',
              !active && chip.count === 0 && 'opacity-50',
            )}
          >
            <span>{chip.name}</span>
            <span className={cn('font-mono text-xs font-normal', active ? 'text-white/80' : 'text-muted-foreground')}>
              {chip.count.toLocaleString()}
            </span>
          </button>
        )
      })}
    </div>
  )
}
