'use client'

import { X } from 'lucide-react'
import type { ActiveFilterChip, ActiveFilterKey } from '@/lib/browse-utils'
import { cn } from '@/lib/utils'

interface ActiveFiltersProps {
  total:      number
  chips:      ActiveFilterChip[]
  updating?:  boolean
  onRemove:   (key: ActiveFilterKey) => void
  onClearAll: () => void
}

/** Result count plus one removable chip per applied filter. */
export function ActiveFilters({ total, chips, updating, onRemove, onClearAll }: ActiveFiltersProps) {
  return (
    <div className="mt-4 flex min-h-9 flex-wrap items-center gap-2.5">
      <p
        aria-live="polite"
        className={cn(
          'mr-1 text-sm font-medium transition-opacity duration-[var(--duration-tesla)]',
          updating && 'opacity-60',
        )}
      >
        <span className="font-mono">{total.toLocaleString()}</span> {total === 1 ? 'auction' : 'auctions'}
      </p>

      {chips.map((chip) => (
        <button
          key={chip.key}
          type="button"
          aria-label={`Remove filter ${chip.label}`}
          onClick={() => onRemove(chip.key)}
          className={cn(
            'flex h-[30px] items-center gap-1.5 rounded-full border border-[var(--color-border-strong)] bg-[var(--color-bg-elevated)] pl-3 pr-2 text-[13px]',
            'transition-colors duration-[var(--duration-tesla)] hover:bg-muted',
            'outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
          )}
        >
          <span>{chip.label}</span>
          <X aria-hidden className="size-3.5 text-muted-foreground" />
        </button>
      ))}

      {chips.length > 0 && (
        <button
          type="button"
          onClick={onClearAll}
          className="px-1 text-[13px] font-medium text-[var(--color-text-link)] hover:underline"
        >
          Clear all
        </button>
      )}
    </div>
  )
}
