'use client'

import { cn } from '@/lib/utils'
import type { CategoryCount } from '@/types/ui/auction-browse.ui'

interface CategoryFilterProps {
  value:          string
  categoryCounts: CategoryCount[]
  onChange:       (slug: string) => void
}

export function CategoryFilter({ value, categoryCounts, onChange }: CategoryFilterProps) {
  const allCount = categoryCounts.reduce((sum, c) => sum + c.count, 0)

  const categories = [
    { id: 'all', label: 'All' },
    ...categoryCounts.map((c) => ({ id: c.slug, label: c.categoryName })),
  ]

  function getCount(slug: string): number {
    if (slug === 'all') return allCount
    return categoryCounts.find((c) => c.slug === slug)?.count ?? 0
  }

  return (
    <ul className="flex flex-col gap-0.5" role="listbox" aria-label="Category">
      {categories.map(({ id, label }) => {
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
