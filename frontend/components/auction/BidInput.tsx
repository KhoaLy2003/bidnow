'use client'

import * as React from 'react'
import { ChevronUp, ChevronDown } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface BidInputProps extends React.ComponentPropsWithoutRef<'input'> {
  error?:        string
  onIncrement?:  () => void
  onDecrement?:  () => void
  showSteppers?: boolean
}

export const BidInput = React.forwardRef<HTMLInputElement, BidInputProps>(
  ({ error, onIncrement, onDecrement, showSteppers = false, className, ...props }, ref) => {
    return (
      <div className="flex flex-col gap-1">
        <div className="relative flex items-center">
          <span className="pointer-events-none absolute left-3 text-sm font-mono font-medium text-muted-foreground select-none">
            $
          </span>
          <Input
            ref={ref}
            type="number"
            inputMode="decimal"
            aria-invalid={!!error}
            className={cn(
              'h-12 pl-7 font-mono font-medium text-base',
              showSteppers && 'pr-10',
              className,
            )}
            {...props}
          />
          {showSteppers && (
            <div className="absolute right-1 flex flex-col">
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                tabIndex={-1}
                onClick={onIncrement}
              >
                <ChevronUp className="size-3" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                tabIndex={-1}
                onClick={onDecrement}
              >
                <ChevronDown className="size-3" />
              </Button>
            </div>
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
