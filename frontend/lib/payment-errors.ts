import { PaymentRequestError } from '@/services/wallet.service'
import { formatCurrency } from '@/lib/format'

export type PaymentFailure =
  | { kind: 'unauthenticated' }
  | { kind: 'insufficient-balance'; required: number | null; available: number | null }
  | { kind: 'expired' }
  | { kind: 'no-longer-due' }
  | { kind: 'account-problem' }
  | { kind: 'invalid'; message: string }
  | { kind: 'unavailable' }
  | { kind: 'unknown' }

export interface PaymentFailureView {
  message:      string
  disablesForm: boolean
}

function parseAmount(value: unknown): number | null {
  if (value === undefined || value === null || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

export function classifyPaymentError(err: unknown): PaymentFailure {
  if (err instanceof PaymentRequestError) {
    const code = err.body?.errorCode
    const errors = err.body?.errors ?? {}
    if (err.status === 401) return { kind: 'unauthenticated' }
    switch (code) {
      case 'INSUFFICIENT_BALANCE':
        return {
          kind: 'insufficient-balance',
          required: parseAmount(errors.required),
          available: parseAmount(errors.availableBalance),
        }
      case 'PAYMENT_DEADLINE_EXPIRED':
        return { kind: 'expired' }
      case 'PAYMENT_NOT_PENDING':
      case 'PAYMENT_HOLD_NOT_FOUND':
        return { kind: 'no-longer-due' }
      case 'SELLER_WALLET_NOT_FOUND':
      case 'DEPOSIT_LOCK_CLOSED':
        return { kind: 'account-problem' }
      case 'INVALID_INPUT': {
        const first = Object.values(errors)[0]
        return { kind: 'invalid', message: first ?? err.message }
      }
    }
    if (err.status === 503 || code === 'SERVICE_UNAVAILABLE') return { kind: 'unavailable' }
    return { kind: 'unknown' }
  }
  if (err instanceof Error && err.message === 'session_expired') return { kind: 'unauthenticated' }
  if (err instanceof TypeError) return { kind: 'unavailable' } // fetch network failure
  return { kind: 'unknown' }
}

export function describePaymentFailure(failure: PaymentFailure): PaymentFailureView {
  switch (failure.kind) {
    case 'insufficient-balance': {
      const needs = failure.required !== null ? formatCurrency(failure.required) : 'more funds'
      const has = failure.available !== null ? ` (you have ${formatCurrency(failure.available)})` : ''
      return {
        message: `You need ${needs} available to pay${has}. Add funds, then try again.`,
        disablesForm: false,
      }
    }
    case 'expired':
      return { message: 'The payment deadline has passed.', disablesForm: true }
    case 'no-longer-due':
      return { message: 'This payment is no longer due.', disablesForm: true }
    case 'account-problem':
      return { message: "We couldn't complete this payment. Please contact support.", disablesForm: true }
    case 'invalid':
      return { message: failure.message, disablesForm: false }
    case 'unauthenticated':
      return { message: 'Please sign in to continue.', disablesForm: false }
    case 'unavailable':
      return { message: "We couldn't reach the payment service. Please try again.", disablesForm: false }
    case 'unknown':
      return { message: 'Could not complete your payment. Please try again.', disablesForm: false }
  }
}
