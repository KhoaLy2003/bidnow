import { create } from 'zustand'
import { walletService } from '@/services/wallet.service'
import { mapPendingPaymentResponse } from '@/types/mappers/wallet.mapper'
import type { PaymentStatusLookup, PendingPayment } from '@/types/ui/wallet.ui'

interface PaymentState {
  /** The caller's unpaid winner holds. */
  holds:      PendingPayment[]
  /** Auctions paid in this browser session; lets the panel say "paid" once the hold has left the server list. */
  paidIds:    ReadonlySet<string>
  /** What wallet-service says became of each auction's hold (missing = not asked yet); see loadStatus. */
  statuses:   Record<string, PaymentStatusLookup>
  isLoading:  boolean
  hasFetched: boolean
  /** The latest load failed. The panel only surfaces it before the first successful load. */
  loadFailed: boolean
  load:       () => Promise<void>
  /** Reloads only if the store has been loaded before (nobody is looking otherwise). */
  refresh:    () => Promise<void>
  /** Reads this auction's hold status (winner or seller). The latest request per auction wins. */
  loadStatus: (auctionId: string) => Promise<void>
  markPaid:   (auctionId: string) => void
  reset:      () => void
}

export const usePaymentStore = create<PaymentState>((set, get) => {
  /** Bumped by every load and by reset(); only the latest load may write its result. */
  let seq = 0
  /** Source of request tokens for loadStatus. */
  let statusCounter = 0
  /** The one token allowed to write each auction's lookup; cleared by reset() and, per auction, by markPaid(). */
  const latestStatusToken = new Map<string, number>()

  return {
    holds:      [],
    paidIds:    new Set<string>(),
    statuses:   {},
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

    loadStatus: async (auctionId) => {
      const token = ++statusCounter
      latestStatusToken.set(auctionId, token)
      set((s) => ({ statuses: { ...s.statuses, [auctionId]: { kind: 'loading' } } }))
      let lookup: PaymentStatusLookup
      try {
        const response = await walletService.getPaymentStatus(auctionId)
        lookup = response ? { kind: 'found', status: response.status } : { kind: 'not-found' }
      } catch {
        lookup = { kind: 'failed' }
      }
      if (latestStatusToken.get(auctionId) !== token) return
      set((s) => ({ statuses: { ...s.statuses, [auctionId]: lookup } }))
    },

    markPaid: (auctionId) => {
      // A lookup that started before the payment must not overwrite "completed" with stale data.
      latestStatusToken.delete(auctionId)
      set((s) => ({
        holds:    s.holds.filter((h) => h.auctionId !== auctionId),
        paidIds:  new Set(s.paidIds).add(auctionId),
        statuses: { ...s.statuses, [auctionId]: { kind: 'found', status: 'COMPLETED' } },
      }))
    },

    reset: () => {
      seq++
      latestStatusToken.clear()
      set({
        holds: [], paidIds: new Set<string>(), statuses: {},
        isLoading: false, hasFetched: false, loadFailed: false,
      })
    },
  }
})
