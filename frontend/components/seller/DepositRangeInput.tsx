'use client'

import { useMemo } from 'react'
import { Input }   from '@/components/ui/input'
import { Label }   from '@/components/ui/label'
import { cn }      from '@/lib/utils'
import { formatCurrency } from '@/lib/format'

interface DepositRangeInputProps {
  depositCents:      number
  startingPriceCents: number
  onChange(cents: number): void
}

export function DepositRangeInput({
  depositCents, startingPriceCents, onChange,
}: DepositRangeInputProps) {
  const minCents = Math.ceil(startingPriceCents * 0.05)
  const maxCents = Math.floor(startingPriceCents * 0.20)

  const pct = startingPriceCents > 0
    ? ((depositCents / startingPriceCents) * 100).toFixed(1)
    : '0.0'

  const inRange = startingPriceCents > 0
    ? depositCents >= minCents && depositCents <= maxCents
    : true

  const { thumbLeft } = useMemo(() => {
    if (startingPriceCents === 0) return { thumbLeft: '0%' }
    const range = maxCents - minCents
    const clamped = Math.max(minCents, Math.min(maxCents, depositCents))
    return { thumbLeft: range > 0 ? `${((clamped - minCents) / range) * 100}%` : '0%' }
  }, [depositCents, minCents, maxCents, startingPriceCents])

  function handleChange(raw: string) {
    const dollars = parseFloat(raw.replace(/[^0-9.]/g, ''))
    if (!isNaN(dollars)) onChange(Math.round(dollars * 100))
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-primary/50 bg-primary/[0.06] p-4">
      <div className="flex flex-col gap-0.5">
        <p className="font-medium text-sm">Deposit required to bid</p>
        <p className="text-xs text-muted-foreground">
          Bidders place a refundable deposit between 5% and 20% of starting price.
        </p>
      </div>

      <div className="flex items-end gap-4">
        <div className="flex flex-col gap-1.5 w-40">
          <Label className="text-xs font-medium text-muted-foreground">Amount</Label>
          <Input
            value={depositCents > 0 ? `$${(depositCents / 100).toFixed(2)}` : ''}
            onChange={e => handleChange(e.target.value)}
            placeholder="$ 0.00"
            className={cn('font-mono text-sm', !inRange && startingPriceCents > 0 && 'border-destructive')}
          />
          <p className={cn(
            'text-xs',
            !inRange && startingPriceCents > 0 ? 'text-destructive' : 'text-muted-foreground',
          )}>
            {startingPriceCents > 0
              ? `${pct}% of ${formatCurrency(startingPriceCents)} · must be 5–20%`
              : 'Set starting price first'}
          </p>
        </div>

        {startingPriceCents > 0 && (
          <div className="flex flex-1 flex-col gap-1 pb-5">
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground">5%</span>
              <div className="relative flex-1 h-1 bg-border rounded-full">
                <div
                  className="absolute left-0 top-0 h-full bg-primary rounded-full"
                  style={{ width: thumbLeft }}
                />
                <div
                  className="absolute top-1/2 size-3 -translate-y-1/2 -translate-x-1/2 rounded-full border-2 border-primary bg-background"
                  style={{ left: thumbLeft }}
                />
              </div>
              <span className="text-xs text-muted-foreground">20%</span>
            </div>
            <div className="flex justify-between text-[10px] text-muted-foreground px-4">
              <span>{formatCurrency(minCents)}</span>
              <span>{formatCurrency(maxCents)}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
