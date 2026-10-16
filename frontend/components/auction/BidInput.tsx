'use client'

import * as React from 'react'
import { Minus, Plus } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface BidInputProps extends React.ComponentPropsWithoutRef<'input'> {
  error?:        string
  onIncrement?:  () => void
  onDecrement?:  () => void
  showSteppers?: boolean
}

const STEPPER =
  'flex w-[52px] shrink-0 items-center justify-center text-foreground transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:bg-muted disabled:pointer-events-none disabled:opacity-50'

export const BidInput = React.forwardRef<HTMLInputElement, BidInputProps>(
  ({ error, onIncrement, onDecrement, showSteppers = false, className, disabled, ...props }, ref) => {
    return (
      <div className="flex flex-col gap-1">
        <div
          className={cn(
            'flex h-[52px] items-stretch overflow-hidden rounded-xl border border-input bg-background focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50',
            error && 'border-destructive',
          )}
        >
          {showSteppers && (
            <button
              type="button"
              aria-label="Decrease bid"
              tabIndex={-1}
              disabled={disabled}
              onClick={onDecrement}
              className={cn(STEPPER, 'border-r')}
            >
              <Minus className="size-[18px]" aria-hidden />
            </button>
          )}

          <div className="flex min-w-0 flex-1 items-center gap-1 px-3.5">
            <span aria-hidden className="select-none font-mono text-lg text-muted-foreground">$</span>
            <Input
              ref={ref}
              type="number"
              inputMode="decimal"
              aria-invalid={!!error}
              disabled={disabled}
              className={cn(
                // The wrapper draws the border and focus ring; the field itself stays bare.
                'h-full flex-1 rounded-none border-0 bg-transparent p-0 font-mono text-xl font-semibold shadow-none focus-visible:border-0 focus-visible:ring-0 aria-invalid:ring-0 md:text-xl dark:bg-transparent',
                '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none',
                className,
              )}
              {...props}
            />
          </div>

          {showSteppers && (
            <button
              type="button"
              aria-label="Increase bid"
              tabIndex={-1}
              disabled={disabled}
              onClick={onIncrement}
              className={cn(STEPPER, 'border-l')}
            >
              <Plus className="size-[18px]" aria-hidden />
            </button>
          )}
        </div>
        {error && (
          <p className="text-xs text-[var(--color-danger-text)]" role="alert">
            {error}
          </p>
        )}
      </div>
    )
  }
)
BidInput.displayName = 'BidInput'
