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
  // Draft state is only active while the input is focused.
  // When not focused, the displayed value is derived directly from the prop.
  const [minFocused, setMinFocused] = useState(false)
  const [maxFocused, setMaxFocused] = useState(false)
  const [draftMin,   setDraftMin]   = useState('')
  const [draftMax,   setDraftMax]   = useState('')

  const displayMin = minFocused ? draftMin : String(value.min)
  const displayMax = maxFocused ? draftMax : String(value.max)

  function handleMinFocus() {
    setDraftMin(String(value.min))
    setMinFocused(true)
  }

  function handleMaxFocus() {
    setDraftMax(String(value.max))
    setMaxFocused(true)
  }

  function handleMinChange(raw: string) {
    setDraftMin(raw)
    const dollars = parseFloat(raw)
    if (isNaN(dollars) || dollars < 0) return
    if (dollars > value.max) return  // would invert the range — wait for blur to clamp
    onChange({ min: dollars, max: value.max })
  }

  function handleMaxChange(raw: string) {
    setDraftMax(raw)
    const dollars = parseFloat(raw)
    if (isNaN(dollars) || dollars < 0) return
    if (dollars < value.min) return  // would invert the range — wait for blur to clamp
    onChange({ min: value.min, max: Math.min(dollars, max) })
  }

  function handleMinBlur() {
    setMinFocused(false)
    const dollars = parseFloat(draftMin)
    if (isNaN(dollars) || dollars < 0) {
      onChange({ min: 0, max: value.max })
    } else {
      onChange({ min: Math.max(0, Math.min(dollars, value.max)), max: value.max })
    }
  }

  function handleMaxBlur() {
    setMaxFocused(false)
    const dollars = parseFloat(draftMax)
    if (isNaN(dollars) || dollars < 0) {
      onChange({ min: value.min, max })
    } else {
      onChange({ min: value.min, max: Math.max(value.min, Math.min(dollars, max)) })
    }
  }

  const minInvalid = minFocused && (() => {
    const d = parseFloat(draftMin)
    return !isNaN(d) && d > value.max
  })()

  const maxInvalid = maxFocused && (() => {
    const d = parseFloat(draftMax)
    return !isNaN(d) && (d < value.min || d > max)
  })()

  return (
    <div className="flex flex-col gap-3 px-1">
      <Slider
        min={0}
        max={max}
        step={1}
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
            max={value.max}
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
            min={value.min}
            max={max}
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
