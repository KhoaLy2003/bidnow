'use client'

import { useCallback, useRef, useState } from 'react'
import { toast } from 'sonner'
import { bidService, BID_PAGE_SIZE } from '@/services/bid.service'
import type { BidEntry } from '@/types/ui/auction.ui'

/**
 * Loads older bid pages on demand. The first page comes with the screen's initial data, so paging starts at 1.
 * `append` decides where the rows go (the live store, or local state on a static view); both dedupe by id,
 * so a page that repeats rows after new live bids shifted the offsets is harmless.
 */
export function useBidPaging(
  auctionId: string,
  initialHasMore: boolean,
  append: (older: BidEntry[]) => void,
): { hasMore: boolean; isLoading: boolean; loadMore: () => Promise<void> } {
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(initialHasMore)
  const [isLoading, setLoading] = useState(false)
  const inFlight = useRef(false)

  const loadMore = useCallback(async () => {
    if (inFlight.current) return
    inFlight.current = true
    setLoading(true)
    try {
      const next = await bidService.getAuctionBids(auctionId, { page: page + 1, size: BID_PAGE_SIZE })
      append(next.items)
      setPage(next.page)
      setHasMore(next.hasNext)
    } catch {
      toast.error('Could not load more bids')
    } finally {
      inFlight.current = false
      setLoading(false)
    }
  }, [auctionId, page, append])

  return { hasMore, isLoading, loadMore }
}
