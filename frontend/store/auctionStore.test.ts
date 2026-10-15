import { beforeEach, describe, expect, it } from 'vitest'
import { AuctionStatus } from '@/lib/design-tokens'
import { baseAuction } from '@/lib/realtime/test-fixtures'
import { useAuctionStore } from './auctionStore'

const RESPONSE = { auctionId: 'a-1', price: 200, endedAt: '2026-10-01T12:00:00Z' }

describe('auctionStore.ownBuyNowCompleted', () => {
  beforeEach(() => useAuctionStore.getState().reset())

  it('closes the open auction for the caller', () => {
    useAuctionStore.getState().hydrate(baseAuction(), [])

    useAuctionStore.getState().ownBuyNowCompleted(RESPONSE, { id: 'me' })

    const live = useAuctionStore.getState().live
    expect(live?.status).toBe(AuctionStatus.Closed)
    expect(live?.winnerId).toBe('me')
    expect(live?.currentBid).toBe(200)
  })

  it('drops a late response addressed to an auction that is no longer open', () => {
    useAuctionStore.getState().hydrate(baseAuction({ id: 'a-2' }), [])

    useAuctionStore.getState().ownBuyNowCompleted(RESPONSE, { id: 'me' })

    expect(useAuctionStore.getState().live?.status).toBe(AuctionStatus.Active)
  })

  it('does nothing before the page has hydrated', () => {
    useAuctionStore.getState().ownBuyNowCompleted(RESPONSE, { id: 'me' })

    expect(useAuctionStore.getState().live).toBeNull()
  })
})
