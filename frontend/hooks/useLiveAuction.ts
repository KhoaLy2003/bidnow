'use client'

import { useMemo } from 'react'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuthStore } from '@/store/authStore'
import { useHasMounted } from '@/hooks/useHasMounted'
import { deriveDisplayAuction } from '@/lib/realtime/auction-live'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'

/**
 * The auction as the viewer should see it: live store values over the server snapshot.
 * The user id is withheld until mount so server and first client render match.
 */
export function useLiveAuction(base: AuctionDetail): {
  auction:  AuctionDetail
  bids:     BidEntry[] | null
  isOutbid: boolean
  userId:   string | null
} {
  const live = useAuctionStore((s) => (s.live?.auctionId === base.id ? s.live : null))
  const storedUserId = useAuthStore((s) => s.user?.id ?? null)
  const mounted = useHasMounted()
  const userId = mounted ? storedUserId : null

  return useMemo(() => {
    const { auction, isOutbid } = deriveDisplayAuction(base, live, userId)
    return { auction, bids: live?.bids ?? null, isOutbid, userId }
  }, [base, live, userId])
}
