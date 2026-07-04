'use client'

import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { useWalletStore } from '@/store/walletStore'
import { useAuthStore } from '@/store/authStore'
import { walletService } from '@/services/wallet.service'
import { mapWalletResponse, mapTransactionResponse } from '@/types/mappers/wallet.mapper'
import { getErrorMessage } from '@/lib/utils'

let fetchSeq = 0

export function useWallet() {
  const state = useWalletStore()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const [error, setError] = useState<string | null>(null)

  const fetchWallet = useCallback(() => {
    if (!isAuthenticated) return Promise.resolve()

    const seq = ++fetchSeq

    return Promise.all([
      walletService.getWallet(),
      walletService.getTransactions({ page: 0, size: 10 }),
    ])
      .then(([walletRes, transactionsRes]) => {
        if (seq !== fetchSeq) return // superseded by a newer fetch — discard this stale result
        state.setBalance(mapWalletResponse(walletRes.data))
        state.setTransactions(transactionsRes.data.data.map(mapTransactionResponse))
        state.setHasFetched(true)
        setError(null)
      })
      .catch((err: unknown) => {
        if (seq !== fetchSeq) return
        const message = getErrorMessage(err, 'Failed to load wallet')
        setError(message)
        toast.error(message)
      })
      .finally(() => {
        if (seq === fetchSeq) state.setLoading(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  useEffect(() => {
    if (isAuthenticated && !state.hasFetched) {
      fetchWallet()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, state.hasFetched])

  const depositFunds = useCallback(
    async (amount: number) => {
      await walletService.deposit(amount)
      await fetchWallet()
    },
    [fetchWallet]
  )

  return {
    available:    state.available,
    held:         state.held,
    total:        state.total,
    isLow:        state.isLow,
    transactions: state.transactions,
    isLoading:    state.isLoading,
    error,
    refetch:      fetchWallet,
    depositFunds,
  }
}
