import { create } from 'zustand'
import type { Transaction, WalletBalance } from '@/types/ui/wallet.ui'

const LOW_BALANCE_THRESHOLD = 10  // $10.00

interface WalletState extends WalletBalance {
  isLow:          boolean
  transactions:   Transaction[]
  isLoading:      boolean
  setBalance:     (balance: WalletBalance) => void
  addTransaction: (tx: Transaction) => void
  deposit:        (amount: number) => void
  setLoading:     (v: boolean) => void
}

export const useWalletStore = create<WalletState>((set) => ({
  available:    0,
  held:         0,
  total:        0,
  isLow:        false,
  transactions: [],
  isLoading:    false,

  setBalance: (balance) =>
    set({ ...balance, isLow: balance.available < LOW_BALANCE_THRESHOLD }),

  addTransaction: (tx) =>
    set((state) => ({ transactions: [tx, ...state.transactions] })),

  deposit: (amount) =>
    set((state) => {
      const available = state.available + amount
      return {
        available,
        total: available + state.held,
        isLow: available < LOW_BALANCE_THRESHOLD,
      }
    }),

  setLoading: (v) => set({ isLoading: v }),
}))
