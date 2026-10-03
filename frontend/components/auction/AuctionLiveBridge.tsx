'use client'

import { useCallback, useEffect, useRef } from 'react'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuctionSocket } from '@/hooks/useAuctionSocket'
import { auctionService } from '@/services/auction.service'
import { bidService } from '@/services/bid.service'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

interface AuctionLiveBridgeProps {
  auction:     AuctionDetail
  initialBids: BidEntry[]
}

/** Seeds the live store from the server render and keeps it current over STOMP. Renders nothing. */
export function AuctionLiveBridge({ auction, initialBids }: AuctionLiveBridgeProps) {
  // Declared before the socket hook so the store is hydrated before the first push is applied.
  useEffect(() => {
    useAuctionStore.getState().hydrate(auction, initialBids)
  }, [auction, initialBids])

  // Cleared on unmount so an in-flight resync never hydrates after the page is gone.
  const activeRef = useRef(true)
  useEffect(() => {
    activeRef.current = true
    return () => {
      activeRef.current = false
      useAuctionStore.getState().reset()
    }
  }, [auction.id])

  const resync = useCallback(() => {
    void Promise.all([auctionService.getAuctionById(auction.id), bidService.getAuctionBids(auction.id)])
      .then(([fresh, page]) => {
        if (activeRef.current && fresh && fresh.id === auction.id) {
          useAuctionStore.getState().hydrate(fresh, page.items)
        }
      })
      .catch(() => {
        // Best effort: the next push or a reload catches up.
      })
  }, [auction.id])

  useAuctionSocket(auction.id, resync)
  return null
}
