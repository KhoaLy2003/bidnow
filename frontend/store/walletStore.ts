import { create } from 'zustand'
import type { Transaction, WalletBalance } from '@/types/ui/wallet.ui'

const LOW_BALANCE_THRESHOLD = 10  // $10.00

interface WalletState extends WalletBalance {
  isLow:           boolean
  transactions:    Transaction[]
  isLoading:       boolean
  hasFetched:      boolean
  setBalance:      (balance: WalletBalance) => void
  setTransactions: (transactions: Transaction[]) => void
  setLoading:      (v: boolean) => void
  setHasFetched:   (v: boolean) => void
}

export const useWalletStore = create<WalletState>((set) => ({
  available:    0,
  held:         0,
  total:        0,
  isLow:        false,
  transactions: [],
  isLoading:    true,
  hasFetched:   false,

  setBalance: (balance) =>
    set({ ...balance, isLow: balance.available < LOW_BALANCE_THRESHOLD }),

  setTransactions: (transactions) => set({ transactions }),

  setLoading: (v) => set({ isLoading: v }),

  setHasFetched: (v) => set({ hasFetched: v }),
}))
