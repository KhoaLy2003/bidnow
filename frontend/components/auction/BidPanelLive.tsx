'use client'

import { Zap } from 'lucide-react'
import { BidCountdown } from './BidCountdown'
import { CurrentBidDisplay } from './CurrentBidDisplay'
import { BidForm } from './BidForm'
import { StatusBadge } from './StatusBadge'
import { BuyNowButton } from './BuyNowButton'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { formatCurrency } from '@/lib/format'
import { isLessThan } from '@/lib/money'
import { useWalletStore } from '@/store/walletStore'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface BidPanelLiveProps {
  auction:               AuctionDetail
  isCurrentUserWinning:  boolean
}

export function BidPanelLive({ auction, isCurrentUserWinning }: BidPanelLiveProps) {
  const walletAvailable = useWalletStore((s) => s.available)

  return (
    <div className="flex flex-col gap-[18px] rounded-[20px] border bg-card p-[22px] shadow-[0_1px_2px_rgb(20_22_26/0.04),0_12px_32px_rgb(20_22_26/0.06)]">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <span className="text-[13px] font-medium text-muted-foreground">Current bid</span>
        <StatusBadge status={auction.status} />
      </div>

      {/* Current bid */}
      <CurrentBidDisplay
        className="-mt-2"
        amount={auction.currentBid}
        isCurrentUserWinning={isCurrentUserWinning}
        status={auction.status}
        size="lg"
        aside={
          <a
            href="#bids"
            className="text-[13px] text-muted-foreground underline underline-offset-[3px] hover:text-foreground"
          >
            {auction.totalBids} {auction.totalBids === 1 ? 'bid' : 'bids'}
          </a>
        }
      />

      {/* Countdown */}
      <BidCountdown startsAt={auction.startsAt} endsAt={auction.endsAt} />

      {/* Bid form */}
      <BidForm
        auctionId={auction.id}
        currentBid={auction.currentBid}
        bidIncrement={auction.bidIncrement}
        startingPrice={auction.startingPrice}
        totalBids={auction.totalBids}
      />

      {/* Buy Now block — only while a buy-now price is set and the live price is still below it */}
      {auction.buyNowPrice !== undefined && isLessThan(auction.currentBid, auction.buyNowPrice) && (
        <div className="flex flex-col gap-2.5 rounded-[14px] border border-[var(--color-success-border)] bg-[var(--color-success-subtle)] px-4 py-3.5">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[11px] font-mono uppercase tracking-widest text-[var(--color-success-text)]">
              or buy it now
            </span>
            <span className="font-mono text-lg font-bold text-[var(--color-success-text)]">
              {formatCurrency(auction.buyNowPrice)}
            </span>
          </div>
          <BuyNowButton
            auctionId={auction.id}
            title={auction.title}
            buyNowPrice={auction.buyNowPrice}
            depositAmount={auction.depositAmount}
          />
        </div>
      )}

      {/* Account rows */}
      <dl className="flex flex-col gap-2.5 border-t pt-4 text-xs text-muted-foreground">
        <div className="flex items-center justify-between">
          <dt>Wallet balance</dt>
          <dd className="font-mono font-medium text-foreground">{formatCurrency(walletAvailable)}</dd>
        </div>
        <div className="flex items-center justify-between">
          <dt>Deposit required</dt>
          <dd className="font-mono font-medium text-foreground">{formatCurrency(auction.depositAmount)}</dd>
        </div>
        <div className="flex items-center justify-between">
          <dt>
            <Label htmlFor={`autobid-${auction.id}`} className="flex items-center gap-1.5 font-normal text-muted-foreground">
              <Zap className="size-3.5 text-[var(--color-text-brand)]" aria-hidden />
              Auto-bid <span className="text-[10px] uppercase tracking-wide">coming soon</span>
            </Label>
          </dt>
          <dd>
            <Switch id={`autobid-${auction.id}`} checked={false} disabled />
          </dd>
        </div>
      </dl>
    </div>
  )
}
