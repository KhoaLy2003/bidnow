'use client'

import { cn } from '@/lib/utils'
import { BROWSE_CATEGORIES } from '@/types/ui/browse.ui'

interface CategoryFilterProps {
  value:    string
  counts:   Record<string, number>
  onChange: (categoryName: string) => void
}

export function CategoryFilter({ value, counts, onChange }: CategoryFilterProps) {
  return (
    <ul className="flex flex-col gap-0.5" role="listbox" aria-label="Category">
      {BROWSE_CATEGORIES.map(({ id, label }) => {
        const isActive = value === id
        const count    = counts[id] ?? 0

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
              <span className={cn(
                'text-xs tabular-nums shrink-0',
                isActive ? 'text-white/80' : 'text-muted-foreground',
              )}>
                {count}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
