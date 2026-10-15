'use client'

import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { AuctionLiveBridge } from '@/components/auction/AuctionLiveBridge'
import { CancelAuctionDialog } from '@/components/seller/manage/CancelAuctionDialog'
import { EndedView } from '@/components/seller/manage/EndedView'
import { LiveView } from '@/components/seller/manage/LiveView'
import { useBidPaging } from '@/hooks/useBidPaging'
import { useLiveAuction } from '@/hooks/useLiveAuction'
import { getManageMode } from '@/lib/seller-manage'
import { getErrorMessage } from '@/lib/utils'
import { auctionService } from '@/services/auction.service'
import { useAuctionStore } from '@/store/auctionStore'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

interface LiveAuctionContainerProps {
  /** The server snapshot taken when the page loaded; the live store is layered over it. */
  base:           AuctionDetail
  initialBids:    BidEntry[]
  initialHasMore: boolean
  /** Reloads the auction from the server (after a cancel, or once the live store sees it end). */
  onChanged():    Promise<void>
}

/**
 * Keeps one auction live over the socket. When it ends or is cancelled the same container switches to the
 * ended layout, so the bridge (and its subscription) is never torn down mid-transition.
 */
export function LiveAuctionContainer({ base, initialBids, initialHasMore, onChanged }: LiveAuctionContainerProps) {
  const { auction, bids } = useLiveAuction(base)
  const entries = bids ?? initialBids

  const append = useCallback(
    (older: BidEntry[]) => useAuctionStore.getState().appendOlderBids(base.id, older),
    [base.id],
  )
  const { hasMore, isLoading, loadMore } = useBidPaging(base.id, initialHasMore, append)

  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const live = getManageMode(auction.status, auction.winnerId) === 'live'

  // The auction ended (or was cancelled) while open: pull the server's final view, which has the payment fields.
  useEffect(() => {
    if (!live) void onChanged()
  }, [live, onChanged])

  async function confirmCancel(reason: string) {
    setCancelling(true)
    try {
      await auctionService.cancelAuction(base.id, reason)
      toast.success('Auction cancelled.')
      setCancelOpen(false)
      await onChanged()
    } catch (error) {
      toast.error(getErrorMessage(error, 'Failed to cancel the auction. Please try again.'))
    } finally {
      setCancelling(false)
    }
  }

  return (
    <>
      <AuctionLiveBridge auction={base} initialBids={initialBids} />

      {live ? (
        <LiveView
          auction={auction}
          bids={entries}
          hasMoreBids={hasMore}
          loadingBids={isLoading}
          onLoadMore={() => void loadMore()}
          onCancel={() => setCancelOpen(true)}
        />
      ) : (
        <EndedView
          auction={auction}
          bids={entries}
          hasMoreBids={hasMore}
          loadingBids={isLoading}
          onLoadMore={() => void loadMore()}
        />
      )}

      <CancelAuctionDialog
        open={cancelOpen}
        auctionTitle={auction.title}
        totalBids={auction.totalBids}
        currentBid={auction.currentBid}
        submitting={cancelling}
        onClose={() => setCancelOpen(false)}
        onConfirm={(reason) => void confirmCancel(reason)}
      />
    </>
  )
}
