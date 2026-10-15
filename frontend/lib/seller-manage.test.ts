import { describe, expect, it } from 'vitest'
import { AuctionStatus } from '@/lib/design-tokens'
import {
  canCancelAuction,
  canEditAuction,
  countBidders,
  draftChecklist,
  getManageMode,
  validateDraft,
  withDeltas,
  type DraftFields,
} from './seller-manage'
import type { BidEntry } from '@/types/ui/auction.ui'

function bid(id: string, bidderId: string, amount: number, minutesAgo: number): BidEntry {
  return {
    id, auctionId: 'a-1', bidderId, bidderName: bidderId, amount,
    placedAt: new Date(Date.parse('2026-10-15T10:00:00Z') - minutesAgo * 60_000),
    isAutoBid: false,
  }
}

describe('getManageMode', () => {
  it('maps pre-start states to the editor', () => {
    expect(getManageMode(AuctionStatus.Draft)).toBe('draft')
    expect(getManageMode(AuctionStatus.Scheduled)).toBe('draft')
  })

  it('maps every live timer state to live', () => {
    expect(getManageMode(AuctionStatus.Active)).toBe('live')
    expect(getManageMode(AuctionStatus.EndingSoon)).toBe('live')
    expect(getManageMode(AuctionStatus.Critical)).toBe('live')
  })

  it('is sold when closed with a winner, ended otherwise', () => {
    expect(getManageMode(AuctionStatus.Closed, 'u-1')).toBe('sold')
    expect(getManageMode(AuctionStatus.Closed)).toBe('ended')
  })
})

describe('edit and cancel gates', () => {
  it('allows editing only before the auction starts', () => {
    expect(canEditAuction(AuctionStatus.Draft)).toBe(true)
    expect(canEditAuction(AuctionStatus.Scheduled)).toBe(true)
    expect(canEditAuction(AuctionStatus.Active)).toBe(false)
    expect(canEditAuction(AuctionStatus.Closed)).toBe(false)
  })

  it('allows cancelling draft, scheduled and live auctions but not ended ones', () => {
    for (const s of [AuctionStatus.Draft, AuctionStatus.Scheduled, AuctionStatus.Active, AuctionStatus.EndingSoon, AuctionStatus.Critical]) {
      expect(canCancelAuction(s)).toBe(true)
    }
    expect(canCancelAuction(AuctionStatus.Closed)).toBe(false)
  })
})

describe('countBidders', () => {
  it('counts distinct bidders', () => {
    expect(countBidders([bid('1', 'a', 10, 3), bid('2', 'b', 20, 2), bid('3', 'a', 30, 1)])).toBe(2)
    expect(countBidders([])).toBe(0)
  })
})

describe('withDeltas', () => {
  it('shows each bid\'s rise over the next older loaded bid, newest first', () => {
    const rows = withDeltas([bid('3', 'a', 130, 1), bid('2', 'b', 120.5, 2), bid('1', 'a', 100, 3)])
    expect(rows.map((r) => r.delta)).toEqual([9.5, 20.5, null])
  })

  it('has no delta for a lone bid', () => {
    expect(withDeltas([bid('1', 'a', 100, 1)])[0].delta).toBeNull()
  })
})

describe('validateDraft', () => {
  const NOW = Date.parse('2026-10-15T10:00:00Z')
  const HOUR = 3_600_000
  const valid: DraftFields = {
    title: 'Nikon F3 with 50mm f/1.4',
    description: 'Working body, clean glass, seals replaced.',
    categoryId: 'c-1',
    imageCount: 1,
    startingPrice: 650,
    bidIncrement: 25,
    buyNowPrice: 0,
    depositAmount: 65,
    startTime: new Date(NOW + 24 * HOUR),
    endTime: new Date(NOW + 96 * HOUR),
  }

  it('accepts a complete draft', () => {
    expect(validateDraft(valid, NOW)).toEqual({})
  })

  it('checks the title length after trimming', () => {
    expect(validateDraft({ ...valid, title: '  abc ' }, NOW).title).toMatch(/at least 5/)
    expect(validateDraft({ ...valid, title: 'x'.repeat(256) }, NOW).title).toMatch(/255/)
  })

  it('needs a 20-character description, a category and a photo', () => {
    expect(validateDraft({ ...valid, description: 'too short' }, NOW).description).toMatch(/20/)
    expect(validateDraft({ ...valid, categoryId: '' }, NOW).categoryId).toBeDefined()
    expect(validateDraft({ ...valid, imageCount: 0 }, NOW).images).toBeDefined()
  })

  it('needs a positive starting price with at most two decimals', () => {
    expect(validateDraft({ ...valid, startingPrice: 0 }, NOW).startingPrice).toBeDefined()
    expect(validateDraft({ ...valid, startingPrice: 10.005, depositAmount: 1 }, NOW).startingPrice).toMatch(/2 decimal/)
  })

  it('needs a bid increment of at least one cent', () => {
    expect(validateDraft({ ...valid, bidIncrement: 0 }, NOW).bidIncrement).toBeDefined()
    expect(validateDraft({ ...valid, bidIncrement: 0.01 }, NOW).bidIncrement).toBeUndefined()
  })

  it('requires Buy It Now, when set, to exceed the starting price', () => {
    expect(validateDraft({ ...valid, buyNowPrice: 650 }, NOW).buyNowPrice).toMatch(/greater/)
    expect(validateDraft({ ...valid, buyNowPrice: 700 }, NOW).buyNowPrice).toBeUndefined()
    expect(validateDraft({ ...valid, buyNowPrice: 0 }, NOW).buyNowPrice).toBeUndefined()
  })

  it('keeps the deposit within 5-20% of the starting price, inclusive', () => {
    expect(validateDraft({ ...valid, depositAmount: 32.5 }, NOW).depositAmount).toBeUndefined()
    expect(validateDraft({ ...valid, depositAmount: 130 }, NOW).depositAmount).toBeUndefined()
    expect(validateDraft({ ...valid, depositAmount: 32.49 }, NOW).depositAmount).toBeDefined()
    expect(validateDraft({ ...valid, depositAmount: 130.01 }, NOW).depositAmount).toBeDefined()
  })

  it('keeps the duration between one hour and 30 days and the end in the future', () => {
    const start = new Date(NOW + HOUR)
    expect(validateDraft({ ...valid, startTime: start, endTime: new Date(start.getTime() + HOUR - 1) }, NOW).schedule).toMatch(/1 hour/)
    expect(validateDraft({ ...valid, startTime: start, endTime: new Date(start.getTime() + HOUR) }, NOW).schedule).toBeUndefined()
    expect(validateDraft({ ...valid, startTime: start, endTime: new Date(start.getTime() + 30 * 24 * HOUR) }, NOW).schedule).toBeUndefined()
    expect(validateDraft({ ...valid, startTime: start, endTime: new Date(start.getTime() + 30 * 24 * HOUR + 1) }, NOW).schedule).toMatch(/30 days/)
    expect(validateDraft({ ...valid, startTime: new Date(NOW - 5 * HOUR), endTime: new Date(NOW - HOUR) }, NOW).schedule).toMatch(/future/)
  })
})

describe('draftChecklist', () => {
  it('marks each item done unless its field has an error', () => {
    const items = draftChecklist({ title: 'bad', images: 'add one', depositAmount: 'range' })
    const done = Object.fromEntries(items.map((i) => [i.key, i.done]))
    expect(done).toEqual({
      title: false, description: true, category: true, images: false, pricing: false, schedule: true,
    })
  })
})
