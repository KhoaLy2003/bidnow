import { describe, expect, it } from 'vitest'
import { countByTab, filterByTab, isBidStatusPush, MY_BIDS_POLL_MS } from './my-bids'
import type { MyActiveBid } from '@/types/ui/bid.ui'

function bid(auctionId: string, isWinning: boolean): MyActiveBid {
  return {
    auctionId, title: auctionId, primaryImageUrl: null, currentPrice: 100, myHighestBid: 100,
    totalBids: 1, endTime: new Date('2026-10-17T10:00:00Z'), lastBidAt: new Date('2026-10-16T09:00:00Z'), isWinning,
  }
}

const bids = [bid('a', true), bid('b', false), bid('c', true), bid('d', false), bid('e', true)]

describe('filterByTab', () => {
  it('returns everything, in order, for "all"', () => {
    expect(filterByTab(bids, 'all').map((b) => b.auctionId)).toEqual(['a', 'b', 'c', 'd', 'e'])
  })

  it('returns only the winning bids for "winning"', () => {
    expect(filterByTab(bids, 'winning').map((b) => b.auctionId)).toEqual(['a', 'c', 'e'])
  })

  it('returns only the outbid bids for "outbid"', () => {
    expect(filterByTab(bids, 'outbid').map((b) => b.auctionId)).toEqual(['b', 'd'])
  })
})

describe('countByTab', () => {
  it('counts every tab', () => {
    expect(countByTab(bids)).toEqual({ all: 5, winning: 3, outbid: 2 })
  })

  it('is all zero for no bids', () => {
    expect(countByTab([])).toEqual({ all: 0, winning: 0, outbid: 0 })
  })
})

describe('isBidStatusPush', () => {
  it.each(['outbid', 'won', 'lost'] as const)('refreshes the list on a %s notification', (type) => {
    expect(isBidStatusPush(type)).toBe(true)
  })

  it.each(['bid_placed', 'ending_soon', 'payment', 'system'] as const)('ignores a %s notification', (type) => {
    expect(isBidStatusPush(type)).toBe(false)
  })
})

describe('MY_BIDS_POLL_MS', () => {
  it('polls every 30 seconds', () => {
    expect(MY_BIDS_POLL_MS).toBe(30_000)
  })
})
