'use client'

import { useState } from 'react'
import { Slider } from '@/components/ui/slider'
import { Input }  from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { PriceRange } from '@/types/ui/browse.ui'

interface PriceRangeFilterProps {
  value:    PriceRange
  max:      number
  onChange: (range: PriceRange) => void
}

export function PriceRangeFilter({ value, max, onChange }: PriceRangeFilterProps) {
  const maxDollars = Math.round(max / 100)

  // Draft state is only active while the input is focused.
  // When not focused, the displayed value is derived directly from the prop.
  const [minFocused, setMinFocused] = useState(false)
  const [maxFocused, setMaxFocused] = useState(false)
  const [draftMin,   setDraftMin]   = useState('')
  const [draftMax,   setDraftMax]   = useState('')

  const displayMin = minFocused ? draftMin : String(Math.round(value.min / 100))
  const displayMax = maxFocused ? draftMax : String(Math.round(value.max / 100))

  function handleMinFocus() {
    setDraftMin(String(Math.round(value.min / 100)))
    setMinFocused(true)
  }

  function handleMaxFocus() {
    setDraftMax(String(Math.round(value.max / 100)))
    setMaxFocused(true)
  }

  function handleMinChange(raw: string) {
    setDraftMin(raw)
    const dollars = parseInt(raw, 10)
    if (isNaN(dollars) || dollars < 0) return
    const cents = dollars * 100
    if (cents > value.max) return  // would invert the range — wait for blur to clamp
    onChange({ min: cents, max: value.max })
  }

  function handleMaxChange(raw: string) {
    setDraftMax(raw)
    const dollars = parseInt(raw, 10)
    if (isNaN(dollars) || dollars < 0) return
    const cents = dollars * 100
    if (cents < value.min) return  // would invert the range — wait for blur to clamp
    onChange({ min: value.min, max: Math.min(cents, max) })
  }

  function handleMinBlur() {
    setMinFocused(false)
    const dollars = parseInt(draftMin, 10)
    if (isNaN(dollars) || dollars < 0) {
      onChange({ min: 0, max: value.max })
    } else {
      // Clamp: min cannot exceed max
      const cents = Math.max(0, Math.min(dollars * 100, value.max))
      onChange({ min: cents, max: value.max })
    }
  }

  function handleMaxBlur() {
    setMaxFocused(false)
    const dollars = parseInt(draftMax, 10)
    if (isNaN(dollars) || dollars < 0) {
      onChange({ min: value.min, max })
    } else {
      // Clamp: max cannot go below min or above ceiling
      const cents = Math.max(value.min, Math.min(dollars * 100, max))
      onChange({ min: value.min, max: cents })
    }
  }

  const minInvalid = minFocused && (() => {
    const d = parseInt(draftMin, 10)
    return !isNaN(d) && d * 100 > value.max
  })()

  const maxInvalid = maxFocused && (() => {
    const d = parseInt(draftMax, 10)
    return !isNaN(d) && (d * 100 < value.min || d * 100 > max)
  })()

  return (
    <div className="flex flex-col gap-3 px-1">
      <Slider
        min={0}
        max={max}
        step={100}
        value={[value.min, value.max]}
        onValueChange={(rawValue) => {
          const vals = Array.isArray(rawValue) ? rawValue : [rawValue]
          onChange({ min: vals[0] ?? 0, max: vals[1] ?? value.max })
        }}
        aria-label="Price range"
      />

      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <span className="pointer-events-none select-none absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
            $
          </span>
          <Input
            type="number"
            inputMode="numeric"
            value={displayMin}
            onChange={(e) => handleMinChange(e.target.value)}
            onFocus={handleMinFocus}
            onBlur={handleMinBlur}
            min={0}
            max={Math.round(value.max / 100)}
            placeholder="Min"
            className={cn(
              'h-8 pl-6 text-sm font-mono [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none',
              minInvalid && 'border-destructive focus-visible:ring-destructive/20',
            )}
          />
        </div>

        <span className="shrink-0 text-sm text-muted-foreground">—</span>

        <div className="relative flex-1">
          <span className="pointer-events-none select-none absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
            $
          </span>
          <Input
            type="number"
            inputMode="numeric"
            value={displayMax}
            onChange={(e) => handleMaxChange(e.target.value)}
            onFocus={handleMaxFocus}
            onBlur={handleMaxBlur}
            min={Math.round(value.min / 100)}
            max={maxDollars}
            placeholder="Max"
            className={cn(
              'h-8 pl-6 text-sm font-mono [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none',
              maxInvalid && 'border-destructive focus-visible:ring-destructive/20',
            )}
          />
        </div>
      </div>
    </div>
  )
}
