import { CountdownTimer }    from '@/components/auction/CountdownTimer'
import { CurrentBidDisplay } from '@/components/auction/CurrentBidDisplay'
import { formatCurrency }    from '@/lib/format'
import type { SellerAuction, SellerBidItem } from '@/types/seller'

interface AuctionMonitorPanelProps {
  auction: SellerAuction
  bids:    SellerBidItem[]
}

export function AuctionMonitorPanel({ auction, bids }: AuctionMonitorPanelProps) {
  return (
    <div className="flex flex-col gap-6">
      {/* Live stats strip */}
      <div className="grid grid-cols-4 divide-x divide-border rounded-xl border border-[var(--color-border-strong)] overflow-hidden">
        {/* Current bid */}
        <div className="flex flex-col gap-1 p-5">
          <CurrentBidDisplay amount={auction.currentBid} size="lg" />
          {bids.length > 0 && (
            <p className="text-xs text-muted-foreground">
              ↑ +{formatCurrency(auction.bidIncrement)} · @{bids[0].bidderName} · just now
            </p>
          )}
        </div>

        {/* Time left */}
        <div className="col-span-1 flex flex-col items-center justify-center gap-1 bg-[var(--color-auction-ending-bg)] p-5">
          <p className="text-[10px] font-medium uppercase text-[var(--color-auction-ending-text)]">Time Left</p>
          <CountdownTimer endsAt={auction.endsAt} size="lg" />
          <p className="text-xs text-[var(--color-auction-ending-text)]">⚡ Anti-snipe armed</p>
        </div>

        {/* Activity */}
        <div className="flex flex-col gap-2 p-5">
          <p className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">Activity</p>
          <div className="flex gap-5">
            <div>
              <p className="font-mono font-medium text-[length:var(--font-size-xl)]">{auction.totalBids}</p>
              <p className="text-xs text-muted-foreground">bids</p>
            </div>
            <div>
              <p className="font-mono font-medium text-[length:var(--font-size-xl)]">
                {new Set(bids.map(b => b.bidderName)).size}
              </p>
              <p className="text-xs text-muted-foreground">bidders</p>
            </div>
          </div>
        </div>

        {/* Watching */}
        <div className="flex flex-col gap-1 p-5">
          <p className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">Watching Now</p>
          <p className="font-mono font-medium text-[length:var(--font-size-xl)]">{auction.watchers}</p>
        </div>
      </div>

      {/* Bid feed */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <p className="font-medium text-sm">Live bid feed</p>
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <span className="inline-block size-1.5 rounded-full bg-[var(--color-auction-active-text)] animate-pulse" />
            updating
          </span>
        </div>

        <div className="rounded-md border border-[var(--color-border-default)]">
          {bids.slice(0, 7).map((bid, i) => (
            <div
              key={bid.id}
              className={`grid grid-cols-[24px_1fr_auto_16px] items-center gap-3 px-3 py-2 text-xs border-b border-[var(--color-border-default)] last:border-0 ${i === 0 ? 'bg-[var(--brand-50)]' : ''}`}
            >
              <div className="size-6 rounded-full border border-border bg-gradient-to-br from-[var(--brand-50)] to-[var(--brand-100)]" />
              <div>
                <p className="font-medium">@{bid.bidderName}</p>
                <p className="text-muted-foreground">
                  {bid.placedAt.toLocaleTimeString()} {bid.isAutoBid ? '· auto' : ''}
                </p>
              </div>
              <p className="font-mono font-medium">{formatCurrency(bid.amount)}</p>
              {bid.isWinning
                ? <span className="text-[var(--color-auction-won-text)]">★</span>
                : <span />
              }
            </div>
          ))}
        </div>

        {bids.length > 7 && (
          <button type="button" className="text-xs text-muted-foreground hover:text-foreground transition-colors duration-[var(--duration-tesla)] self-start underline underline-offset-2">
            Load {bids.length - 7} more →
          </button>
        )}
      </div>
    </div>
  )
}
