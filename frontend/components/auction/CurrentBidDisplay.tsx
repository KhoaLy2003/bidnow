'use client'

import { useState, useEffect } from 'react'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { AuctionStatus } from '@/lib/design-tokens'
import { cn } from '@/lib/utils'
interface CurrentBidDisplayProps {
  amount:               number        // cents
  isCurrentUserWinning?: boolean
  status?:              AuctionStatus
  size?:                'sm' | 'md' | 'lg'
  className?:           string
}

const PRICE_SIZE = {
  sm: 'text-[length:var(--font-size-price-sm)]',
  md: 'text-[length:var(--font-size-price-md)]',
  lg: 'text-[length:var(--font-size-price-lg)]',
} as const

export function CurrentBidDisplay({
  amount,
  isCurrentUserWinning,
  status,
  size = 'md',
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
    <div className={cn('flex flex-col gap-1', className)}>
      <span className="text-xs text-muted-foreground uppercase font-medium">
        Current Bid
      </span>

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
              'absolute inset-0 font-mono font-medium leading-none',
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
            'block font-mono font-medium leading-none',
            PRICE_SIZE[size],
            animating && '[animation:price-roll-in_250ms_ease-out_forwards]',
          )}
        >
          {formatCurrency(animating ? displayedAmount : amount)}
        </span>
      </div>

      {isCurrentUserWinning && !isOutbid && (
        <Badge variant="won" className="w-fit">
          You&apos;re winning
        </Badge>
      )}
      {isOutbid && (
        <Badge variant="outbid" className="w-fit">
          You&apos;ve been outbid
        </Badge>
      )}
    </div>
  )
}
