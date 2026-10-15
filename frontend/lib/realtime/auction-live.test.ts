import { describe, expect, it } from 'vitest'
import { AuctionStatus } from '@/lib/design-tokens'
import {
  applyAuctionEnded, applyOwnBuyNow, deriveDisplayAuction, seedLiveState,
} from './auction-live'
import { baseAuction } from './test-fixtures'

const ME = { id: 'me' }
const RESPONSE = { auctionId: 'a-1', price: 200, endedAt: '2026-10-01T12:00:00Z' }

function seeded() {
  return seedLiveState(baseAuction(), [])
}

describe('applyOwnBuyNow', () => {
  it('closes the auction with the caller as winner at the buy-now price', () => {
    const next = applyOwnBuyNow(seeded(), RESPONSE, ME)

    expect(next.status).toBe(AuctionStatus.Closed)
    expect(next.winnerId).toBe('me')
    expect(next.currentWinnerId).toBe('me')
    expect(next.currentBid).toBe(200)
    expect(next.isOutbid).toBe(false)
  })

  it('clears an outbid flag', () => {
    const outbid = { ...seeded(), isOutbid: true }

    expect(applyOwnBuyNow(outbid, RESPONSE, ME).isOutbid).toBe(false)
  })

  it('never lowers a price that is already higher', () => {
    const high = { ...seeded(), currentBid: 250 }

    expect(applyOwnBuyNow(high, RESPONSE, ME).currentBid).toBe(250)
  })

  it('ignores a response for another auction', () => {
    const state = seeded()

    expect(applyOwnBuyNow(state, { ...RESPONSE, auctionId: 'other' }, ME)).toBe(state)
  })

  it('is idempotent with the realtime AUCTION_ENDED that follows', () => {
    const local = applyOwnBuyNow(seeded(), RESPONSE, ME)
    const replayed = applyAuctionEnded(local, {
      winnerId: 'me', finalPrice: 200, endedAt: '2026-10-01T12:00:00Z',
    })

    expect(replayed).toEqual(local)
  })

  it('lets the buyer see the Won status', () => {
    const live = applyOwnBuyNow(seeded(), RESPONSE, ME)

    const { auction } = deriveDisplayAuction(baseAuction(), live, 'me')

    expect(auction.status).toBe(AuctionStatus.Won)
    expect(auction.currentBid).toBe(200)
  })

  it('shows every other viewer the Closed status', () => {
    const live = applyOwnBuyNow(seeded(), RESPONSE, ME)

    const { auction } = deriveDisplayAuction(baseAuction(), live, 'someone-else')

    expect(auction.status).toBe(AuctionStatus.Closed)
  })
})
