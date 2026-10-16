'use client'

import { useState, useEffect, type ReactNode } from 'react'
import { Trophy, ArrowDown } from 'lucide-react'
import { formatCurrency } from '@/lib/format'
import { AuctionStatus } from '@/lib/design-tokens'
import { cn } from '@/lib/utils'
interface CurrentBidDisplayProps {
  amount:               number        // dollars
  isCurrentUserWinning?: boolean
  status?:              AuctionStatus
  size?:                'sm' | 'md' | 'lg'
  /** Sits beside the price on the same baseline, e.g. the bid count. */
  aside?:               ReactNode
  className?:           string
}

const PRICE_SIZE = {
  sm: 'text-[length:var(--font-size-price-sm)]',
  md: 'text-[length:var(--font-size-price-md)]',
  lg: 'text-[length:var(--font-size-price-lg)]',
} as const

const BANNER =
  'flex w-fit items-center gap-2 rounded-[10px] border px-3 py-2 text-[13px] font-semibold'

/** The animated live price, with a "you're winning" / "you've been outbid" banner beneath it. */
export function CurrentBidDisplay({
  amount,
  isCurrentUserWinning,
  status,
  size = 'md',
  aside,
  className,
}: CurrentBidDisplayProps) {
  const [displayedAmount, setDisplayedAmount] = useState(amount)
  const [animating, setAnimating] = useState(false)
  const [prevAmount, setPrevAmount] = useState(amount)
  const isOutbid = status === AuctionStatus.Outbid

  if (amount !== prevAmount) {
    setPrevAmount(amount)
    setAnimating(true)
  }

  useEffect(() => {
    if (!animating) return
    const t = setTimeout(() => {
      setDisplayedAmount(amount)
      setAnimating(false)
    }, 250)
    return () => clearTimeout(t)
  }, [animating, amount])

  return (
    <div className={cn('flex flex-col gap-3', className)}>
      <div className="flex flex-wrap items-baseline gap-x-3.5 gap-y-1">
        {/* Overflow container for price-roll animation */}
        <div
          className={cn(
            'relative overflow-hidden',
            animating && '[animation:bid-pulse_600ms_ease-in-out]',
            isOutbid && '[animation:outbid-flash_600ms_ease-in-out]',
          )}
        >
          {/* Exiting price */}
          {animating && (
            <span
              aria-hidden
              className={cn(
                'absolute inset-0 font-mono font-semibold leading-none tracking-tight',
                PRICE_SIZE[size],
                '[animation:price-roll-out_250ms_ease-in_forwards]',
              )}
            >
              {formatCurrency(displayedAmount)}
            </span>
          )}
          {/* Current price */}
          <span
            className={cn(
              'block font-mono font-semibold leading-none tracking-tight',
              PRICE_SIZE[size],
              animating && '[animation:price-roll-in_250ms_ease-out_forwards]',
            )}
          >
            {formatCurrency(animating ? displayedAmount : amount)}
          </span>
        </div>
        {aside}
      </div>

      {isCurrentUserWinning && !isOutbid && (
        <div
          role="status"
          className={cn(BANNER, 'border-[var(--color-auction-won-border)] bg-[var(--color-auction-won-bg)] text-[var(--color-auction-won-text)]')}
        >
          <Trophy className="size-4" aria-hidden />
          You&apos;re winning
        </div>
      )}
      {isOutbid && (
        <div
          role="status"
          className={cn(BANNER, 'border-[var(--color-auction-outbid-border)] bg-[var(--color-auction-outbid-bg)] text-[var(--color-auction-outbid-text)]')}
        >
          <ArrowDown className="size-4" aria-hidden />
          You&apos;ve been outbid
        </div>
      )}
    </div>
  )
}
