import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { auctionService } from './auction.service'

const mockFetch = vi.mocked(apiFetch)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('auctionService.cancelAuction', () => {
  beforeEach(() => mockFetch.mockReset())

  it('posts the trimmed reason to the cancel endpoint', async () => {
    mockFetch.mockResolvedValue(new Response(null, { status: 204 }))

    await auctionService.cancelAuction('a-1', '  Item damaged  ')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/auctions/a-1/cancel', {
      method: 'POST',
      body: JSON.stringify({ reason: 'Item damaged' }),
    })
  })

  it('sends no body when the reason is missing or blank', async () => {
    mockFetch.mockResolvedValue(new Response(null, { status: 204 }))

    await auctionService.cancelAuction('a-1')
    await auctionService.cancelAuction('a-2', '   ')

    expect(mockFetch).toHaveBeenNthCalledWith(1, '/api/v1/auctions/a-1/cancel', { method: 'POST' })
    expect(mockFetch).toHaveBeenNthCalledWith(2, '/api/v1/auctions/a-2/cancel', { method: 'POST' })
  })

  it('throws the error body on a non-2xx response', async () => {
    const error = { status: 400, message: 'Auction cannot be cancelled in status: COMPLETED' }
    mockFetch.mockResolvedValue(json(error, 400))

    await expect(auctionService.cancelAuction('a-1')).rejects.toEqual(error)
  })
})
