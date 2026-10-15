import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { BidRequestError, bidService } from './bid.service'

const mockFetch = vi.mocked(apiFetch)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('bidService.buyNow', () => {
  beforeEach(() => mockFetch.mockReset())

  it('posts the auction id to the buy-now endpoint and returns the data', async () => {
    const data = { auctionId: 'a-1', price: 200, endedAt: '2026-10-01T12:00:00Z' }
    mockFetch.mockResolvedValue(json({ status: 201, data }, 201))

    const result = await bidService.buyNow('a-1')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/bids/buy-now', {
      method: 'POST',
      body: JSON.stringify({ auctionId: 'a-1' }),
    })
    expect(result).toEqual(data)
  })

  it('throws a BidRequestError carrying the status and error body', async () => {
    mockFetch.mockResolvedValue(
      json({ status: 409, errorCode: 'BUY_NOW_NOT_AVAILABLE', message: 'gone' }, 409))

    const err = await bidService.buyNow('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(BidRequestError)
    expect((err as BidRequestError).status).toBe(409)
    expect((err as BidRequestError).body?.errorCode).toBe('BUY_NOW_NOT_AVAILABLE')
  })

  it('keeps a null body when the error response is not JSON', async () => {
    mockFetch.mockResolvedValue(new Response('<html>', { status: 502 }))

    const err = await bidService.buyNow('a-1').catch((e: unknown) => e)

    expect((err as BidRequestError).status).toBe(502)
    expect((err as BidRequestError).body).toBeNull()
  })
})
