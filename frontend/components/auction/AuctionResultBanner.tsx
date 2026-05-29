import { Trophy, Clock } from 'lucide-react'
import { formatCurrency } from '@/lib/format'
import { AuctionStatus } from '@/lib/design-tokens'

interface AuctionResultBannerProps {
  readonly status:    AuctionStatus.Won | AuctionStatus.Lost | AuctionStatus.Closed
  readonly finalBid:  number
}

export function AuctionResultBanner({ status, finalBid }: AuctionResultBannerProps) {
  const isWon = status === AuctionStatus.Won

  if (isWon) {
    return (
      <div
        className="flex flex-col gap-2 p-4 border-b"
        style={{
          background: 'var(--color-auction-won-bg)',
          borderColor: 'var(--color-auction-won-border)',
          color: 'var(--color-auction-won-text)',
        }}
      >
        <div className="flex items-center gap-2">
          <Trophy className="size-4" />
          <span className="font-display font-medium text-base">
            🎉 You won this auction!
          </span>
        </div>
        <div className="flex items-baseline gap-2">
          <span className="text-[10px] uppercase tracking-widest opacity-70">Final bid</span>
          <span className="font-mono text-xl tracking-tight">{formatCurrency(finalBid)}</span>
        </div>
      </div>
    )
  }

  return (
    <div
      className="flex flex-col gap-1 p-4 border-b"
      style={{ background: 'var(--color-bg-elevated)' }}
    >
      <div className="flex items-center gap-2">
        <Clock className="size-4 text-muted-foreground" />
        <span className="font-display font-medium text-base">Auction ended</span>
      </div>
      <div className="flex items-baseline gap-2">
        <span className="text-[10px] uppercase tracking-widest text-muted-foreground">Final bid</span>
        <span className="font-mono text-lg text-muted-foreground tracking-tight">{formatCurrency(finalBid)}</span>
      </div>
    </div>
  )
}
