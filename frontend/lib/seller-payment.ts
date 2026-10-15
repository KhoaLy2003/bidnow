import type { PaymentStatusLookup } from '@/types/ui/wallet.ui'

export type SellerPaymentState =
  | 'loading' | 'error' | 'awaiting' | 'overdue' | 'paid' | 'forfeited' | 'cancelled' | 'none'

const MINUTE_MS = 60_000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

/**
 * How long the winner has left to pay, or null when there is no deadline or it has passed.
 * `1d 4h` above a day, `5h 12m` above an hour, else `42m`; under a minute reads `<1m`.
 */
export function formatPaymentTimeLeft(deadline: Date | undefined, now: number = Date.now()): string | null {
  if (!deadline) return null
  const ms = deadline.getTime() - now
  if (ms <= 0) return null
  if (ms >= DAY_MS) {
    const days = Math.floor(ms / DAY_MS)
    const hours = Math.floor((ms % DAY_MS) / HOUR_MS)
    return `${days}d ${hours}h`
  }
  if (ms >= HOUR_MS) {
    const hours = Math.floor(ms / HOUR_MS)
    const minutes = Math.floor((ms % HOUR_MS) / MINUTE_MS)
    return `${hours}h ${minutes}m`
  }
  const minutes = Math.floor(ms / MINUTE_MS)
  return minutes < 1 ? '<1m' : `${minutes}m`
}

export interface SellerPaymentInput {
  lookup:          PaymentStatusLookup
  paymentDeadline?: Date
  winnerPaidAt?:   Date
  now?:            number
}

/**
 * What the seller sees for a sold auction. Wallet-service's hold status is the system of record; the auction's
 * own `winnerPaidAt` is a copy of it, used only when the lookup has nothing better to say (no hold row, request failed).
 */
export function deriveSellerPaymentState(input: SellerPaymentInput): SellerPaymentState {
  const { lookup, paymentDeadline, winnerPaidAt, now = Date.now() } = input

  if (lookup.kind === 'found') {
    switch (lookup.status) {
      case 'COMPLETED':
        return 'paid'
      case 'FORFEITED':
        return 'forfeited'
      case 'CANCELLED':
        return 'cancelled'
      case 'PENDING_PAYMENT':
        return paymentDeadline && paymentDeadline.getTime() <= now ? 'overdue' : 'awaiting'
      default:
        return 'none' // a status this build does not know: neutral state, not nothing
    }
  }

  if (winnerPaidAt) return 'paid'

  switch (lookup.kind) {
    case 'idle':
    case 'loading':
      return 'loading'
    case 'failed':
      return 'error'
    case 'not-found':
      return 'none'
  }
}
