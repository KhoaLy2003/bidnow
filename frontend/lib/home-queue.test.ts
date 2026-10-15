import { describe, expect, it } from 'vitest'
import { AuctionStatus } from '@/lib/design-tokens'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { closingNext, formatQueueClock, queueState, runningLots } from './home-queue'

const NOW = new Date('2026-10-16T12:00:00Z').getTime()

function item(id: string, secondsLeft: number, status = AuctionStatus.Active): AuctionBrowseItem {
  return {
    id,
    title: id,
    primaryImageUrl: null,
    currentPrice: 100,
    totalBids: 1,
    endTime: new Date(NOW + secondsLeft * 1000),
    status,
    buyNowPrice: null,
    categoryName: 'Watches',
  }
}

describe('queueState', () => {
  it('labels each stage of the countdown in words', () => {
    expect(queueState(3600).label).toBe('Live')
    expect(queueState(300).label).toBe('Ending soon')
    expect(queueState(61).label).toBe('Ending soon')
    expect(queueState(60).label).toBe('Final minute')
    expect(queueState(0).label).toBe('Closed')
  })

  it('maps stages to tones', () => {
    expect([301, 300, 60, 0].map((s) => queueState(s).tone)).toEqual(['live', 'warning', 'critical', 'closed'])
  })

  it('sizes the bar by time left within the hour', () => {
    expect(queueState(1800).progress).toBe(50)
    expect(queueState(7200).progress).toBe(100)
    expect(queueState(0).progress).toBe(0)
  })

  it('never lets a running auction draw an invisible bar', () => {
    expect(queueState(1).progress).toBe(2)
  })
})

describe('formatQueueClock', () => {
  it('shows hours, minutes and seconds under a day', () => {
    expect(formatQueueClock(3600 + 12 * 60 + 7)).toBe('01:12:07')
    expect(formatQueueClock(59)).toBe('00:00:59')
  })

  it('switches to days and hours beyond a day', () => {
    expect(formatQueueClock(2 * 86_400 + 4 * 3600 + 30)).toBe('2d 04h')
  })

  it('clamps negative time to zero', () => {
    expect(formatQueueClock(-4)).toBe('00:00:00')
  })
})

describe('runningLots / closingNext', () => {
  it('sorts running auctions by closing time and drops finished ones', () => {
    const lots = [item('c', 900), item('closed', 100, AuctionStatus.Closed), item('a', 60), item('gone', -5), item('b', 300)]

    expect(runningLots(lots, NOW).map((a) => a.id)).toEqual(['a', 'b', 'c'])
  })

  it('does not mutate the list it is given', () => {
    const lots = [item('b', 300), item('a', 60)]
    runningLots(lots, NOW)

    expect(lots.map((a) => a.id)).toEqual(['b', 'a'])
  })

  it('merges overlapping lists without repeating an auction and honours the limit', () => {
    const lots = [item('a', 60), item('b', 300), item('a', 60), item('c', 900)]

    expect(closingNext(lots, NOW, 2).map((a) => a.id)).toEqual(['a', 'b'])
  })
})
