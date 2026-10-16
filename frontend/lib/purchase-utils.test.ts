import { describe, expect, it } from 'vitest'
import {
  applyPaidIds,
  buildPurchasesHref,
  filterToStatus,
  hoursUntil,
  parsePurchaseFilter,
  parsePurchasePage,
  purchaseNote,
  statusPresentation,
} from './purchase-utils'
import type { Purchase } from '@/types/ui/purchase.ui'

const NOW = new Date('2026-10-16T12:00:00Z').getTime()

function purchase(overrides: Partial<Purchase> = {}): Purchase {
  return { auctionId: 'a-1', title: 'Leica M3', finalPrice: 1420, status: 'PENDING_PAYMENT', ...overrides }
}

describe('parsePurchaseFilter', () => {
  it('accepts the known filters case-insensitively', () => {
    expect(parsePurchaseFilter('pending')).toBe('pending')
    expect(parsePurchaseFilter('PAID')).toBe('paid')
    expect(parsePurchaseFilter('forfeited')).toBe('forfeited')
  })

  it('falls back to all for missing or unknown values', () => {
    expect(parsePurchaseFilter(null)).toBe('all')
    expect(parsePurchaseFilter(undefined)).toBe('all')
    expect(parsePurchaseFilter('bogus')).toBe('all')
  })
})

describe('filterToStatus', () => {
  it('maps each filter to the API status, all to none', () => {
    expect(filterToStatus('all')).toBeUndefined()
    expect(filterToStatus('pending')).toBe('PENDING_PAYMENT')
    expect(filterToStatus('paid')).toBe('PAID')
    expect(filterToStatus('forfeited')).toBe('FORFEITED')
  })
})

describe('parsePurchasePage', () => {
  it('turns the 1-based URL page into a zero-based index', () => {
    expect(parsePurchasePage('1')).toBe(0)
    expect(parsePurchasePage('3')).toBe(2)
  })

  it('treats missing, zero, negative and garbage as the first page', () => {
    expect(parsePurchasePage(null)).toBe(0)
    expect(parsePurchasePage('0')).toBe(0)
    expect(parsePurchasePage('-2')).toBe(0)
    expect(parsePurchasePage('abc')).toBe(0)
  })
})

describe('buildPurchasesHref', () => {
  it('omits default parts', () => {
    expect(buildPurchasesHref('all', 0)).toBe('/purchases')
  })

  it('includes the filter and a 1-based page when set', () => {
    expect(buildPurchasesHref('paid', 0)).toBe('/purchases?status=paid')
    expect(buildPurchasesHref('all', 1)).toBe('/purchases?page=2')
    expect(buildPurchasesHref('forfeited', 2)).toBe('/purchases?status=forfeited&page=3')
  })
})

describe('statusPresentation', () => {
  it('labels and styles each status', () => {
    expect(statusPresentation('PENDING_PAYMENT')).toEqual({ label: 'Pending payment', variant: 'ending-soon' })
    expect(statusPresentation('PAID')).toEqual({ label: 'Paid', variant: 'won' })
    expect(statusPresentation('FORFEITED')).toEqual({ label: 'Forfeited', variant: 'critical' })
  })
})

describe('hoursUntil', () => {
  it('floors to whole hours and never goes negative', () => {
    expect(hoursUntil(new Date(NOW + 11.9 * 3_600_000), NOW)).toBe(11)
    expect(hoursUntil(new Date(NOW - 1000), NOW)).toBe(0)
  })

  it('is null without a deadline', () => {
    expect(hoursUntil(undefined, NOW)).toBeNull()
  })
})

describe('purchaseNote', () => {
  it('counts down a pending payment', () => {
    const p = purchase({ paymentDeadline: new Date(NOW + 11.5 * 3_600_000) })
    expect(purchaseNote(p, NOW)).toBe('11 hours left')
  })

  it('says the deadline passed when a pending deadline is in the past', () => {
    expect(purchaseNote(purchase({ paymentDeadline: new Date(NOW - 1000) }), NOW)).toBe('Deadline passed')
  })

  it('has a neutral note while the wallet has not opened the payment', () => {
    expect(purchaseNote(purchase(), NOW)).toBe('Payment details are on the way')
  })

  it('shows when a paid purchase was paid', () => {
    const p = purchase({ status: 'PAID', paidAt: new Date('2026-10-12T10:00:00Z') })
    expect(purchaseNote(p, NOW)).toMatch(/^Paid Oct 1[12], 2026$/)
  })

  it('shows when a forfeited purchase missed its deadline', () => {
    const p = purchase({ status: 'FORFEITED', paymentDeadline: new Date('2026-09-21T12:00:00Z') })
    expect(purchaseNote(p, NOW)).toMatch(/^Deadline passed Sep 2[01], 2026$/)
  })
})

describe('applyPaidIds', () => {
  it('flips only pending rows that were paid this session', () => {
    const items = [
      purchase({ auctionId: 'a-1' }),
      purchase({ auctionId: 'a-2' }),
      purchase({ auctionId: 'a-3', status: 'FORFEITED' }),
    ]

    const result = applyPaidIds(items, new Set(['a-1', 'a-3']))

    expect(result.map((p) => p.status)).toEqual(['PAID', 'PENDING_PAYMENT', 'FORFEITED'])
  })
})
