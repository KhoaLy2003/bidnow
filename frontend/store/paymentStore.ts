import { create } from 'zustand'
import { walletService } from '@/services/wallet.service'
import { mapPendingPaymentResponse } from '@/types/mappers/wallet.mapper'
import type { PendingPayment } from '@/types/ui/wallet.ui'

interface PaymentState {
  /** The caller's unpaid winner holds. */
  holds:      PendingPayment[]
  /** Auctions paid in this browser session; lets the panel say "paid" once the hold has left the server list. */
  paidIds:    ReadonlySet<string>
  isLoading:  boolean
  hasFetched: boolean
  /** The latest load failed. The panel only surfaces it before the first successful load. */
  loadFailed: boolean
  load:       () => Promise<void>
  /** Reloads only if the store has been loaded before (nobody is looking otherwise). */
  refresh:    () => Promise<void>
  markPaid:   (auctionId: string) => void
  reset:      () => void
}

export const usePaymentStore = create<PaymentState>((set, get) => {
  /** Bumped by every load and by reset(); only the latest load may write its result. */
  let seq = 0

  return {
    holds:      [],
    paidIds:    new Set<string>(),
    isLoading:  false,
    hasFetched: false,
    loadFailed: false,

    load: async () => {
      const mine = ++seq
      set({ isLoading: true })
      try {
        const dtos = await walletService.getPendingPayments()
        if (mine !== seq) return
        const paid = get().paidIds
        set({
          holds:      dtos.map(mapPendingPaymentResponse).filter((h) => !paid.has(h.auctionId)),
          hasFetched: true,
          loadFailed: false,
          isLoading:  false,
        })
      } catch {
        if (mine !== seq) return
        set({ loadFailed: true, isLoading: false })
      }
    },

    refresh: async () => {
      if (get().hasFetched) await get().load()
    },

    markPaid: (auctionId) =>
      set((s) => ({
        holds:   s.holds.filter((h) => h.auctionId !== auctionId),
        paidIds: new Set(s.paidIds).add(auctionId),
      })),

    reset: () => {
      seq++
      set({ holds: [], paidIds: new Set<string>(), isLoading: false, hasFetched: false, loadFailed: false })
    },
  }
})
