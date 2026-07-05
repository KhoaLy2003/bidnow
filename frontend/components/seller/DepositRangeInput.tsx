'use client'

import { CurrencyInput } from '@/components/ui/currency-input'
import { Label }         from '@/components/ui/label'
import { cn }            from '@/lib/utils'
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

  const clamped   = Math.max(min, Math.min(max, deposit > 0 ? deposit : min))
  const sliderPct = startingPrice > 0 && max > min
    ? ((clamped - min) / (max - min)) * 100
    : 0

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

        {startingPrice > 0 && max > min && (
          <div className="flex flex-1 flex-col gap-1 pb-5">
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground">5%</span>
              <input
                type="range"
                min={min}
                max={max}
                step={1}
                value={clamped}
                onChange={e => onChange(Number(e.target.value))}
                className={cn(
                  'flex-1 h-1 appearance-none rounded-full cursor-pointer',
                  '[&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:size-3',
                  '[&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2',
                  '[&::-webkit-slider-thumb]:border-primary [&::-webkit-slider-thumb]:bg-background',
                  '[&::-webkit-slider-thumb]:cursor-grab [&::-webkit-slider-thumb:active]:cursor-grabbing',
                  '[&::-moz-range-thumb]:size-3 [&::-moz-range-thumb]:rounded-full',
                  '[&::-moz-range-thumb]:border-2 [&::-moz-range-thumb]:border-primary',
                  '[&::-moz-range-thumb]:bg-background [&::-moz-range-thumb]:border-solid',
                )}
                style={{
                  background: `linear-gradient(to right, var(--primary) ${sliderPct}%, var(--border) ${sliderPct}%)`,
                }}
              />
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
