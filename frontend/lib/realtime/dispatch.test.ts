import { describe, expect, it, vi } from 'vitest'
import { dispatchRealtimeMessage, parseRealtimeMessage, parseUserNotificationMessage, type RealtimeHandlers } from './dispatch'

const notification = {
  id: 'n-1',
  type: 'BID_OUTBID',
  title: "You've been outbid",
  message: 'Someone outbid you on "Vase". Current price: $120.00.',
  actionUrl: '/auctions/a-1',
  auctionId: 'a-1',
  metadata: { currentPrice: '120' },
  read: false,
  createdAt: '2026-10-01T10:00:00',
}

describe('parseUserNotificationMessage', () => {
  it('returns the envelope with the server unread count', () => {
    const body = JSON.stringify({ type: 'NOTIFICATION', notification, unreadCount: 3 })

    expect(parseUserNotificationMessage(body)).toEqual({ type: 'NOTIFICATION', notification, unreadCount: 3 })
  })

  it('rejects other envelopes, malformed JSON, incomplete notifications and a missing count', () => {
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'OUTBID', auctionId: 'a-1', payload: {} }))).toBeNull()
    expect(parseUserNotificationMessage('not json')).toBeNull()
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'NOTIFICATION', unreadCount: 1 }))).toBeNull()
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'NOTIFICATION', notification: { id: 'n-1' }, unreadCount: 1 }))).toBeNull()
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'NOTIFICATION', notification }))).toBeNull()
  })
})

describe('parseRealtimeMessage', () => {
  it('no longer accepts the removed OUTBID message', () => {
    const body = JSON.stringify({ type: 'OUTBID', auctionId: 'a-1', payload: { currentPrice: 1 } })

    expect(parseRealtimeMessage(body)).toBeNull()
  })
})

describe('dispatchRealtimeMessage', () => {
  const handlers = (): RealtimeHandlers => ({
    bidPlaced: vi.fn(), extended: vi.fn(), ended: vi.fn(), cancelled: vi.fn(),
  })

  it('ignores messages for another auction', () => {
    const h = handlers()
    const msg = parseRealtimeMessage(JSON.stringify({ type: 'AUCTION_CANCELLED', auctionId: 'a-2', payload: { reason: null } }))

    expect(dispatchRealtimeMessage(msg!, 'a-1', h)).toBe(false)
    expect(h.cancelled).not.toHaveBeenCalled()
  })

  it('routes messages for the open auction', () => {
    const h = handlers()
    const msg = parseRealtimeMessage(JSON.stringify({ type: 'AUCTION_CANCELLED', auctionId: 'a-1', payload: { reason: 'fraud' } }))

    expect(dispatchRealtimeMessage(msg!, 'a-1', h)).toBe(true)
    expect(h.cancelled).toHaveBeenCalledWith({ reason: 'fraud' })
  })
})
