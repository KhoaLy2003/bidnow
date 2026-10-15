import { describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { BidRequestError } from '@/services/bid.service'
import { classifyBidError, describeBidFailure } from './bid-errors'

function failure(status: number, errorCode: string, errors?: Record<string, string>): BidRequestError {
  return new BidRequestError(status, { status, errorCode, message: 'm', errors })
}

describe('classifyBidError — buy now', () => {
  it('maps BUY_NOW_NOT_AVAILABLE to buy-now-unavailable', () => {
    expect(classifyBidError(failure(409, 'BUY_NOW_NOT_AVAILABLE'))).toEqual({ kind: 'buy-now-unavailable' })
  })

  it('still maps the shared codes', () => {
    expect(classifyBidError(failure(409, 'AUCTION_NOT_OPEN'))).toEqual({ kind: 'not-open' })
    expect(classifyBidError(failure(403, 'BID_OWN_AUCTION'))).toEqual({ kind: 'own-auction' })
    expect(classifyBidError(failure(403, 'BID_INSUFFICIENT_BALANCE', { required: '20.00', availableBalance: '10.00' })))
      .toEqual({ kind: 'insufficient-balance', required: 20, available: 10 })
  })

  it('maps 401, 503 and network failures', () => {
    expect(classifyBidError(new BidRequestError(401, null))).toEqual({ kind: 'unauthenticated' })
    expect(classifyBidError(new BidRequestError(503, null))).toEqual({ kind: 'unavailable' })
    expect(classifyBidError(new TypeError('Failed to fetch'))).toEqual({ kind: 'unavailable' })
  })
})

describe('describeBidFailure — buy now', () => {
  it('explains the price/auction state and disables the form', () => {
    const view = describeBidFailure({ kind: 'buy-now-unavailable' })

    expect(view.message).toBe(
      'Buy It Now is no longer available — the price has been reached or the auction has ended.')
    expect(view.disablesForm).toBe(true)
  })

  it('offers a wallet top-up link when the balance is too low', () => {
    const view = describeBidFailure({ kind: 'insufficient-balance', required: 20, available: 10 })

    expect(view.action).toEqual({ href: '/wallet', label: 'Top up wallet' })
    expect(view.disablesForm).toBe(false)
  })
})
