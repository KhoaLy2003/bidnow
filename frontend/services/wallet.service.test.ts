import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { PaymentRequestError, walletService } from './wallet.service'

const mockFetch = vi.mocked(apiFetch)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('walletService.getPendingPayments', () => {
  beforeEach(() => mockFetch.mockReset())

  it('gets the pending payments and returns the data list', async () => {
    const data = [{
      auctionId: 'a-1', totalAmount: 500, depositApplied: 50, remaining: 450,
      fundsHeld: true, deadline: '2026-10-17T10:00:00', hoursLeft: 47, expired: false,
    }]
    mockFetch.mockResolvedValue(json({ status: 200, data }))

    const result = await walletService.getPendingPayments()

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/wallets/payments/pending')
    expect(result).toEqual(data)
  })

  it('throws a PaymentRequestError carrying the status and body', async () => {
    mockFetch.mockResolvedValue(json({ status: 500, errorCode: 'INTERNAL_ERROR', message: 'boom' }, 500))

    const err = await walletService.getPendingPayments().catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(500)
    expect((err as PaymentRequestError).body?.errorCode).toBe('INTERNAL_ERROR')
  })
})

describe('walletService.confirmPayment', () => {
  beforeEach(() => mockFetch.mockReset())

  it('posts the auction id to the confirm endpoint and returns the data', async () => {
    const data = {
      auctionId: 'a-1', amountPaid: 500, depositApplied: 50, remainingPaid: 450,
      availableBalance: 100, lockedBalance: 0,
    }
    mockFetch.mockResolvedValue(json({ status: 200, data }))

    const result = await walletService.confirmPayment('a-1')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/wallets/payments/confirm', {
      method: 'POST',
      body: JSON.stringify({ auctionId: 'a-1' }),
    })
    expect(result).toEqual(data)
  })

  it('throws a PaymentRequestError carrying the status and error body', async () => {
    mockFetch.mockResolvedValue(
      json({ status: 400, errorCode: 'INSUFFICIENT_BALANCE', message: 'low', errors: { required: '450', availableBalance: '100' } }, 400))

    const err = await walletService.confirmPayment('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(400)
    expect((err as PaymentRequestError).body?.errorCode).toBe('INSUFFICIENT_BALANCE')
    expect((err as PaymentRequestError).body?.errors).toEqual({ required: '450', availableBalance: '100' })
  })

  it('keeps a null body when the error response is not JSON (gateway 502)', async () => {
    mockFetch.mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 }))

    const err = await walletService.confirmPayment('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(502)
    expect((err as PaymentRequestError).body).toBeNull()
  })
})

describe('walletService.getPaymentStatus', () => {
  beforeEach(() => mockFetch.mockReset())

  it('gets the status for an auction and returns the data', async () => {
    const data = { auctionId: 'a-1', status: 'COMPLETED', totalAmount: 500 }
    mockFetch.mockResolvedValue(json({ status: 200, data }))

    const result = await walletService.getPaymentStatus('a-1')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/wallets/payments/a-1')
    expect(result).toEqual(data)
  })

  it('returns null on a 404 with the backend error body (no hold for this caller)', async () => {
    mockFetch.mockResolvedValue(
      json({ status: 404, errorCode: 'PAYMENT_HOLD_NOT_FOUND', message: 'none' }, 404))

    await expect(walletService.getPaymentStatus('a-1')).resolves.toBeNull()
  })

  it('returns null on a 404 without a JSON body (for example a gateway 404)', async () => {
    mockFetch.mockResolvedValue(new Response('Not Found', { status: 404 }))

    await expect(walletService.getPaymentStatus('a-1')).resolves.toBeNull()
  })

  it('throws a PaymentRequestError for any other failure', async () => {
    mockFetch.mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 }))

    const err = await walletService.getPaymentStatus('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(502)
    expect((err as PaymentRequestError).body).toBeNull()
  })

  it('keeps the error body for a JSON failure', async () => {
    mockFetch.mockResolvedValue(json({ status: 500, errorCode: 'UNEXPECTED_ERROR', message: 'boom' }, 500))

    const err = await walletService.getPaymentStatus('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).body?.errorCode).toBe('UNEXPECTED_ERROR')
  })
})
