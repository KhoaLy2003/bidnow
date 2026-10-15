'use client'

import { useEffect } from 'react'
import { useAuthStore } from '@/store/authStore'
import { usePaymentStore } from '@/store/paymentStore'

/**
 * The winner's pending payments from the shared store. On mount it loads the store, or, if it already holds data,
 * revalidates it in the background (no skeleton flash) so a long-lived tab never shows a stale deadline. Live pushes,
 * window focus / reconnect (useUserNotifications) and payments keep it fresh.
 */
export function usePendingPayments() {
  const userId     = useAuthStore((s) => s.user?.id ?? null)
  const holds      = usePaymentStore((s) => s.holds)
  const paidIds    = usePaymentStore((s) => s.paidIds)
  const isLoading  = usePaymentStore((s) => s.isLoading)
  const hasFetched = usePaymentStore((s) => s.hasFetched)
  const loadFailed = usePaymentStore((s) => s.loadFailed)
  const load       = usePaymentStore((s) => s.load)

  useEffect(() => {
    if (!userId) return
    // Live state, not the render snapshot; runs only on mount / user change, so a failed load cannot retry in a loop.
    const s = usePaymentStore.getState()
    void (s.hasFetched ? s.refresh() : s.load())
  }, [userId])

  return { holds, paidIds, isLoading, hasFetched, loadFailed, refetch: load }
}
