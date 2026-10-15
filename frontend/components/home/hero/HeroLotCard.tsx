'use client'

import Image from 'next/image'
import Link from 'next/link'
import NumberFlow from '@number-flow/react'
import { ArrowRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useCountdown } from '@/hooks/useCountdown'
import { splitCountdown } from '@/lib/auction-display'
import { resolveImageUrl } from '@/lib/image-utils'
import type { LiveLot } from '@/hooks/useLiveLot'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

interface HeroLotCardProps {
  lot:  AuctionBrowseItem
  live: LiveLot
}

const TIMER_TONE = {
  normal:   'text-foreground',
  warning:  'text-[var(--color-warning-default)]',
  critical: 'text-[var(--color-danger-default)]',
} as const

/** Frosted card under the watch: the real lot the dial is counting down, live. */
export function HeroLotCard({ lot, live }: HeroLotCardProps) {
  const { state } = live
  const { secondsLeft, timerState, isExpired } = useCountdown(state.endsAt)
  const units = splitCountdown(secondsLeft ?? 0)
  const imageUrl = resolveImageUrl(lot.primaryImageUrl) ?? (lot.primaryImageUrl ? undefined : '/placeholder-auction.png')
  const inAntiSnipeWindow = secondsLeft !== null && !isExpired && secondsLeft < 120

  return (
    <div className="relative rounded-2xl border border-white/15 bg-white/[0.07] p-5 backdrop-blur-xl">
      <div className="flex items-center gap-4">
        <div className="relative size-16 shrink-0 overflow-hidden rounded-xl bg-white/10">
          {imageUrl && <Image src={imageUrl} alt="" fill sizes="64px" className="object-cover" />}
        </div>
        <div className="min-w-0">
          <p className="text-xs text-muted-foreground">
            {lot.categoryName} · <span className="font-mono">{state.totalBids}</span> bids
          </p>
          <Link
            href={`/auctions/${lot.id}`}
            className="mt-0.5 block truncate font-display text-[length:var(--font-size-md)] font-medium hover:text-[var(--color-text-brand)] transition-colors duration-[var(--duration-tesla)]"
          >
            {lot.title}
          </Link>
        </div>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-4">
        <div>
          <p className="text-xs uppercase text-muted-foreground">Current bid</p>
          <NumberFlow
            value={state.currentBid}
            locales="en-US"
            format={{ style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 }}
            className="mt-1 block font-mono text-[length:var(--font-size-price-md)] font-medium leading-none"
          />
        </div>
        <div>
          <p className="text-xs uppercase text-muted-foreground">{isExpired ? 'Status' : 'Closes in'}</p>
          {isExpired ? (
            <p className="mt-1 font-mono text-[length:var(--font-size-timer-sm)] font-medium leading-none text-muted-foreground">
              Closed
            </p>
          ) : (
            <div
              className={cn(
                'mt-1 flex items-baseline gap-2 font-mono text-[length:var(--font-size-price-md)] font-medium leading-none',
                TIMER_TONE[timerState],
              )}
              role="timer"
              aria-label="Time left"
            >
              {units.map(({ value, unit }) => (
                <NumberFlow
                  key={unit}
                  value={Number(value)}
                  trend={-1}
                  format={{ minimumIntegerDigits: 2 }}
                  suffix={unit}
                  className="[&::part(suffix)]:ml-0.5 [&::part(suffix)]:text-[length:var(--font-size-sm)] [&::part(suffix)]:text-muted-foreground"
                />
              ))}
            </div>
          )}
        </div>
      </div>

      <p className="mt-4 rounded-lg bg-white/[0.06] px-3 py-2.5 text-sm leading-snug text-muted-foreground">
        {isExpired
          ? 'This auction has ended.'
          : inAntiSnipeWindow
            ? 'Anti-snipe is active: a bid now adds 5 minutes to the clock.'
            : 'A bid in the final 2 minutes adds 5 more. The ring drains in the last 5.'}
      </p>

      <Button
        variant="brand"
        size="xl"
        className="mt-4 h-12 w-full"
        render={<Link href={`/auctions/${lot.id}`} />}
        nativeButton={false}
      >
        {isExpired ? 'See the result' : 'Bid on this lot'}
        <ArrowRight className="size-4" />
      </Button>
    </div>
  )
}
