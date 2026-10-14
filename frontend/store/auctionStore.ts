import { create } from 'zustand'
import {
  appendOlderBids, applyAuctionCancelled, applyAuctionEnded, applyAuctionExtended,
  applyBidPlaced, applyOwnBid, mergeSnapshot, seedLiveState,
  type LiveAuctionState,
} from '@/lib/realtime/auction-live'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'
import type { PlaceBidResponse } from '@/types/api/bid.api'
import type {
  AuctionEndedPayload, AuctionExtendedPayload, BidPlacedPayload,
} from '@/types/api/realtime.api'

interface AuctionState {
  live:            LiveAuctionState | null
  hydrate:         (auction: AuctionDetail, bids: BidEntry[]) => void
  bidPlaced:       (auctionId: string, p: BidPlacedPayload, meId: string | null) => void
  ownBidPlaced:    (r: PlaceBidResponse, me: { id: string; name: string }) => void
  extended:        (auctionId: string, p: AuctionExtendedPayload) => void
  ended:           (auctionId: string, p: AuctionEndedPayload) => void
  cancelled:       (auctionId: string) => void
  appendOlderBids: (auctionId: string, bids: BidEntry[]) => void
  reset:           () => void
}

/** Live state of the auction detail page being viewed. Pushes before hydration are dropped (resync covers them). */
export const useAuctionStore = create<AuctionState>((set) => {
  // Writes addressed to an auction other than the open one (late responses after navigation) are dropped.
  const update = (auctionId: string, fn: (live: LiveAuctionState) => LiveAuctionState) =>
    set((state) => (state.live?.auctionId === auctionId ? { live: fn(state.live) } : state))

  return {
    live: null,

    hydrate: (auction, bids) =>
      set((state) => {
        const seeded = seedLiveState(auction, bids)
        return { live: state.live?.auctionId === auction.id ? mergeSnapshot(state.live, seeded) : seeded }
      }),

    bidPlaced:       (id, p, meId) => update(id, (live) => applyBidPlaced(live, p, meId)),
    ownBidPlaced:    (r, me) => update(r.auctionId, (live) => applyOwnBid(live, r, me)),
    extended:        (id, p) => update(id, (live) => applyAuctionExtended(live, p)),
    ended:           (id, p) => update(id, (live) => applyAuctionEnded(live, p)),
    cancelled:       (id) => update(id, applyAuctionCancelled),
    appendOlderBids: (id, bids) => update(id, (live) => appendOlderBids(live, bids)),
    reset:           () => set({ live: null }),
  }
})
