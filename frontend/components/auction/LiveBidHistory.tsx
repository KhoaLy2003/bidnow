'use client'

import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { BidHistory } from './BidHistory'
import { useLiveAuction } from '@/hooks/useLiveAuction'
import { useAuctionStore } from '@/store/auctionStore'
import { bidService, BID_PAGE_SIZE } from '@/services/bid.service'
import { toBidHistoryItem } from '@/types/mappers/bid.mapper'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

interface LiveBidHistoryProps {
  auction:        AuctionDetail
  initialBids:    BidEntry[]
  initialHasMore: boolean
}

export function LiveBidHistory({ auction: base, initialBids, initialHasMore }: LiveBidHistoryProps) {
  const { auction, bids, userId } = useLiveAuction(base)
  const [page, setPage]         = useState(0)
  const [hasMore, setHasMore]   = useState(initialHasMore)
  const [isLoading, setLoading] = useState(false)

  const entries = bids ?? initialBids
  const items = useMemo(
    () => entries.map((b) => toBidHistoryItem(b, { currentUserId: userId, currentPrice: auction.currentBid })),
    [entries, userId, auction.currentBid],
  )

  // New live bids shift offsets; a later page may repeat rows, which the store dedupes by id.
  async function loadMore() {
    if (isLoading) return
    setLoading(true)
    try {
      const next = await bidService.getAuctionBids(base.id, { page: page + 1, size: BID_PAGE_SIZE })
      useAuctionStore.getState().appendOlderBids(base.id, next.items)
      setPage(next.page)
      setHasMore(next.hasNext)
    } catch {
      toast.error('Could not load more bids')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section id="bids" className="overflow-hidden rounded-[20px] border">
      <h2 className="border-b px-5 py-[18px] font-display text-lg font-semibold">
        Bid History · {auction.totalBids}
      </h2>
      <BidHistory
        items={items}
        total={auction.totalBids}
        hasMore={hasMore}
        isLoading={isLoading}
        onLoadMore={loadMore}
        className="p-2"
      />
    </section>
  )
}
