'use client'

import { useState, useEffect } from 'react'
import { Bell, BellOff } from 'lucide-react'
import { CountdownTimer } from './CountdownTimer'
import { StatusBadge } from './StatusBadge'
import { WatchingFooter } from './WatchingFooter'
import { useCountdown } from '@/hooks/useCountdown'
import { formatCurrency } from '@/lib/format'
import { AuctionStatus } from '@/lib/design-tokens'
import { cn } from '@/lib/utils'
import type { Auction } from '@/types/ui/auction.ui'

interface BidPanelUpcomingProps {
  auction: Auction
}

export function BidPanelUpcoming({ auction }: BidPanelUpcomingProps) {
  const [notifying, setNotifying] = useState(false)
  const [startDateStr, setStartDateStr] = useState<string | null>(null)
  const { isExpired } = useCountdown(auction.startsAt)

  // Format start date client-side only to avoid SSR timezone hydration mismatch
  useEffect(() => {
    setStartDateStr(
      auction.startsAt.toLocaleString('en-US', {
        weekday: 'short', month: 'short', day: 'numeric',
        hour: 'numeric', minute: '2-digit', timeZoneName: 'short',
      })
    )
  }, [auction.startsAt])

  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col">
      {/* Panel header */}
      <div className="flex items-center justify-between px-[18px] pt-[14px]">
        <StatusBadge status={AuctionStatus.Scheduled} />
        <span className="font-mono text-xs text-muted-foreground">#{auction.id}</span>
      </div>

      {/* Starting price */}
      <div className="flex flex-col gap-1 px-[18px] pt-4">
        <span className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground">
          Starting price
        </span>
        <span className="font-mono text-[34px] leading-none tracking-tight">
          {formatCurrency(auction.startingPrice)}
        </span>
      </div>

      {/* Countdown */}
      <div className="flex flex-col gap-2 px-[18px] pt-[18px]">
        <span className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground">
          Starts in
        </span>
        <div
          className="flex items-center justify-between px-4 py-4 rounded-lg border"
          style={{
            background:  'var(--color-auction-scheduled-bg)',
            borderColor: 'var(--color-auction-scheduled-border)',
          }}
        >
          {isExpired ? (
            <span
              className="font-mono text-sm"
              style={{ color: 'var(--color-auction-scheduled-text)' }}
            >
              Starting now…
            </span>
          ) : (
            <>
              <CountdownTimer endsAt={auction.startsAt} size="lg" />
              <span className="font-mono text-[10px] text-muted-foreground">HH:MM:SS</span>
            </>
          )}
        </div>
        {startDateStr && (
          <span className="font-mono text-[10.5px] text-muted-foreground">
            Begins {startDateStr}
          </span>
        )}
      </div>

      {/* Notify button */}
      <div className="px-[18px] py-[18px]">
        <button
          type="button"
          onClick={() => setNotifying(!notifying)}
          className={cn(
            'w-full h-11 rounded px-4 text-sm font-medium flex items-center justify-center gap-2 transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] border',
            notifying
              ? 'bg-[var(--color-text-brand)] text-white border-[var(--color-text-brand)]'
              : 'bg-background border-foreground text-foreground',
          )}
        >
          {notifying
            ? <><Bell className="size-3.5" /> You&apos;ll be notified</>
            : <><BellOff className="size-3.5" /> Notify me when live</>
          }
        </button>
      </div>

      <WatchingFooter n={auction.watchers} />
    </div>
  )
}
