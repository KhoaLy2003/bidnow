'use client'

import { CountdownTimer } from './CountdownTimer'
import { CurrentBidDisplay } from './CurrentBidDisplay'
import { BidForm } from './BidForm'
import { StatusBadge } from './StatusBadge'
import { formatCurrency } from '@/lib/format'
import { useWalletStore } from '@/store/walletStore'
import { PanelFooter } from './PanelFooter'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface BidPanelLiveProps {
  auction:               AuctionDetail
  isCurrentUserWinning:  boolean
}

export function BidPanelLive({ auction, isCurrentUserWinning }: BidPanelLiveProps) {
  const walletAvailable = useWalletStore((s) => s.available)

  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col">
      {/* Panel header */}
      <div className="flex items-center justify-between px-[18px] pt-[14px]">
        <StatusBadge status={auction.status} />
      </div>

      {/* Current bid */}
      <div className="px-[18px] pt-[14px] flex flex-col gap-1">
        <CurrentBidDisplay
          amount={auction.currentBid}
          isCurrentUserWinning={isCurrentUserWinning}
          status={auction.status}
          size="lg"
        />
        <span className="font-mono text-xs text-muted-foreground">
          · {auction.totalBids} bids
        </span>
      </div>

      {/* Countdown */}
      <div className="flex items-center gap-2 px-[18px] pt-[14px]">
        <CountdownTimer endsAt={auction.endsAt} size="md" />
        <span className="text-xs text-muted-foreground">remaining</span>
      </div>

      {/* Wallet balance */}
      <div className="flex items-center justify-between px-[18px] pt-[14px] text-xs text-muted-foreground">
        <span>Wallet balance</span>
        <span className="font-mono text-foreground">{formatCurrency(walletAvailable)}</span>
      </div>

      {/* Bid form */}
      <div className="px-[18px] pt-3 pb-[18px] flex flex-col gap-2">
        <BidForm
          auctionId={auction.id}
          currentBid={auction.currentBid}
          minIncrement={auction.bidIncrement}
        />

        {/* Buy Now block — only when buyNowPrice is set */}
        {auction.buyNowPrice !== undefined && (
          <div className="border border-[var(--color-success-border)] rounded-lg p-3 bg-[var(--color-success-subtle)] flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-mono uppercase tracking-widest text-[var(--color-success-text)]">
                or buy it now
              </span>
              <span className="font-mono text-sm font-bold text-[var(--color-success-text)]">
                {formatCurrency(auction.buyNowPrice)}
              </span>
            </div>
            <button
              type="button"
              onClick={() => { /* TODO: wire Buy Now flow */ }}
              className="w-full h-9 rounded text-sm font-medium bg-[var(--color-success-default)] text-white flex items-center justify-center transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:opacity-90"
            >
              Buy Now →
            </button>
          </div>
        )}
      </div>

      <PanelFooter />
    </div>
  )
}
