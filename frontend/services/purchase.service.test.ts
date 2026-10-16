import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { purchaseService } from './purchase.service'

const mockFetch = vi.mocked(apiFetch)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('purchaseService.getMyPurchases', () => {
  beforeEach(() => mockFetch.mockReset())

  it('requests the first page of all purchases by default', async () => {
    const data = { data: [], pagination: { page: 0, limit: 10, total: 0, totalPages: 0, hasNext: false, hasPrev: false } }
    mockFetch.mockResolvedValue(json({ status: 200, data }))

    const result = await purchaseService.getMyPurchases()

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/auctions/me/purchases?page=0&size=10')
    expect(result).toEqual(data)
  })

  it('sends the status filter and page', async () => {
    mockFetch.mockResolvedValue(json({ status: 200, data: { data: [], pagination: {} } }))

    await purchaseService.getMyPurchases({ status: 'PENDING_PAYMENT', page: 2, size: 5 })

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/auctions/me/purchases?status=PENDING_PAYMENT&page=2&size=5')
  })

  it('throws on a non-2xx response', async () => {
    mockFetch.mockResolvedValue(json({ message: 'boom' }, 500))

    await expect(purchaseService.getMyPurchases()).rejects.toThrow('Failed to load purchases (500)')
  })
})
