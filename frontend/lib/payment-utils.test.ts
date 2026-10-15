import { describe, expect, it } from 'vitest'
import {
  RECENT_WIN_MS,
  derivePaymentPanelState,
  formatHoursLeft,
  getShortfall,
  isPaymentPush,
  isRecentWin,
  toAuctionPreview,
  type PaymentPanelInput,
} from './payment-utils'
import type { AuctionImage } from '@/types/ui/auction.ui'
import type { PendingPayment } from '@/types/ui/wallet.ui'

function hold(over: Partial<PendingPayment> = {}): PendingPayment {
  return {
    auctionId: 'a-1', totalAmount: 500, depositApplied: 100, remaining: 400,
    fundsHeld: false, hoursLeft: 47, expired: false, ...over,
  }
}

describe('isPaymentPush', () => {
  it('matches the payment notification types that change what is owed', () => {
    expect(isPaymentPush('PAYMENT_REQUIRED')).toBe(true)
    expect(isPaymentPush('PAYMENT_RECEIVED')).toBe(true)
    expect(isPaymentPush('PAYMENT_FAILED')).toBe(true)
  })

  it('ignores everything else, including reminders', () => {
    expect(isPaymentPush('PAYMENT_REMINDER')).toBe(false)
    expect(isPaymentPush('BID_OUTBID')).toBe(false)
  })
})

describe('getShortfall', () => {
  it('is null when the funds are already held', () => {
    expect(getShortfall(hold({ fundsHeld: true }), 0)).toBeNull()
  })

  it('is null when the balance covers the remainder, including exactly', () => {
    expect(getShortfall(hold(), 400)).toBeNull()
    expect(getShortfall(hold(), 1000)).toBeNull()
  })

  it('returns the missing amount', () => {
    expect(getShortfall(hold(), 150.5)).toBe(249.5)
  })

  it('detects a balance that is one cent short', () => {
    expect(getShortfall(hold(), 399.99)).toBe(0.01)
  })

  it('is not fooled by float drift', () => {
    expect(getShortfall(hold({ remaining: 0.3 }), 0.1 + 0.2)).toBeNull()
    expect(getShortfall(hold({ remaining: 0.3 }), 0.1)).toBe(0.2)
  })

  it('is null when nothing remains to pay', () => {
    expect(getShortfall(hold({ remaining: 0 }), 0)).toBeNull()
  })
})

describe('formatHoursLeft', () => {
  it('words zero, one and many hours', () => {
    expect(formatHoursLeft(0)).toBe('Less than 1 hour left')
    expect(formatHoursLeft(1)).toBe('1 hour left')
    expect(formatHoursLeft(47)).toBe('47 hours left')
  })

  it('treats a negative value as under an hour', () => {
    expect(formatHoursLeft(-3)).toBe('Less than 1 hour left')
  })
})

describe('isRecentWin', () => {
  const now = Date.parse('2026-10-15T12:00:00Z')

  it('treats an unknown completion time as recent (live state has no completedAt yet)', () => {
    expect(isRecentWin(undefined, now)).toBe(true)
  })

  it('is true within the window and false after it', () => {
    expect(isRecentWin(new Date(now - 60_000), now)).toBe(true)
    expect(isRecentWin(new Date(now - RECENT_WIN_MS - 1), now)).toBe(false)
  })

  it('is false for an auction won days ago', () => {
    expect(isRecentWin(new Date('2026-10-12T12:00:00Z'), now)).toBe(false)
  })

  it('is true when the client clock is behind the server', () => {
    expect(isRecentWin(new Date(now + 5_000), now)).toBe(true)
  })
})

describe('derivePaymentPanelState', () => {
  const base: PaymentPanelInput = {
    holds: [], auctionId: 'a-1', paidIds: new Set<string>(),
    hasFetched: true, loadFailed: false, pollWindowOpen: false,
  }

  it('is loading before the first fetch', () => {
    expect(derivePaymentPanelState({ ...base, hasFetched: false })).toEqual({ state: 'loading', hold: null })
  })

  it('is error when the first fetch failed', () => {
    expect(derivePaymentPanelState({ ...base, hasFetched: false, loadFailed: true }).state).toBe('error')
  })

  it('does not show error once a fetch has succeeded', () => {
    expect(derivePaymentPanelState({ ...base, loadFailed: true }).state).toBe('none')
  })

  it('is due when there is an unexpired hold for the auction', () => {
    const h = hold()
    expect(derivePaymentPanelState({ ...base, holds: [h] })).toEqual({ state: 'due', hold: h })
  })

  it('is expired when the hold is past its deadline', () => {
    const h = hold({ expired: true })
    expect(derivePaymentPanelState({ ...base, holds: [h] })).toEqual({ state: 'expired', hold: h })
  })

  it('ignores holds for other auctions', () => {
    expect(derivePaymentPanelState({ ...base, holds: [hold({ auctionId: 'other' })] }).state).toBe('none')
  })

  it('is preparing while the window is open and no hold exists yet', () => {
    expect(derivePaymentPanelState({ ...base, pollWindowOpen: true }).state).toBe('preparing')
  })

  it('is none once the window has closed with no hold', () => {
    expect(derivePaymentPanelState(base).state).toBe('none')
  })

  it('is paid when the auction was paid this session, even if a stale hold is present', () => {
    expect(derivePaymentPanelState({ ...base, paidIds: new Set(['a-1']), holds: [hold()] }))
      .toEqual({ state: 'paid', hold: null })
  })
})

describe('toAuctionPreview', () => {
  const img = (over: Partial<AuctionImage>): AuctionImage => ({
    id: 'i', imageUrl: 'https://cdn.test/full.jpg', displayOrder: 0, isPrimary: false, ...over,
  })

  it('prefers the primary image and its thumbnail', () => {
    const preview = toAuctionPreview({
      title: 'Watch',
      images: [
        img({ id: 'a', imageUrl: 'https://cdn.test/a.jpg' }),
        img({ id: 'b', isPrimary: true, imageUrl: 'https://cdn.test/b.jpg', thumbnailUrl: 'https://cdn.test/b-thumb.jpg' }),
      ],
    })
    expect(preview).toEqual({ title: 'Watch', imageUrl: 'https://cdn.test/b-thumb.jpg' })
  })

  it('falls back to the first image by display order', () => {
    const preview = toAuctionPreview({
      title: 'Watch',
      images: [
        img({ id: 'a', displayOrder: 2, imageUrl: 'https://cdn.test/a.jpg' }),
        img({ id: 'b', displayOrder: 1, imageUrl: 'https://cdn.test/b.jpg' }),
      ],
    })
    expect(preview.imageUrl).toBe('https://cdn.test/b.jpg')
  })

  it('has no image when the auction has none', () => {
    expect(toAuctionPreview({ title: 'Watch', images: [] })).toEqual({ title: 'Watch', imageUrl: undefined })
  })
})
