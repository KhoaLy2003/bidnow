import { describe, expect, it } from 'vitest'
import { AuctionStatus } from '@/lib/design-tokens'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import {
  RING_SEGMENTS,
  countdownHandAngles,
  litSegments,
  pickHeroLot,
  ringTone,
  seedHeroLot,
  wallClockHandAngles,
} from './hero-watch'

const NOW = new Date('2026-10-16T12:00:00Z').getTime()

function item(overrides: Partial<AuctionBrowseItem> = {}): AuctionBrowseItem {
  return {
    id: 'a-1',
    title: 'Chronograph watch',
    primaryImageUrl: null,
    currentPrice: 1240,
    totalBids: 14,
    endTime: new Date(NOW + 3_600_000),
    status: AuctionStatus.Active,
    buyNowPrice: null,
    categoryName: 'Watches',
    ...overrides,
  }
}

describe('litSegments', () => {
  it('keeps the ring full until the final five minutes', () => {
    expect(litSegments(3 * 3600)).toBe(RING_SEGMENTS)
    expect(litSegments(300)).toBe(RING_SEGMENTS)
  })

  it('drains one segment per five seconds', () => {
    expect(litSegments(150)).toBe(30)
    expect(litSegments(60)).toBe(12)
  })

  it('keeps a segment lit until the very last second', () => {
    expect(litSegments(1)).toBe(1)
    expect(litSegments(0)).toBe(0)
    expect(litSegments(-5)).toBe(0)
  })

  it('treats an unknown time as a full ring', () => {
    expect(litSegments(Number.POSITIVE_INFINITY)).toBe(RING_SEGMENTS)
  })
})

describe('ringTone', () => {
  it('follows the countdown thresholds', () => {
    expect(ringTone(301)).toBe('calm')
    expect(ringTone(300)).toBe('warning')
    expect(ringTone(61)).toBe('warning')
    expect(ringTone(60)).toBe('critical')
    expect(ringTone(1)).toBe('critical')
    expect(ringTone(0)).toBe('closed')
  })

  it('is calm when there is no countdown to follow', () => {
    expect(ringTone(Number.POSITIVE_INFINITY)).toBe('calm')
  })
})

describe('countdownHandAngles', () => {
  it('rests every hand at 12 when time is up', () => {
    expect(countdownHandAngles(0)).toEqual({ second: 0, minute: 0, hour: 0 })
  })

  it('moves the second hand clockwise as the countdown runs down', () => {
    const early = countdownHandAngles(45).second
    const later = countdownHandAngles(30).second

    expect(later).toBeGreaterThan(early)
    expect(later).toBeCloseTo(Math.PI)
  })

  it('places the minute and hour hands from the time left', () => {
    const { minute, hour } = countdownHandAngles(15 * 60 + 30)

    expect(minute).toBeCloseTo(((60 - 15.5) / 60) * Math.PI * 2)
    expect(hour).toBeCloseTo(((12 - 15.5 / 60) / 12) * Math.PI * 2)
  })

  it('clamps negative time to zero', () => {
    expect(countdownHandAngles(-10)).toEqual(countdownHandAngles(0))
  })
})

describe('wallClockHandAngles', () => {
  it('points all hands straight up at 12:00:00', () => {
    const angles = wallClockHandAngles(new Date(2026, 9, 16, 12, 0, 0, 0))

    expect(angles.second).toBe(0)
    expect(angles.minute).toBe(0)
    expect(angles.hour).toBe(0)
  })

  it('puts the hour hand at 90 degrees at 3 o clock', () => {
    expect(wallClockHandAngles(new Date(2026, 9, 16, 15, 0, 0, 0)).hour).toBeCloseTo(Math.PI / 2)
  })

  it('moves the second hand smoothly with milliseconds', () => {
    expect(wallClockHandAngles(new Date(2026, 9, 16, 12, 0, 30, 0)).second).toBeCloseTo(Math.PI)
    expect(wallClockHandAngles(new Date(2026, 9, 16, 12, 0, 30, 500)).second).toBeGreaterThan(Math.PI)
  })
})

describe('seedHeroLot', () => {
  it('carries the browse snapshot into the live state', () => {
    const live = seedHeroLot(item())

    expect(live.auctionId).toBe('a-1')
    expect(live.currentBid).toBe(1240)
    expect(live.totalBids).toBe(14)
    expect(live.endsAt).toEqual(new Date(NOW + 3_600_000))
    expect(live.bids).toEqual([])
    expect(live.extensionCount).toBe(0)
    expect(live.isOutbid).toBe(false)
  })
})

describe('pickHeroLot', () => {
  it('picks the soonest-ending running auction', () => {
    const later = item({ id: 'later', endTime: new Date(NOW + 7_200_000) })
    const sooner = item({ id: 'sooner', endTime: new Date(NOW + 600_000) })

    expect(pickHeroLot([later, sooner], NOW)?.id).toBe('sooner')
  })

  it('skips closed and already-expired auctions', () => {
    const closed = item({ id: 'closed', status: AuctionStatus.Closed })
    const expired = item({ id: 'expired', endTime: new Date(NOW - 1000) })
    const live = item({ id: 'live' })

    expect(pickHeroLot([closed, expired, live], NOW)?.id).toBe('live')
  })

  it('returns null when nothing is running', () => {
    expect(pickHeroLot([], NOW)).toBeNull()
    expect(pickHeroLot([item({ status: AuctionStatus.Closed })], NOW)).toBeNull()
  })
})
