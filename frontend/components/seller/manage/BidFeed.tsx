'use client'

import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { UserAvatar } from '@/components/shared/UserAvatar'
import { useHasMounted } from '@/hooks/useHasMounted'
import { formatCurrency, formatRelativeTime } from '@/lib/format'
import { withDeltas } from '@/lib/seller-manage'
import { cn } from '@/lib/utils'
import type { BidEntry } from '@/types/ui/auction.ui'

type Filter = 'all' | 'auto'

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'All bids' },
  { key: 'auto', label: 'Auto-bids' },
]

const ABSOLUTE_TIME: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }

interface BidFeedProps {
  /** Newest first. */
  bids:        BidEntry[]
  totalBids:   number
  hasMore:     boolean
  isLoading:   boolean
  onLoadMore(): void
  /** Live auctions pulse and show relative times; ended ones show the final bids with absolute times. */
  live:        boolean
  /** The bid to star: the current leader while live, the winning bid once sold. */
  winningBidId?: string
}

export function BidFeed({ bids, totalBids, hasMore, isLoading, onLoadMore, live, winningBidId }: BidFeedProps) {
  const [filter, setFilter] = useState<Filter>('all')
  const mounted = useHasMounted()

  // Deltas are computed on the full list first, so filtering to auto-bids keeps each bid's true rise.
  const rows = withDeltas(bids).filter((r) => filter === 'all' || r.bid.isAutoBid)

  return (
    <section aria-labelledby="bid-feed-heading" className="overflow-hidden rounded-xl border border-[var(--color-border-default)]">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--color-border-default)] px-5 py-3.5">
        <div className="flex items-center gap-3">
          <h2 id="bid-feed-heading" className="text-sm font-medium">{live ? 'Live bid feed' : 'Bid history'}</h2>
          {live && (
            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className="inline-block size-1.5 animate-pulse rounded-full bg-[var(--color-auction-active-text)]" />
              updating
            </span>
          )}
        </div>

        <div role="group" aria-label="Filter bids" className="flex overflow-hidden rounded-md border border-[var(--color-border-strong)]">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              aria-pressed={filter === f.key}
              onClick={() => setFilter(f.key)}
              className={cn(
                'h-8 px-3.5 text-[13px] font-medium transition-colors duration-[var(--duration-tesla)]',
                filter === f.key ? 'bg-foreground text-background' : 'bg-background text-[var(--color-text-secondary)] hover:bg-muted',
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {rows.length === 0 ? (
        <p className="px-5 py-10 text-center text-sm text-muted-foreground">
          {filter === 'auto' && bids.length > 0 ? 'No auto-bids among the loaded bids.' : 'No bids yet.'}
        </p>
      ) : (
        <ul>
          {rows.map(({ bid, delta }) => {
            const winning = bid.id === winningBidId
            return (
              <li
                key={bid.id}
                className={cn(
                  'grid grid-cols-[32px_minmax(0,1fr)_auto_24px] items-center gap-3 border-b border-[var(--color-border-default)] px-5 py-2.5',
                  winning && 'bg-[var(--color-text-brand)]/10',
                )}
              >
                <UserAvatar name={bid.bidderName} avatarUrl={bid.bidderAvatarUrl} size="default" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">@{bid.bidderName}</p>
                  <p className="text-xs text-muted-foreground">
                    {live
                      ? (mounted ? formatRelativeTime(bid.placedAt) : '')
                      : bid.placedAt.toLocaleString('en-US', ABSOLUTE_TIME)}
                    {bid.isAutoBid && <span className="text-[var(--color-auction-scheduled-text)]"> · auto-bid</span>}
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-mono text-sm font-medium">{formatCurrency(bid.amount)}</p>
                  {delta !== null && delta > 0 && (
                    <p className="font-mono text-[11px] text-muted-foreground">+{formatCurrency(delta)}</p>
                  )}
                </div>
                <span
                  className="text-center text-[var(--color-auction-won-text)]"
                  aria-label={winning ? (live ? 'Leading bid' : 'Winning bid') : undefined}
                >
                  {winning ? '★' : ''}
                </span>
              </li>
            )
          })}
        </ul>
      )}

      <div className="flex items-center justify-between px-5 py-3">
        <span className="text-xs text-muted-foreground">
          Showing {bids.length} of {totalBids} bids
        </span>
        {hasMore && (
          <Button variant="link" size="sm" disabled={isLoading} onClick={onLoadMore}>
            {isLoading && <Loader2 className="mr-1 size-3 animate-spin" aria-hidden />}
            Load more →
          </Button>
        )}
      </div>
    </section>
  )
}
