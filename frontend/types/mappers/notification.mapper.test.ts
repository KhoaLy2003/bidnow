import { describe, expect, it } from 'vitest'
import { NOTIFICATION_SERVER_TYPES, type NotificationDto } from '@/types/api/notification.api'
import { shouldToast, toNotification, toNotificationType } from './notification.mapper'

const dto: NotificationDto = {
  id: 'n-1',
  type: 'BID_OUTBID',
  title: "You've been outbid",
  message: 'Someone outbid you on "Vase". Current price: $120.00.',
  actionUrl: '/auctions/a-1',
  auctionId: 'a-1',
  metadata: { currentPrice: '120' },
  read: false,
  createdAt: '2026-10-01T10:00:00+07:00',
}

describe('toNotificationType', () => {
  it('gives every backend type a category; only account/system types fall back to system', () => {
    const system = new Set(['USER_REGISTERED', 'OTP_VERIFICATION', 'SYSTEM_ANNOUNCEMENT'])
    for (const type of NOTIFICATION_SERVER_TYPES) {
      expect(toNotificationType(type) === 'system', type).toBe(system.has(type))
    }
  })

  it('maps the key types', () => {
    expect(toNotificationType('BID_OUTBID')).toBe('outbid')
    expect(toNotificationType('AUCTION_WON')).toBe('won')
    expect(toNotificationType('AUCTION_LOST')).toBe('lost')
    expect(toNotificationType('AUCTION_ENDING_SOON')).toBe('ending_soon')
    expect(toNotificationType('NEW_BID')).toBe('bid_placed')
    expect(toNotificationType('PAYMENT_REMINDER')).toBe('payment_due')
    expect(toNotificationType('PAYMENT_RECEIVED')).toBe('payment')
    expect(toNotificationType('PAYMENT_FAILED')).toBe('payment_failed')
    expect(toNotificationType('DEPOSIT_REFUNDED')).toBe('refund')
    expect(toNotificationType('AUCTION_EXTENDED')).toBe('auction')
  })

  it('maps unknown types to system', () => {
    expect(toNotificationType('SOMETHING_NEW')).toBe('system')
  })
})

describe('toNotification', () => {
  it('maps the DTO, keeping the server type and parsing the offset timestamp', () => {
    expect(toNotification(dto)).toEqual({
      id: 'n-1',
      type: 'outbid',
      serverType: 'BID_OUTBID',
      title: "You've been outbid",
      message: 'Someone outbid you on "Vase". Current price: $120.00.',
      isRead: false,
      createdAt: new Date('2026-10-01T03:00:00Z'),
      auctionId: 'a-1',
      linkUrl: '/auctions/a-1',
    })
  })

  it('turns null links into undefined', () => {
    const n = toNotification({ ...dto, actionUrl: null, auctionId: null })
    expect(n.linkUrl).toBeUndefined()
    expect(n.auctionId).toBeUndefined()
  })
})

describe('shouldToast', () => {
  it('toasts only the important types', () => {
    for (const type of ['BID_OUTBID', 'AUCTION_WON', 'PAYMENT_REQUIRED', 'PAYMENT_REMINDER', 'AUCTION_ENDING_SOON']) {
      expect(shouldToast(toNotification({ ...dto, type })), type).toBe(true)
    }
    for (const type of ['NEW_BID', 'AUCTION_LOST', 'DEPOSIT_REFUNDED', 'AUCTION_EXTENDED']) {
      expect(shouldToast(toNotification({ ...dto, type })), type).toBe(false)
    }
  })
})
