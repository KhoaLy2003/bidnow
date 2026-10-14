import { BidRequestError } from '@/services/bid.service'
import { formatCurrency } from '@/lib/format'

export type BidFailure =
  | { kind: 'unauthenticated' }
  | { kind: 'insufficient-balance'; required: number | null; available: number | null }
  | { kind: 'too-low'; minimumBid: number | null }
  | { kind: 'not-open' }
  | { kind: 'own-auction' }
  | { kind: 'wallet-problem'; message: string }
  | { kind: 'unavailable' }
  | { kind: 'invalid'; message: string }
  | { kind: 'unknown' }

export interface BidFailureView {
  message:      string
  action?:      { href: string; label: string }
  disablesForm: boolean
}

function parseAmount(value: string | undefined): number | null {
  if (value === undefined) return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

export function classifyBidError(err: unknown): BidFailure {
  if (err instanceof BidRequestError) {
    const code = err.body?.errorCode
    const errors = err.body?.errors ?? {}
    if (err.status === 401) return { kind: 'unauthenticated' }
    switch (code) {
      case 'BID_INSUFFICIENT_BALANCE':
        return {
          kind: 'insufficient-balance',
          required: parseAmount(errors.required),
          available: parseAmount(errors.availableBalance),
        }
      case 'BID_TOO_LOW':
        return { kind: 'too-low', minimumBid: parseAmount(errors.minimumBid) }
      case 'AUCTION_NOT_OPEN':
      case 'AUCTION_NOT_FOUND':
        return { kind: 'not-open' }
      case 'BID_OWN_AUCTION':
        return { kind: 'own-auction' }
      case 'WALLET_NOT_ACTIVE':
      case 'WALLET_NOT_FOUND':
      case 'DEPOSIT_LOCK_CLOSED':
        return { kind: 'wallet-problem', message: err.message }
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

export function describeBidFailure(failure: BidFailure): BidFailureView {
  switch (failure.kind) {
    case 'insufficient-balance': {
      const needs = failure.required !== null ? formatCurrency(failure.required) : 'the deposit amount'
      const has = failure.available !== null ? ` (you have ${formatCurrency(failure.available)})` : ''
      return {
        message: `You need ${needs} available to lock this auction's deposit${has}.`,
        action: { href: '/wallet', label: 'Top up wallet' },
        disablesForm: false,
      }
    }
    case 'too-low':
      return {
        message: failure.minimumBid !== null
          ? `Someone bid first — the minimum is now ${formatCurrency(failure.minimumBid)}.`
          : 'Your bid is below the current minimum.',
        disablesForm: false,
      }
    case 'not-open':
      return { message: 'This auction is not open for bidding.', disablesForm: true }
    case 'own-auction':
      return { message: "You can't bid on your own auction.", disablesForm: true }
    case 'wallet-problem':
      return { message: failure.message, action: { href: '/wallet', label: 'Go to wallet' }, disablesForm: false }
    case 'unavailable':
      return { message: 'Bidding is temporarily unavailable. Please try again.', disablesForm: false }
    case 'invalid':
      return { message: failure.message, disablesForm: false }
    case 'unauthenticated':
      return { message: 'Please log in to bid.', action: { href: '/login', label: 'Log in' }, disablesForm: false }
    case 'unknown':
      return { message: 'Could not place your bid. Please try again.', disablesForm: false }
  }
}
