import { describe, expect, it } from 'vitest'
import { SellerAuctionStatus } from '@/types/ui/seller.ui'
import type { SellerAuction } from '@/types/ui/seller.ui'
import {
  DEFAULT_SELLER_FILTERS,
  buildSellerGroups,
  canDeleteAuction,
  canEditAuction,
  filterBySearchAndCategory,
  formatEndedAgo,
  matchesStatusFilter,
  nextToClose,
  sellerGroupOf,
  sellerPrice,
  statusCounts,
  summarizeSellerAuctions,
  timeLeftShare,
} from './seller-auctions'

const NOW = new Date('2026-10-16T12:00:00Z').getTime()
const HOUR = 3600 * 1000
const DAY = 24 * HOUR

function auction(id: string, status: SellerAuctionStatus, over: Partial<SellerAuction> = {}): SellerAuction {
  return {
    id,
    title:         `Lot ${id}`,
    categoryId:    'watches',
    sellerId:      's1',
    startingPrice: 100,
    currentBid:    0,
    totalBids:     0,
    startsAt:      new Date(NOW - DAY),
    endsAt:        new Date(NOW + DAY),
    createdAt:     new Date(NOW - 2 * DAY),
    status,
    ...over,
  }
}

const S = SellerAuctionStatus

describe('sellerGroupOf', () => {
  it('puts running auctions in live, unstarted ones in upcoming, finished ones in ended', () => {
    expect(sellerGroupOf(S.Active)).toBe('live')
    expect(sellerGroupOf(S.Scheduled)).toBe('upcoming')
    expect(sellerGroupOf(S.Draft)).toBe('upcoming')
    expect([S.Completed, S.Failed, S.Cancelled, S.Rejected].map(sellerGroupOf)).toEqual(['ended', 'ended', 'ended', 'ended'])
  })
})

describe('matchesStatusFilter / statusCounts', () => {
  const all = [
    auction('1', S.Active), auction('2', S.Active), auction('3', S.Scheduled), auction('4', S.Draft),
    auction('5', S.Completed), auction('6', S.Failed), auction('7', S.Cancelled), auction('8', S.Rejected),
  ]

  it('treats failed, cancelled and rejected as unsold, completed as sold', () => {
    expect(all.filter((a) => matchesStatusFilter(a, 'sold')).map((a) => a.id)).toEqual(['5'])
    expect(all.filter((a) => matchesStatusFilter(a, 'unsold')).map((a) => a.id)).toEqual(['6', '7', '8'])
  })

  it('counts every chip and the counts add up to the total', () => {
    const c = statusCounts(all)
    expect(c).toEqual({ all: 8, live: 2, scheduled: 1, draft: 1, sold: 1, unsold: 3 })
    expect(c.live + c.scheduled + c.draft + c.sold + c.unsold).toBe(c.all)
  })
})

describe('filterBySearchAndCategory', () => {
  const items = [
    auction('a', S.Active, { title: 'Leica M6 TTL', categoryId: 'cameras' }),
    auction('b', S.Active, { title: 'Omega Seamaster', categoryId: 'watches' }),
    auction('c', S.Completed, { title: 'Leica D-Lux', categoryId: 'cameras' }),
  ]

  it('matches the title case-insensitively and ignores surrounding spaces', () => {
    expect(filterBySearchAndCategory(items, '  LEICA ', '').map((a) => a.id)).toEqual(['a', 'c'])
  })

  it('narrows by category and combines with the search', () => {
    expect(filterBySearchAndCategory(items, '', 'watches').map((a) => a.id)).toEqual(['b'])
    expect(filterBySearchAndCategory(items, 'leica', 'watches')).toEqual([])
  })

  it('returns everything for an empty search and category', () => {
    expect(filterBySearchAndCategory(items, '', '')).toHaveLength(3)
  })
})

describe('buildSellerGroups', () => {
  const live1 = auction('live-late', S.Active, { endsAt: new Date(NOW + 2 * DAY), currentBid: 900, totalBids: 3 })
  const live2 = auction('live-soon', S.Active, { endsAt: new Date(NOW + HOUR), currentBid: 500, totalBids: 9 })
  const sched = auction('sched', S.Scheduled, { startsAt: new Date(NOW + DAY), endsAt: new Date(NOW + 3 * DAY) })
  const schedFirst = auction('sched-first', S.Scheduled, { startsAt: new Date(NOW + HOUR), endsAt: new Date(NOW + 2 * DAY) })
  const draft = auction('draft', S.Draft)
  const sold = auction('sold', S.Completed, { endsAt: new Date(NOW - DAY), currentBid: 1200, totalBids: 4 })
  const soldOlder = auction('sold-older', S.Completed, { endsAt: new Date(NOW - 5 * DAY), currentBid: 300, totalBids: 20 })
  const all = [soldOlder, draft, live1, sched, sold, live2, schedFirst]

  it('splits one list into live, upcoming and ended in that order', () => {
    expect(buildSellerGroups(all, DEFAULT_SELLER_FILTERS).map((g) => g.key)).toEqual(['live', 'upcoming', 'ended'])
  })

  it('orders each band by time by default', () => {
    const [liveGroup, upcoming, ended] = buildSellerGroups(all, DEFAULT_SELLER_FILTERS)
    expect(liveGroup.auctions.map((a) => a.id)).toEqual(['live-soon', 'live-late'])
    expect(upcoming.auctions.map((a) => a.id)).toEqual(['sched-first', 'sched', 'draft'])
    expect(ended.auctions.map((a) => a.id)).toEqual(['sold', 'sold-older'])
  })

  it('sorts by price within each band and labels the order', () => {
    const [liveGroup, , ended] = buildSellerGroups(all, { ...DEFAULT_SELLER_FILTERS, sort: 'price' })
    expect(liveGroup.auctions.map((a) => a.id)).toEqual(['live-late', 'live-soon'])
    expect(liveGroup.hint).toBe('Highest price first')
    expect(ended.auctions.map((a) => a.id)).toEqual(['sold', 'sold-older'])
  })

  it('sorts by bid count within each band', () => {
    const groups = buildSellerGroups(all, { ...DEFAULT_SELLER_FILTERS, sort: 'bids' })
    expect(groups[0].auctions.map((a) => a.id)).toEqual(['live-soon', 'live-late'])
    expect(groups[2].auctions.map((a) => a.id)).toEqual(['sold-older', 'sold'])
    expect(groups[2].hint).toBe('Most bids first')
  })

  it('drops bands with nothing in them when a chip is chosen', () => {
    const groups = buildSellerGroups(all, { ...DEFAULT_SELLER_FILTERS, status: 'draft' })
    expect(groups.map((g) => g.key)).toEqual(['upcoming'])
    expect(groups[0].auctions.map((a) => a.id)).toEqual(['draft'])
  })

  it('applies search and category before grouping', () => {
    const cameras = [
      auction('c1', S.Active, { categoryId: 'cameras', title: 'Leica' }),
      auction('c2', S.Completed, { categoryId: 'cameras', title: 'Hasselblad' }),
      auction('w1', S.Active, { categoryId: 'watches', title: 'Omega' }),
    ]
    const groups = buildSellerGroups(cameras, { ...DEFAULT_SELLER_FILTERS, categoryId: 'cameras' })
    expect(groups.map((g) => g.auctions.map((a) => a.id))).toEqual([['c1'], ['c2']])
    expect(buildSellerGroups(cameras, { ...DEFAULT_SELLER_FILTERS, query: 'zzz' })).toEqual([])
  })

  it('does not mutate the input', () => {
    const copy = [...all]
    buildSellerGroups(all, DEFAULT_SELLER_FILTERS)
    expect(all).toEqual(copy)
  })

  it('gives each band its own column captions', () => {
    const [liveGroup, upcoming, ended] = buildSellerGroups(all, DEFAULT_SELLER_FILTERS)
    expect(liveGroup.columns.time).toBe('Time left')
    expect(upcoming.columns.time).toBe('Starts in')
    expect(ended.columns).toEqual({ price: 'Final price', time: 'Ended', status: 'Result' })
  })
})

describe('sellerPrice', () => {
  it('uses the leading bid once there is a bid, the starting bid before that', () => {
    expect(sellerPrice(auction('1', S.Active, { startingPrice: 800 }))).toBe(800)
    expect(sellerPrice(auction('2', S.Active, { startingPrice: 800, currentBid: 950, totalBids: 2 }))).toBe(950)
  })
})

describe('nextToClose', () => {
  it('returns the live auction ending first, ignoring everything else', () => {
    const items = [
      auction('later', S.Active, { endsAt: new Date(NOW + DAY) }),
      auction('soon', S.Active, { endsAt: new Date(NOW + HOUR) }),
      auction('sched', S.Scheduled, { endsAt: new Date(NOW + 60_000) }),
    ]
    expect(nextToClose(items)?.id).toBe('soon')
    expect(nextToClose([auction('x', S.Draft)])).toBeNull()
  })
})

describe('summarizeSellerAuctions', () => {
  it('lists non-zero parts only', () => {
    const items = [auction('1', S.Active), auction('2', S.Draft), auction('3', S.Completed), auction('4', S.Cancelled)]
    expect(summarizeSellerAuctions(items)).toBe('4 auctions · 1 live · 1 draft · 2 ended')
  })

  it('handles one auction and none', () => {
    expect(summarizeSellerAuctions([auction('1', S.Active)])).toBe('1 auction · 1 live')
    expect(summarizeSellerAuctions([])).toBe('0 auctions')
  })
})

describe('timeLeftShare', () => {
  it('is the share of the run still left, clamped to 2..100', () => {
    const a = auction('1', S.Active, { startsAt: new Date(NOW - HOUR), endsAt: new Date(NOW + HOUR) })
    expect(timeLeftShare(a, NOW)).toBe(50)
    expect(timeLeftShare(a, NOW - 10 * HOUR)).toBe(100)
    expect(timeLeftShare(a, NOW + HOUR - 1000)).toBe(2)
    expect(timeLeftShare(a, NOW + 2 * HOUR)).toBe(0)
  })

  it('stays full for an auction whose dates are unusable', () => {
    const bad = auction('1', S.Active, { startsAt: new Date(NOW + HOUR), endsAt: new Date(NOW + HOUR) })
    expect(timeLeftShare(bad, NOW)).toBe(100)
  })
})

describe('formatEndedAgo', () => {
  it('counts whole days since the auction ended', () => {
    expect(formatEndedAgo(new Date(NOW - HOUR), NOW)).toBe('today')
    expect(formatEndedAgo(new Date(NOW - DAY), NOW)).toBe('1 day ago')
    expect(formatEndedAgo(new Date(NOW - 4 * DAY - HOUR), NOW)).toBe('4 days ago')
  })
})

describe('row permissions', () => {
  it('lets drafts and scheduled auctions be edited, and live ones be deleted but not edited', () => {
    expect(canEditAuction(auction('1', S.Draft))).toBe(true)
    expect(canEditAuction(auction('2', S.Scheduled))).toBe(true)
    expect(canEditAuction(auction('3', S.Active))).toBe(false)
    expect(canDeleteAuction(auction('3', S.Active))).toBe(true)
    expect(canDeleteAuction(auction('4', S.Completed))).toBe(false)
  })
})
