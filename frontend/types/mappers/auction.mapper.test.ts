import { describe, expect, it } from 'vitest'
import { mapAuctionDetailResponse } from './auction.mapper'
import type { AuctionDetailResponse } from '@/types/api/auction.api'

function detailDto(overrides: Partial<AuctionDetailResponse> = {}): AuctionDetailResponse {
  return {
    id: 'a-1',
    title: "Seiko 6139 'Pogue' chronograph",
    description: 'Chronograph',
    category: { id: 'c-1', name: 'Watches', slug: 'watches' },
    startingPrice: 1000,
    bidIncrement: 50,
    buyNowPrice: null,
    depositAmount: 100,
    currentPrice: 1150,
    totalBids: 27,
    status: 'COMPLETED',
    startTime: '2026-10-11T11:37:00Z',
    endTime: '2026-10-14T11:42:00Z',
    originalEndTime: '2026-10-14T11:37:00Z',
    extensionCount: 1,
    completedAt: '2026-10-14T11:42:00Z',
    winnerId: 'u-9',
    images: [],
    seller: null,
    createdAt: '2026-10-10T12:30:00Z',
    ...overrides,
  }
}

describe('mapAuctionDetailResponse payment fields', () => {
  it('maps the payment deadline and paid timestamp to dates', () => {
    const detail = mapAuctionDetailResponse(detailDto({
      paymentDeadline: '2026-10-16T11:42:00Z',
      winnerPaidAt: '2026-10-15T02:00:00Z',
    }))

    expect(detail.paymentDeadline).toEqual(new Date('2026-10-16T11:42:00Z'))
    expect(detail.winnerPaidAt).toEqual(new Date('2026-10-15T02:00:00Z'))
  })

  it('leaves them undefined when the server sends null or omits them', () => {
    const nulls = mapAuctionDetailResponse(detailDto({ paymentDeadline: null, winnerPaidAt: null }))
    const omitted = mapAuctionDetailResponse(detailDto())

    expect(nulls.paymentDeadline).toBeUndefined()
    expect(nulls.winnerPaidAt).toBeUndefined()
    expect(omitted.paymentDeadline).toBeUndefined()
    expect(omitted.winnerPaidAt).toBeUndefined()
  })
})
