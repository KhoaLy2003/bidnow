'use client'

import { useHasMounted } from '@/hooks/useHasMounted'
import { useCountdown } from '@/hooks/useCountdown'
import { formatCountdown, formatCurrency, formatRelativeTime } from '@/lib/format'
import type { FeedRow } from '@/lib/seller-manage'
import type { AuctionDetail } from '@/types/ui/auction.ui'

const END_FORMAT: Intl.DateTimeFormatOptions = {
  month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
}

interface LiveStatsProps {
  auction:    AuctionDetail
  /** Newest first, with the rise over the previous loaded bid. */
  rows:       FeedRow[]
  bidders:    number
  /** More bids exist than are loaded, so `bidders` is a lower bound. */
  biddersApprox: boolean
}

export function LiveStats({ auction, rows, bidders, biddersApprox }: LiveStatsProps) {
  const mounted = useHasMounted()
  const { secondsLeft, timerState } = useCountdown(auction.endsAt)
  const latest = rows[0]

  const tone =
    timerState === 'critical'
      ? { bg: 'var(--color-auction-critical-bg)', ink: 'var(--color-auction-critical-text)' }
      : timerState === 'warning'
        ? { bg: 'var(--color-auction-ending-bg)', ink: 'var(--color-auction-ending-text)' }
        : null

  return (
    <section
      aria-label="Live metrics"
      className="flex flex-wrap overflow-hidden rounded-xl border border-[var(--color-border-strong)]"
    >
      {/* Current bid */}
      <div className="flex min-w-[240px] flex-[1.3_1_260px] flex-col gap-1.5 border-b border-[var(--color-border-default)] p-5 sm:border-r sm:border-b-0">
        <p className="text-xs uppercase text-muted-foreground">Current bid</p>
        <p className="font-mono text-[length:var(--font-size-3xl)] font-medium leading-tight">
          {formatCurrency(auction.currentBid)}
        </p>
        <p className="text-xs text-[var(--color-text-secondary)]">
          {latest ? (
            <>
              {latest.delta !== null && latest.delta > 0 && (
                <span className="text-[var(--color-auction-won-text)]">↑ +{formatCurrency(latest.delta)} · </span>
              )}
              @{latest.bid.bidderName}
              {latest.bid.isAutoBid ? ' · auto-bid' : ''}
              {mounted ? ` · ${formatRelativeTime(latest.bid.placedAt)}` : ''}
            </>
          ) : (
            <>No bids yet · opens at {formatCurrency(auction.startingPrice)}</>
          )}
        </p>
      </div>

      {/* Time left */}
      <div
        className="flex min-w-[220px] flex-[1_1_240px] flex-col gap-1.5 border-b border-[var(--color-border-default)] p-5 sm:border-r sm:border-b-0"
        style={tone ? { background: tone.bg, color: tone.ink } : undefined}
      >
        <p className={`text-xs uppercase ${tone ? '' : 'text-muted-foreground'}`}>Time left</p>
        <p
          className="font-mono text-[length:var(--font-size-3xl)] font-medium leading-tight"
          aria-live="off"
        >
          {secondsLeft === null ? '—' : formatCountdown(secondsLeft)}
        </p>
        <p className={`text-xs ${tone ? '' : 'text-muted-foreground'}`}>
          ⚡ Anti-snipe armed · ends {auction.endsAt.toLocaleString('en-US', END_FORMAT)}
          {auction.extensionCount > 0 && ` · extended ${auction.extensionCount}×`}
        </p>
      </div>

      {/* Activity */}
      <div className="flex min-w-[180px] flex-[1_1_180px] flex-col gap-1.5 p-5">
        <p className="text-xs uppercase text-muted-foreground">Activity</p>
        <div className="flex gap-7">
          <div>
            <p className="font-mono text-[length:var(--font-size-2xl)] font-medium leading-tight">{auction.totalBids}</p>
            <p className="text-xs text-muted-foreground">bids</p>
          </div>
          <div>
            <p className="font-mono text-[length:var(--font-size-2xl)] font-medium leading-tight">
              {bidders}{biddersApprox ? '+' : ''}
            </p>
            <p className="text-xs text-muted-foreground">bidders</p>
          </div>
        </div>
      </div>
    </section>
  )
}
