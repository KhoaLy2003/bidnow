import { describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { PaymentRequestError } from '@/services/wallet.service'
import { classifyPaymentError, describePaymentFailure } from './payment-errors'

function failure(status: number, errorCode: string, errors?: Record<string, string>): PaymentRequestError {
  return new PaymentRequestError(status, { status, errorCode, message: 'server message', errors })
}

describe('classifyPaymentError', () => {
  it('maps INSUFFICIENT_BALANCE with parsed amounts', () => {
    expect(classifyPaymentError(failure(400, 'INSUFFICIENT_BALANCE', { required: '450.00', availableBalance: '100' })))
      .toEqual({ kind: 'insufficient-balance', required: 450, available: 100 })
  })

  it('maps INSUFFICIENT_BALANCE without an errors map to null amounts', () => {
    expect(classifyPaymentError(failure(400, 'INSUFFICIENT_BALANCE')))
      .toEqual({ kind: 'insufficient-balance', required: null, available: null })
  })

  it('accepts numeric amounts as well as strings', () => {
    const err = failure(400, 'INSUFFICIENT_BALANCE', { required: 20, availableBalance: 10 } as unknown as Record<string, string>)
    expect(classifyPaymentError(err)).toEqual({ kind: 'insufficient-balance', required: 20, available: 10 })
  })

  it('maps the deadline, not-pending and not-found codes', () => {
    expect(classifyPaymentError(failure(400, 'PAYMENT_DEADLINE_EXPIRED'))).toEqual({ kind: 'expired' })
    expect(classifyPaymentError(failure(409, 'PAYMENT_NOT_PENDING'))).toEqual({ kind: 'no-longer-due' })
    expect(classifyPaymentError(failure(404, 'PAYMENT_HOLD_NOT_FOUND'))).toEqual({ kind: 'no-longer-due' })
  })

  it('maps account problems', () => {
    expect(classifyPaymentError(failure(404, 'SELLER_WALLET_NOT_FOUND'))).toEqual({ kind: 'account-problem' })
    expect(classifyPaymentError(failure(409, 'DEPOSIT_LOCK_CLOSED'))).toEqual({ kind: 'account-problem' })
  })

  it('maps INVALID_INPUT to the first field error, else the server message', () => {
    expect(classifyPaymentError(failure(400, 'INVALID_INPUT', { auctionId: 'is required' })))
      .toEqual({ kind: 'invalid', message: 'is required' })
    expect(classifyPaymentError(failure(400, 'INVALID_INPUT')))
      .toEqual({ kind: 'invalid', message: 'server message' })
  })

  it('maps 401, 503, SERVICE_UNAVAILABLE and network failures', () => {
    expect(classifyPaymentError(new PaymentRequestError(401, null))).toEqual({ kind: 'unauthenticated' })
    expect(classifyPaymentError(new PaymentRequestError(503, null))).toEqual({ kind: 'unavailable' })
    expect(classifyPaymentError(failure(500, 'SERVICE_UNAVAILABLE'))).toEqual({ kind: 'unavailable' })
    expect(classifyPaymentError(new TypeError('Failed to fetch'))).toEqual({ kind: 'unavailable' })
  })

  it('maps an expired session to unauthenticated', () => {
    expect(classifyPaymentError(new Error('session_expired'))).toEqual({ kind: 'unauthenticated' })
  })

  it('does not crash on a gateway 502 with a non-JSON body', () => {
    expect(classifyPaymentError(new PaymentRequestError(502, null))).toEqual({ kind: 'unknown' })
  })

  it('falls back to unknown for anything else', () => {
    expect(classifyPaymentError(failure(500, 'SOMETHING_ELSE'))).toEqual({ kind: 'unknown' })
    expect(classifyPaymentError('weird')).toEqual({ kind: 'unknown' })
  })
})

describe('describePaymentFailure', () => {
  it('words an insufficient balance with the amounts and keeps the form enabled', () => {
    const view = describePaymentFailure({ kind: 'insufficient-balance', required: 450, available: 100 })
    expect(view.message).toBe('You need $450 available to pay (you have $100). Add funds, then try again.')
    expect(view.disablesForm).toBe(false)
  })

  it('words an insufficient balance without amounts', () => {
    const view = describePaymentFailure({ kind: 'insufficient-balance', required: null, available: null })
    expect(view.message).toBe('You need more funds available to pay. Add funds, then try again.')
  })

  it('disables the form for terminal failures', () => {
    expect(describePaymentFailure({ kind: 'expired' })).toEqual({
      message: 'The payment deadline has passed.', disablesForm: true,
    })
    expect(describePaymentFailure({ kind: 'no-longer-due' })).toEqual({
      message: 'This payment is no longer due.', disablesForm: true,
    })
    expect(describePaymentFailure({ kind: 'account-problem' })).toEqual({
      message: "We couldn't complete this payment. Please contact support.", disablesForm: true,
    })
  })

  it('keeps the form enabled for retryable failures', () => {
    expect(describePaymentFailure({ kind: 'unavailable' }).disablesForm).toBe(false)
    expect(describePaymentFailure({ kind: 'unknown' }).disablesForm).toBe(false)
    expect(describePaymentFailure({ kind: 'unauthenticated' }).disablesForm).toBe(false)
    expect(describePaymentFailure({ kind: 'invalid', message: 'bad' })).toEqual({ message: 'bad', disablesForm: false })
  })
})
