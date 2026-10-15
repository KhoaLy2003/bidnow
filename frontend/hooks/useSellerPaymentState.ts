'use client'

import { IDLE_LOOKUP } from '@/lib/payment-utils'
import { deriveSellerPaymentState, type SellerPaymentState } from '@/lib/seller-payment'
import { usePaymentStore } from '@/store/paymentStore'

/**
 * The seller-facing payment state of a sold auction, read from the shared payment store.
 * It never fetches: SellerPaymentPanel owns the request, so any number of readers (the result banner)
 * stay in step with it without duplicate calls.
 */
export function useSellerPaymentState(
  auctionId: string,
  paymentDeadline?: Date,
  winnerPaidAt?: Date,
): SellerPaymentState {
  // IDLE_LOOKUP is a module constant on purpose: a selector returning a fresh object makes zustand re-render forever.
  const lookup = usePaymentStore((s) => s.statuses[auctionId] ?? IDLE_LOOKUP)
  return deriveSellerPaymentState({ lookup, paymentDeadline, winnerPaidAt })
}
