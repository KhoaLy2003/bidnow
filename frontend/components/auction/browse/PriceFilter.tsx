'use client'

import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { Slider } from '@/components/ui/slider'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { filterPillClass } from './FilterToggle'
import { formatPriceRangeLabel } from '@/lib/browse-utils'
import {
  PRICE_PRESETS,
  SLIDER_MAX,
  SLIDER_MIN_GAP,
  isPriceFiltered,
  isSamePriceRange,
  parsePriceInput,
  rangeToSlider,
  sliderToRange,
} from '@/lib/browse-price'
import { DEFAULT_MAX_PRICE } from '@/types/ui/browse.ui'
import type { PriceRange } from '@/types/ui/browse.ui'
import { cn } from '@/lib/utils'

const FULL_RANGE: PriceRange = { min: 0, max: DEFAULT_MAX_PRICE }

/** `debounced` is true for slider drags and typing, false for presets and reset. */
type PriceChange = (range: PriceRange, options: { debounced: boolean }) => void

interface PriceBodyProps {
  value:    PriceRange
  onChange: PriceChange
  /** Taller inputs and presets for touch. */
  touch?:   boolean
}

interface PriceBoxProps {
  id:          string
  label:       string
  /** null renders an empty box showing the placeholder. */
  value:       number | null
  placeholder: string
  touch?:      boolean
  onType:      (raw: string) => void
  onCommit:    (raw: string) => void
}

/** Number box that shows what the user is typing while focused and the filter value otherwise. */
function PriceBox({ id, label, value, placeholder, touch, onType, onCommit }: PriceBoxProps) {
  const [draft, setDraft] = useState<string | null>(null)
  const shown = draft ?? (value === null ? '' : String(value))

  return (
    <div className="relative flex-1">
      <label htmlFor={id} className="sr-only">{label}</label>
      <span aria-hidden className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">$</span>
      <Input
        id={id}
        inputMode="decimal"
        autoComplete="off"
        value={shown}
        placeholder={placeholder}
        onFocus={() => setDraft(value === null ? '' : String(value))}
        onChange={(e) => {
          setDraft(e.target.value)
          onType(e.target.value)
        }}
        onBlur={() => {
          onCommit(draft ?? '')
          setDraft(null)
        }}
        className={cn('pl-6 font-mono', touch ? 'h-11 text-base' : 'h-10')}
      />
    </div>
  )
}

/** Histogram-free price control: slider, typed bounds and one-tap ranges. Used in the popover and the mobile sheet. */
export function PriceFilterBody({ value, onChange, touch }: PriceBodyProps) {
  const [low, high] = rangeToSlider(value)

  return (
    <div className="flex flex-col gap-4">
      <div className="px-1.5 pt-2">
        <Slider
          min={0}
          max={SLIDER_MAX}
          step={1}
          minStepsBetweenValues={SLIDER_MIN_GAP}
          value={[low, high]}
          onValueChange={(next) => {
            const [lo, hi] = Array.isArray(next) ? next : [next, next]
            const range = sliderToRange(lo, hi ?? lo)
            if (!isSamePriceRange(range, value)) onChange(range, { debounced: true })
          }}
          aria-label="Price range"
        />
      </div>

      <div className="flex items-center gap-2.5">
        <PriceBox
          id={touch ? 'price-min-sheet' : 'price-min'}
          label="Minimum price"
          value={value.min > 0 ? value.min : null}
          placeholder="0"
          touch={touch}
          onType={(raw) => {
            const min = parsePriceInput(raw, 0)
            if (min === null || min > value.max) return
            onChange({ min, max: value.max }, { debounced: true })
          }}
          onCommit={(raw) => {
            const min = parsePriceInput(raw, 0) ?? value.min
            onChange({ min: Math.min(min, value.max), max: value.max }, { debounced: false })
          }}
        />
        <span aria-hidden className="text-sm text-muted-foreground">to</span>
        <PriceBox
          id={touch ? 'price-max-sheet' : 'price-max'}
          label="Maximum price"
          value={value.max < DEFAULT_MAX_PRICE ? value.max : null}
          placeholder="No max"
          touch={touch}
          onType={(raw) => {
            const max = parsePriceInput(raw, DEFAULT_MAX_PRICE)
            if (max === null || max < value.min) return
            onChange({ min: value.min, max }, { debounced: true })
          }}
          onCommit={(raw) => {
            const max = parsePriceInput(raw, DEFAULT_MAX_PRICE) ?? value.max
            onChange({ min: value.min, max: Math.max(max, value.min) }, { debounced: false })
          }}
        />
      </div>

      <div className="flex flex-wrap gap-2">
        {PRICE_PRESETS.map((preset) => {
          const active = isSamePriceRange(preset.range, value)
          return (
            <button
              key={preset.label}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(active ? FULL_RANGE : preset.range, { debounced: false })}
              className={cn(
                'rounded-full border px-3 text-[13px] font-medium',
                'transition-[background-color,border-color,color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
                'outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                touch ? 'h-11' : 'h-8',
                active
                  ? 'border-brand-600 bg-accent text-[var(--color-text-brand)]'
                  : 'border-border bg-background hover:border-[var(--color-border-strong)]',
              )}
            >
              {preset.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}

interface PriceFilterProps {
  value:    PriceRange
  onChange: PriceChange
}

/** Desktop price control: a filter-bar pill that opens the price popover. */
export function PriceFilter({ value, onChange }: PriceFilterProps) {
  const active = isPriceFiltered(value)

  return (
    <Popover>
      <PopoverTrigger
        render={<button type="button" className={filterPillClass(active)} />}
      >
        <span>{formatPriceRangeLabel(value.min, value.max)}</span>
        <ChevronDown aria-hidden className="size-4" />
      </PopoverTrigger>
      <PopoverContent align="start" sideOffset={8} className="w-[380px] max-w-[calc(100vw-2rem)] gap-0 rounded-xl p-5">
        <PriceFilterBody value={value} onChange={onChange} />
        <div className="mt-5 flex items-center justify-between border-t border-border pt-3.5">
          <button
            type="button"
            disabled={!active}
            onClick={() => onChange(FULL_RANGE, { debounced: false })}
            className="text-sm font-medium text-[var(--color-text-link)] hover:underline disabled:pointer-events-none disabled:opacity-40"
          >
            Reset
          </button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
