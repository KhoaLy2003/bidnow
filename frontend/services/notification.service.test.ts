import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { notificationService } from './notification.service'

const mockFetch = vi.mocked(apiFetch)

function ok(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('notificationService', () => {
  beforeEach(() => mockFetch.mockReset())

  it('lists with page, size, read, each type and a trimmed search', async () => {
    mockFetch.mockResolvedValue(ok({ data: { data: [], pagination: {} } }))

    await notificationService.list({ page: 1, size: 20, read: false, types: ['BID_OUTBID', 'AUCTION_WON'], search: ' watch ' })

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/notifications?page=1&size=20&read=false&types=BID_OUTBID&types=AUCTION_WON&search=watch')
  })

  it('omits unset filters', async () => {
    mockFetch.mockResolvedValue(ok({ data: { data: [], pagination: {} } }))

    await notificationService.list({})

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/notifications?page=0&size=20')
  })

  it('uses the right method and path for each action', async () => {
    mockFetch.mockImplementation(async () => ok({ data: {} }))

    await notificationService.unreadCount()
    await notificationService.markRead('n-1')
    await notificationService.markUnread('n-1')
    await notificationService.markAllRead()
    await notificationService.remove('n-1')
    await notificationService.removeRead()
    await notificationService.removeAll()

    expect(mockFetch.mock.calls).toEqual([
      ['/api/v1/notifications/unread-count'],
      ['/api/v1/notifications/n-1/read', { method: 'PUT' }],
      ['/api/v1/notifications/n-1/unread', { method: 'PUT' }],
      ['/api/v1/notifications/mark-all-read', { method: 'PUT' }],
      ['/api/v1/notifications/n-1', { method: 'DELETE' }],
      ['/api/v1/notifications/delete-read', { method: 'DELETE' }],
      ['/api/v1/notifications/delete-all', { method: 'DELETE' }],
    ])
  })

  it('throws the error body on failure', async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ errorCode: 'NOT_FOUND' }), { status: 404 }))

    await expect(notificationService.markRead('x')).rejects.toEqual({ errorCode: 'NOT_FOUND' })
  })

  it('throws { status } when an error body is not JSON', async () => {
    mockFetch.mockResolvedValue(new Response('<html>', { status: 502 }))

    await expect(notificationService.markRead('x')).rejects.toEqual({ status: 502 })
  })
})
