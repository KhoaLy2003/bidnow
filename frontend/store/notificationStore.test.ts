import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/notification.service', () => ({
  notificationService: {
    list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn(), remove: vi.fn(),
  },
}))

import { notificationService } from '@/services/notification.service'
import type { NotificationDto } from '@/types/api/notification.api'
import { RECENT_LIMIT, useNotificationStore } from './notificationStore'

const service = vi.mocked(notificationService)

function dto(id: string, read = false): NotificationDto {
  return {
    id, type: 'BID_OUTBID', title: 't', message: 'm', actionUrl: null, auctionId: null, metadata: null,
    read, createdAt: '2026-10-01T10:00:00Z',
  }
}

const store = () => useNotificationStore.getState()

describe('notificationStore', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    useNotificationStore.getState().reset()
  })

  it('loadRecent fetches the latest page and maps it', async () => {
    service.list.mockResolvedValue({ data: { data: [dto('a'), dto('b', true)], pagination: {} } } as never)

    await store().loadRecent()

    expect(service.list).toHaveBeenCalledWith({ page: 0, size: RECENT_LIMIT })
    expect(store().notifications.map((n) => n.id)).toEqual(['a', 'b'])
    expect(store().loading).toBe(false)
  })

  it('refreshCount takes the server count', async () => {
    service.unreadCount.mockResolvedValue({ data: { count: 7 } } as never)

    await store().refreshCount()

    expect(store().unreadCount).toBe(7)
  })

  it('loadRecent ignores a response that resolves after reset()', async () => {
    let resolve!: (v: never) => void
    service.list.mockReturnValue(new Promise<never>((r) => { resolve = r }))

    const pending = store().loadRecent()
    store().reset()
    resolve({ data: { data: [dto('a')], pagination: {} } } as never)
    await pending

    expect(store().notifications).toEqual([])
    expect(store().loading).toBe(false)
  })

  it('refreshCount ignores a response that resolves after reset()', async () => {
    let resolve!: (v: never) => void
    service.unreadCount.mockReturnValue(new Promise<never>((r) => { resolve = r }))

    const pending = store().refreshCount()
    store().reset()
    resolve({ data: { count: 9 } } as never)
    await pending

    expect(store().unreadCount).toBe(0)
  })

  it('applyPush prepends, caps the list and uses the server count, not +1', () => {
    useNotificationStore.setState({
      notifications: Array.from({ length: RECENT_LIMIT }, (_, i) => ({
        id: `old-${i}`, type: 'system' as const, serverType: 'X', title: '', message: '', isRead: true, createdAt: new Date(),
      })),
      unreadCount: 1,
    })

    const added = store().applyPush({ type: 'NOTIFICATION', notification: dto('new'), unreadCount: 5 })

    expect(added?.id).toBe('new')
    expect(store().notifications[0].id).toBe('new')
    expect(store().notifications).toHaveLength(RECENT_LIMIT)
    expect(store().unreadCount).toBe(5)
  })

  it('applyPush ignores a duplicate id', () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 1 })

    const again = store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 9 })

    expect(again).toBeNull()
    expect(store().notifications).toHaveLength(1)
    expect(store().unreadCount).toBe(1)
  })

  it('markRead is optimistic and resyncs from the server on failure', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 3 })
    let reject: (e: unknown) => void = () => {}
    service.markRead.mockReturnValue(new Promise((_, r) => { reject = r }) as never)
    service.list.mockResolvedValue({ data: { data: [dto('a')], pagination: {} } } as never)
    service.unreadCount.mockResolvedValue({ data: { count: 3 } } as never)

    const pending = store().markRead('a')
    expect(store().notifications[0].isRead).toBe(true)
    expect(store().unreadCount).toBe(2)

    reject({ errorCode: 'X' })
    expect(await pending).toBe(false)
    expect(store().notifications.map((n) => n.id)).toEqual(['a'])
    expect(store().notifications[0].isRead).toBe(false)
    expect(store().unreadCount).toBe(3)
  })

  it('markAllRead resyncs from the server on failure', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 4 })
    service.markAllRead.mockRejectedValue({ errorCode: 'X' })
    service.list.mockResolvedValue({ data: { data: [dto('a')], pagination: {} } } as never)
    service.unreadCount.mockResolvedValue({ data: { count: 4 } } as never)

    const saved = await store().markAllRead()

    expect(saved).toBe(false)
    expect(service.list).toHaveBeenCalled()
    expect(store().unreadCount).toBe(4)
    expect(store().notifications.map((n) => n.id)).toEqual(['a'])
    expect(store().notifications[0].isRead).toBe(false)
  })

  it('markAllRead keeps the optimistic state on success', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 4 })
    service.markAllRead.mockResolvedValue({ data: { updated: 4 } } as never)

    expect(await store().markAllRead()).toBe(true)
    expect(store().unreadCount).toBe(0)
    expect(store().notifications[0].isRead).toBe(true)
  })

  it('remove resyncs from the server on failure', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 2 })
    service.remove.mockRejectedValue({ errorCode: 'X' })
    service.list.mockResolvedValue({ data: { data: [dto('a')], pagination: {} } } as never)
    service.unreadCount.mockResolvedValue({ data: { count: 2 } } as never)

    const saved = await store().remove('a')

    expect(saved).toBe(false)
    expect(store().notifications.map((n) => n.id)).toEqual(['a'])
    expect(store().unreadCount).toBe(2)
  })

  it('remove succeeds and decrements for an unread item', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 2 })
    service.remove.mockResolvedValue({ data: 'ok' } as never)

    expect(await store().remove('a')).toBe(true)
    expect(store().notifications).toEqual([])
    expect(store().unreadCount).toBe(1)
  })

  describe('loadRecent merge', () => {
    it('keeps a push applied while the load is pending', async () => {
      let resolve: (v: unknown) => void = () => {}
      service.list.mockReturnValue(new Promise((r) => { resolve = r }) as never)

      const pending = store().loadRecent()
      store().applyPush({ type: 'NOTIFICATION', notification: { ...dto('pushed'), createdAt: '2026-10-01T12:00:00Z' }, unreadCount: 2 })
      resolve({ data: { data: [dto('a')], pagination: {} } })
      await pending

      expect(store().notifications.map((n) => n.id)).toEqual(['pushed', 'a'])
    })

    it('keeps a locally read item read while its markRead is in flight', async () => {
      store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 1 })
      service.markRead.mockReturnValue(new Promise(() => {}) as never)
      void store().markRead('a')
      service.list.mockResolvedValue({ data: { data: [dto('a', false)], pagination: {} } } as never)

      await store().loadRecent()

      expect(store().notifications[0].isRead).toBe(true)
    })

    it('caps and sorts newest first', async () => {
      const items = Array.from({ length: RECENT_LIMIT + 3 }, (_, i) => ({
        ...dto(`n-${i}`), createdAt: new Date(Date.UTC(2026, 9, 1, i)).toISOString(),
      }))
      service.list.mockResolvedValue({ data: { data: items, pagination: {} } } as never)

      await store().loadRecent()

      expect(store().notifications).toHaveLength(RECENT_LIMIT)
      expect(store().notifications[0].id).toBe(`n-${RECENT_LIMIT + 2}`)
    })

    it('keeps loading true until all concurrent loads finish', async () => {
      const resolvers: Array<(v: unknown) => void> = []
      service.list.mockImplementation(() => new Promise((r) => { resolvers.push(r) }) as never)

      const p1 = store().loadRecent()
      const p2 = store().loadRecent()
      resolvers[0]({ data: { data: [], pagination: {} } })
      await p1
      expect(store().loading).toBe(true)
      resolvers[1]({ data: { data: [], pagination: {} } })
      await p2
      expect(store().loading).toBe(false)
    })
  })
})
