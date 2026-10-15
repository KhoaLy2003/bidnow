'use client'

import { useCallback, useState } from 'react'
import { EndedView } from '@/components/seller/manage/EndedView'
import { useBidPaging } from '@/hooks/useBidPaging'
import { upsertBids } from '@/lib/realtime/auction-live'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

interface StaticEndedViewProps {
  auction:        AuctionDetail
  initialBids:    BidEntry[]
  initialHasMore: boolean
}

/** An auction that was already over when the page loaded: no socket, bids paged into local state. */
export function StaticEndedView({ auction, initialBids, initialHasMore }: StaticEndedViewProps) {
  const [bids, setBids] = useState(initialBids)
  const append = useCallback((older: BidEntry[]) => setBids((prev) => upsertBids(prev, older)), [])
  const { hasMore, isLoading, loadMore } = useBidPaging(auction.id, initialHasMore, append)

  return (
    <EndedView
      auction={auction}
      bids={bids}
      hasMoreBids={hasMore}
      loadingBids={isLoading}
      onLoadMore={() => void loadMore()}
    />
  )
}
