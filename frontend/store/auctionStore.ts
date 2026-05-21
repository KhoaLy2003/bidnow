import { create } from 'zustand'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Bid } from '@/types/auction'

interface AuctionState {
  currentBid:      number
  bidHistory:      Bid[]
  status:          AuctionStatus
  isOutbid:        boolean
  setBid:          (amount: number) => void
  addBidToHistory: (bid: Bid) => void
  setStatus:       (status: AuctionStatus) => void
  setOutbid:       (value: boolean) => void
  reset:           () => void
}

const initialState = {
  currentBid: 0,
  bidHistory: [] as Bid[],
  status:     AuctionStatus.Active,
  isOutbid:   false,
}

export const useAuctionStore = create<AuctionState>((set) => ({
  ...initialState,

  setBid: (amount) => set({ currentBid: amount }),

  addBidToHistory: (bid) =>
    set((state) => ({ bidHistory: [bid, ...state.bidHistory].slice(0, 100) })),

  setStatus: (status) => set({ status }),

  setOutbid: (value) => set({ isOutbid: value }),

  reset: () => set(initialState),
}))
