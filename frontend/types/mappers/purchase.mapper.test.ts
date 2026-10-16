import { describe, expect, it } from 'vitest'
import { mapPurchasePage, mapPurchaseResponse } from './purchase.mapper'
import type { PurchaseResponse } from '@/types/api/purchase.api'

function dto(overrides: Partial<PurchaseResponse> = {}): PurchaseResponse {
  return {
    auctionId: 'a-1',
    title: 'Leica M3',
    primaryImageUrl: null,
    finalPrice: 1420,
    paymentStatus: 'PAID',
    completedAt: '2026-10-14T08:00:00Z',
    paymentDeadline: '2026-10-16T08:00:00Z',
    paidAt: '2026-10-14T13:00:00Z',
    ...overrides,
  }
}

describe('mapPurchaseResponse', () => {
  it('maps fields and parses timestamps', () => {
    const p = mapPurchaseResponse(dto())

    expect(p).toMatchObject({ auctionId: 'a-1', title: 'Leica M3', finalPrice: 1420, status: 'PAID' })
    expect(p?.completedAt).toEqual(new Date('2026-10-14T08:00:00Z'))
    expect(p?.paidAt).toEqual(new Date('2026-10-14T13:00:00Z'))
  })

  it('leaves absent or invalid timestamps undefined', () => {
    const p = mapPurchaseResponse(dto({ paidAt: null, paymentDeadline: 'not-a-date' }))

    expect(p?.paidAt).toBeUndefined()
    expect(p?.paymentDeadline).toBeUndefined()
  })

  it('drops a row without a payment status', () => {
    expect(mapPurchaseResponse(dto({ paymentStatus: null }))).toBeNull()
  })
})

describe('mapPurchasePage', () => {
  it('maps items and pagination, skipping non-purchases', () => {
    const page = mapPurchasePage({
      data: [dto(), dto({ auctionId: 'a-2', paymentStatus: null })],
      pagination: { page: 1, limit: 10, total: 12, totalPages: 2, hasNext: false, hasPrev: true },
    })

    expect(page.items.map((p) => p.auctionId)).toEqual(['a-1'])
    expect(page).toMatchObject({ page: 1, total: 12, totalPages: 2, hasNext: false, hasPrev: true })
  })
})
