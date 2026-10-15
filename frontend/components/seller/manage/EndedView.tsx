'use client'

import Link from 'next/link'
import { Eye } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { UserAvatar } from '@/components/shared/UserAvatar'
import { SellerPaymentPanel } from '@/components/seller/SellerPaymentPanel'
import { AuctionFacts, PhotosCard } from '@/components/seller/manage/AuctionFacts'
import { BidFeed } from '@/components/seller/manage/BidFeed'
import { LockedButton } from '@/components/seller/manage/LiveView'
import { ManageHeader } from '@/components/seller/manage/ManageHeader'
import { SaleResult, type SaleVariant } from '@/components/seller/manage/SaleResult'
import { useSellerPaymentState } from '@/hooks/useSellerPaymentState'
import { formatCurrency } from '@/lib/format'
import { countBidders, getManageMode } from '@/lib/seller-manage'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

interface EndedViewProps {
  auction:      AuctionDetail
  /** Newest first. */
  bids:         BidEntry[]
  hasMoreBids:  boolean
  loadingBids:  boolean
  onLoadMore(): void
}

/** A finished auction: what it fetched, who won, how payment is going, and the final bids. */
export function EndedView({ auction, bids, hasMoreBids, loadingBids, onLoadMore }: EndedViewProps) {
  const sold = getManageMode(auction.status, auction.winnerId) === 'sold'
  const payment = useSellerPaymentState(auction.id, auction.paymentDeadline, auction.winnerPaidAt)

  const winningBid = sold ? bids.find((b) => b.bidderId === auction.winnerId) : undefined
  const winnerName = winningBid?.bidderName

  const variant: SaleVariant = !sold ? 'unsold' : payment === 'forfeited' || payment === 'cancelled' ? 'forfeited' : 'sold'

  return (
    <div className="flex flex-col gap-6">
      <ManageHeader auction={auction} muted>
        <Button variant="outline" size="xl" className="px-4" render={<Link href={`/auctions/${auction.id}`} />} nativeButton={false}>
          <Eye aria-hidden />
          View public page
        </Button>
        <LockedButton>Edit</LockedButton>
      </ManageHeader>

      <SaleResult
        auction={auction}
        variant={variant}
        bidders={countBidders(bids)}
        biddersApprox={hasMoreBids}
        winnerName={winnerName}
      />

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-[999_1_620px] flex-col gap-6">
          {sold && (
            <SellerPaymentPanel
              auctionId={auction.id}
              amount={auction.currentBid}
              paymentDeadline={auction.paymentDeadline}
              winnerPaidAt={auction.winnerPaidAt}
              winnerName={winnerName}
            />
          )}
          <BidFeed
            bids={bids}
            totalBids={auction.totalBids}
            hasMore={hasMoreBids}
            isLoading={loadingBids}
            onLoadMore={onLoadMore}
            live={false}
            winningBidId={sold ? winningBid?.id : undefined}
          />
        </div>

        <aside className="flex min-w-0 flex-[1_1_340px] flex-col gap-6">
          {sold && (
            <section aria-labelledby="winner-heading" className="rounded-xl border border-[var(--color-border-default)] p-5">
              <h2 id="winner-heading" className="mb-3.5 text-sm font-medium">Winner</h2>
              <div className="flex items-center gap-3">
                <UserAvatar name={winnerName ?? 'Winner'} avatarUrl={winningBid?.bidderAvatarUrl} size="lg" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{winnerName ? `@${winnerName}` : 'Winner'}</p>
                  <p className="text-xs text-muted-foreground">
                    Winning bid <span className="font-mono">{formatCurrency(auction.currentBid)}</span>
                  </p>
                </div>
              </div>
            </section>
          )}
          <AuctionFacts auction={auction} locked={false} />
          <PhotosCard auction={auction} />
        </aside>
      </div>
    </div>
  )
}
