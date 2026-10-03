import { describe, expect, it } from 'vitest'
import {
  NOTIFICATION_TYPE_FILTERS, parseNotificationFilters, toListParams, toNotificationSearchParams,
} from './notification-filters'
import { NOTIFICATION_SERVER_TYPES } from '@/types/api/notification.api'

describe('notification filters', () => {
  it('parses defaults from empty params', () => {
    expect(parseNotificationFilters(new URLSearchParams())).toEqual({ tab: 'all', type: null, q: '', page: 1 })
  })

  it('parses every param and ignores invalid values', () => {
    expect(parseNotificationFilters(new URLSearchParams('tab=unread&type=BID_OUTBID&q=watch&page=3')))
      .toEqual({ tab: 'unread', type: 'BID_OUTBID', q: 'watch', page: 3 })
    expect(parseNotificationFilters(new URLSearchParams('tab=bogus&type=NOPE&page=-2')))
      .toEqual({ tab: 'all', type: null, q: '', page: 1 })
  })

  it('round-trips through search params, omitting defaults', () => {
    const f = { tab: 'unread' as const, type: 'AUCTION_WON', q: 'vase', page: 2 }
    expect(toNotificationSearchParams(f).toString()).toBe('tab=unread&type=AUCTION_WON&q=vase&page=2')
    expect(parseNotificationFilters(toNotificationSearchParams(f))).toEqual(f)
    expect(toNotificationSearchParams({ tab: 'all', type: null, q: '', page: 1 }).toString()).toBe('')
  })

  it('maps to the 0-based API query', () => {
    expect(toListParams({ tab: 'unread', type: 'BID_OUTBID', q: ' watch ', page: 2 }, 20))
      .toEqual({ page: 1, size: 20, read: false, types: ['BID_OUTBID'], search: 'watch' })
    expect(toListParams({ tab: 'all', type: null, q: '', page: 1 }, 20)).toEqual({ page: 0, size: 20 })
  })

  it('caps page and truncates q', () => {
    const f = parseNotificationFilters(new URLSearchParams({ page: '99999999', q: 'x'.repeat(250) }))
    expect(f.page).toBe(10000)
    expect(f.q).toHaveLength(100)
  })

  it('offers only real backend types in the type filter', () => {
    for (const { value } of NOTIFICATION_TYPE_FILTERS) {
      expect(NOTIFICATION_SERVER_TYPES).toContain(value)
    }
  })
})
