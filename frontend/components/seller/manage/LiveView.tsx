'use client'

import dynamic from 'next/dynamic'
import Link from 'next/link'
import { Eye, Lock, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { AuctionFacts, PhotosCard } from '@/components/seller/manage/AuctionFacts'
import { BidFeed } from '@/components/seller/manage/BidFeed'
import { LiveStats } from '@/components/seller/manage/LiveStats'
import { ManageHeader } from '@/components/seller/manage/ManageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { countBidders, withDeltas } from '@/lib/seller-manage'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

/** A disabled action that shows it exists but is unavailable; `describedBy` points at the note explaining why. */
// Recharts is the heaviest dependency on this page and the chart sits below the fold, so load it on demand.
const PriceChart = dynamic(
  () => import('@/components/seller/manage/PriceChart').then((m) => m.PriceChart),
  { ssr: false, loading: () => <Skeleton className="h-[284px] w-full rounded-xl" /> },
)

export function LockedButton({ children, describedBy }: { children: React.ReactNode; describedBy?: string }) {
  return (
    <Button variant="outline" size="xl" disabled aria-describedby={describedBy} className="px-4">
      <Lock aria-hidden />
      {children}
    </Button>
  )
}

interface LiveViewProps {
  auction:      AuctionDetail
  /** Newest first. */
  bids:         BidEntry[]
  hasMoreBids:  boolean
  loadingBids:  boolean
  onLoadMore(): void
  onCancel():   void
}

/** A running auction: live numbers and bids, details locked, cancel as the one way out. */
export function LiveView({ auction, bids, hasMoreBids, loadingBids, onLoadMore, onCancel }: LiveViewProps) {
  const rows = withDeltas(bids)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4">
        <ManageHeader auction={auction}>
          <Button variant="outline" size="xl" className="px-4" render={<Link href={`/auctions/${auction.id}`} />} nativeButton={false}>
            <Eye aria-hidden />
            View public page
          </Button>
          <LockedButton describedBy="edit-lock-note">Edit</LockedButton>
          <LockedButton describedBy="edit-lock-note">Delete</LockedButton>
          <Button
            variant="outline"
            size="xl"
            className="border-[var(--color-danger-border)] px-4 text-[var(--color-danger-text)] hover:bg-[var(--color-danger-subtle)] hover:text-[var(--color-danger-text)]"
            onClick={onCancel}
          >
            <XCircle aria-hidden />
            Cancel auction
          </Button>
        </ManageHeader>

        <p
          id="edit-lock-note"
          className="flex items-start gap-3 rounded-xl border border-[var(--color-border-default)] bg-[var(--color-bg-elevated)] px-4 py-3 text-[13px] leading-relaxed text-[var(--color-text-secondary)]"
        >
          <Lock className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            <span className="font-medium text-foreground">Editing is locked.</span> This auction is live
            {auction.totalBids > 0 ? ` and has ${auction.totalBids} ${auction.totalBids === 1 ? 'bid' : 'bids'}` : ''}.
            Edit and delete are only available for drafts and auctions that haven&apos;t started. You can still cancel the auction.
          </span>
        </p>
      </div>

      <LiveStats
        auction={auction}
        rows={rows}
        bidders={countBidders(bids)}
        biddersApprox={hasMoreBids}
      />

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-[999_1_620px] flex-col gap-6">
          <PriceChart bids={bids} startingPrice={auction.startingPrice} />
          <BidFeed
            bids={bids}
            totalBids={auction.totalBids}
            hasMore={hasMoreBids}
            isLoading={loadingBids}
            onLoadMore={onLoadMore}
            live
            winningBidId={bids[0]?.id}
          />
        </div>

        <aside className="flex min-w-0 flex-[1_1_340px] flex-col gap-6">
          <AuctionFacts auction={auction} locked />
          <PhotosCard auction={auction} />
        </aside>
      </div>
    </div>
  )
}
