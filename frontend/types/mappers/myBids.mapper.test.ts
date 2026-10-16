import { describe, expect, it } from 'vitest'
import { mapMyActiveBids } from './myBids.mapper'
import type { MyActiveBidsResponse } from '@/types/api/bid.api'

const dto: MyActiveBidsResponse = {
  items: [
    {
      auctionId: 'a-1', title: 'Leica M3', primaryImageUrl: 'https://cdn/leica.jpg',
      currentPrice: 2850, myHighestBid: 2850, totalBids: 9,
      endTime: '2026-10-17T10:00:00Z', lastBidAt: '2026-10-16T09:00:00Z', isWinning: true,
    },
    {
      auctionId: 'a-2', title: 'Omega', primaryImageUrl: null,
      currentPrice: 4350, myHighestBid: 4200, totalBids: 14,
      endTime: '2026-10-18T10:00:00Z', lastBidAt: '2026-10-16T08:00:00Z', isWinning: false,
    },
  ],
  counts: { all: 2, winning: 1, outbid: 1 },
}

describe('mapMyActiveBids', () => {
  it('converts the timestamps to Dates and keeps the amounts in dollars', () => {
    const [first] = mapMyActiveBids(dto)

    expect(first.endTime).toEqual(new Date('2026-10-17T10:00:00Z'))
    expect(first.lastBidAt).toEqual(new Date('2026-10-16T09:00:00Z'))
    expect(first.currentPrice).toBe(2850)
    expect(first.myHighestBid).toBe(2850)
  })

  it('keeps the order, the image (or null) and the winning flag', () => {
    const items = mapMyActiveBids(dto)

    expect(items.map((i) => i.auctionId)).toEqual(['a-1', 'a-2'])
    expect(items.map((i) => i.primaryImageUrl)).toEqual(['https://cdn/leica.jpg', null])
    expect(items.map((i) => i.isWinning)).toEqual([true, false])
  })

  it('maps an empty response to an empty list', () => {
    expect(mapMyActiveBids({ items: [], counts: { all: 0, winning: 0, outbid: 0 } })).toEqual([])
  })
})
