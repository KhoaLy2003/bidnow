import { create } from 'zustand'
import { notificationService } from '@/services/notification.service'
import { toNotification } from '@/types/mappers/notification.mapper'
import type { UserNotificationMessage } from '@/types/api/notification.api'
import type { Notification } from '@/types/ui/notification.ui'

export const RECENT_LIMIT = 10

interface NotificationState {
  /** The most recent notifications (≤ RECENT_LIMIT), newest first — the bell dropdown. */
  notifications: Notification[]
  unreadCount:   number
  loading:       boolean
  loadRecent:    () => Promise<void>
  refreshCount:  () => Promise<void>
  /** Applies a live push; returns the added notification, or null for a duplicate. */
  applyPush:     (msg: UserNotificationMessage) => Notification | null
  /** Optimistic; resolves false (state resynced from the server) if the server rejected it. */
  markRead:      (id: string) => Promise<boolean>
  markAllRead:   () => Promise<boolean>
  remove:        (id: string) => Promise<boolean>
  reset:         () => void
}

export const useNotificationStore = create<NotificationState>((set, get) => {
  let inFlightLoads = 0
  /** Bumped by reset(); a request that started in an earlier epoch must not write its result. */
  let epoch = 0
  /** Ids optimistically marked read whose request has not settled yet (not part of exported state). */
  const pendingRead = new Set<string>()

  /** Runs an optimistic change; if the request fails, resyncs from the server (no snapshot restore). */
  async function optimistic(
    change: () => void,
    request: () => Promise<unknown>,
    readIds: string[] = [],
  ): Promise<boolean> {
    readIds.forEach((id) => pendingRead.add(id))
    change()
    try {
      await request()
      readIds.forEach((id) => pendingRead.delete(id))
      return true
    } catch {
      readIds.forEach((id) => pendingRead.delete(id))
      await Promise.all([get().loadRecent(), get().refreshCount()])
      return false
    }
  }

  return {
    notifications: [],
    unreadCount:   0,
    loading:       false,

    loadRecent: async () => {
      const startEpoch = epoch
      inFlightLoads++
      set({ loading: true })
      try {
        const res = await notificationService.list({ page: 0, size: RECENT_LIMIT })
        if (startEpoch !== epoch) return
        const fetched = res.data.data.map(toNotification)
        set((s) => {
          const local = new Map(s.notifications.map((n) => [n.id, n]))
          const merged = fetched.map((n) => (local.get(n.id)?.isRead && pendingRead.has(n.id) ? { ...n, isRead: true } : n))
          const fetchedIds = new Set(fetched.map((n) => n.id))
          const extras = s.notifications.filter((n) => !fetchedIds.has(n.id))
          return {
            notifications: [...merged, ...extras]
              .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
              .slice(0, RECENT_LIMIT),
          }
        })
      } catch {
        // keep what we have; the next focus / reconnect retries
      } finally {
        if (startEpoch === epoch) {
          inFlightLoads = Math.max(0, inFlightLoads - 1)
          set({ loading: inFlightLoads > 0 })
        }
      }
    },

    refreshCount: async () => {
      const startEpoch = epoch
      try {
        const res = await notificationService.unreadCount()
        if (startEpoch !== epoch) return
        set({ unreadCount: res.data.count })
      } catch {
        // keep the last known count
      }
    },

    applyPush: (msg) => {
      if (get().notifications.some((n) => n.id === msg.notification.id)) return null
      const added = toNotification(msg.notification)
      set((s) => ({
        notifications: [added, ...s.notifications].slice(0, RECENT_LIMIT),
        unreadCount:   msg.unreadCount,
      }))
      return added
    },

    markRead: (id) => {
      const target = get().notifications.find((n) => n.id === id)
      return optimistic(
        () => {
          if (!target || target.isRead) return
          set((s) => ({
            notifications: s.notifications.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
            unreadCount:   Math.max(0, s.unreadCount - 1),
          }))
        },
        () => notificationService.markRead(id),
        target && !target.isRead ? [id] : [],
      )
    },

    markAllRead: () => {
      const unreadIds = get().notifications.filter((n) => !n.isRead).map((n) => n.id)
      return optimistic(
        () => set((s) => ({ notifications: s.notifications.map((n) => ({ ...n, isRead: true })), unreadCount: 0 })),
        () => notificationService.markAllRead(),
        unreadIds,
      )
    },

    remove: (id) => {
      const target = get().notifications.find((n) => n.id === id)
      return optimistic(
        () => set((s) => ({
          notifications: s.notifications.filter((n) => n.id !== id),
          unreadCount:   target && !target.isRead ? Math.max(0, s.unreadCount - 1) : s.unreadCount,
        })),
        () => notificationService.remove(id),
      )
    },

    reset: () => {
      epoch++
      inFlightLoads = 0
      pendingRead.clear()
      set({ notifications: [], unreadCount: 0, loading: false })
    },
  }
})
