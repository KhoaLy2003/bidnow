'use client'

import { useMemo } from 'react'
import { CurrencyInput } from '@/components/ui/currency-input'
import { Label }   from '@/components/ui/label'
import { cn }      from '@/lib/utils'
import { formatCurrency } from '@/lib/format'

interface DepositRangeInputProps {
  readonly deposit:       number
  readonly startingPrice: number
  onChange(dollars: number): void
}

export function DepositRangeInput({
  deposit, startingPrice, onChange,
}: DepositRangeInputProps) {
  const min = Math.ceil(startingPrice * 0.05)
  const max = Math.floor(startingPrice * 0.20)

  const pct = startingPrice > 0
    ? ((deposit / startingPrice) * 100).toFixed(1)
    : '0.0'

  const inRange = startingPrice > 0
    ? deposit >= min && deposit <= max
    : true

  const { thumbLeft } = useMemo(() => {
    if (startingPrice === 0) return { thumbLeft: '0%' }
    const range = max - min
    const clamped = Math.max(min, Math.min(max, deposit))
    return { thumbLeft: range > 0 ? `${((clamped - min) / range) * 100}%` : '0%' }
  }, [deposit, min, max, startingPrice])

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
          <CurrencyInput
            value={deposit}
            onChange={onChange}
            placeholder="0.00"
            hasError={!inRange && startingPrice > 0}
          />
          <p className={cn(
            'text-xs',
            !inRange && startingPrice > 0 ? 'text-destructive' : 'text-muted-foreground',
          )}>
            {startingPrice > 0
              ? `${pct}% of ${formatCurrency(startingPrice)} · must be 5–20%`
              : 'Set starting price first'}
          </p>
        </div>

        {startingPrice > 0 && (
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
              <span>{formatCurrency(min)}</span>
              <span>{formatCurrency(max)}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
