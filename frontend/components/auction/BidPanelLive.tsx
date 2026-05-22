'use client'

import { useEffect } from 'react'
import { CountdownTimer } from './CountdownTimer'
import { CurrentBidDisplay } from './CurrentBidDisplay'
import { BidForm } from './BidForm'
import { StatusBadge } from './StatusBadge'
import { WatchingFooter } from './WatchingFooter'
import { formatCurrency } from '@/lib/format'
import { useWalletStore } from '@/store/walletStore'
import type { Auction } from '@/types/auction'

interface BidPanelLiveProps {
  auction:               Auction
  isCurrentUserWinning:  boolean
}

export function BidPanelLive({ auction, isCurrentUserWinning }: BidPanelLiveProps) {
  const walletAvailable = useWalletStore((s) => s.available)

  // MOCK: seed display-only balance for dev. Remove when real wallet API is wired.
  useEffect(() => {
    if (useWalletStore.getState().available === 0) {
      useWalletStore.getState().setBalance({ available: 240_000, held: 0, total: 240_000 })
    }
  }, [])

  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col">
      {/* Panel header */}
      <div className="flex items-center justify-between px-[18px] pt-[14px]">
        <StatusBadge status={auction.status} />
        <span className="font-mono text-xs text-muted-foreground">#{auction.id}</span>
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
      <div className="px-[18px] pt-3 pb-[18px]">
        <BidForm
          auctionId={auction.id}
          currentBid={auction.currentBid}
          minIncrement={100}
        />
      </div>

      <WatchingFooter n={auction.watchers} />
    </div>
  )
}
