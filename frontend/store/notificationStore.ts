import { create } from 'zustand'
import { notificationService } from '@/services/notification.service'
import { toNotification } from '@/types/mappers/notification.mapper'
import type { UserNotificationMessage } from '@/types/api/notification.api'
import type { Notification } from '@/types/ui/notification.ui'

export const RECENT_LIMIT = 10

interface NotificationState {
  /** The most recent notifications, newest first — the bell dropdown (RECENT_LIMIT, plus any pages loaded on demand). */
  notifications: Notification[]
  unreadCount:   number
  loading:       boolean
  /** Server-side page index the next loadMore() fetches (page 0 is the recent list). */
  nextPage:      number
  /** Whether older notifications exist beyond those loaded. */
  hasMore:       boolean
  loadingMore:   boolean
  loadRecent:    () => Promise<void>
  /** Appends the next page of older notifications; resolves false if the request failed. */
  loadMore:      () => Promise<boolean>
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
    nextPage:      1,
    hasMore:       false,
    loadingMore:   false,

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
          const sorted = [...merged, ...extras].sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
          // Once older pages were loaded on demand, keep them; otherwise this is the capped recent list.
          return s.nextPage > 1
            ? { notifications: sorted }
            : { notifications: sorted.slice(0, RECENT_LIMIT), hasMore: res.data.pagination?.hasNext === true }
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

    loadMore: async () => {
      const { loadingMore, hasMore, nextPage } = get()
      if (loadingMore || !hasMore) return true
      const startEpoch = epoch
      set({ loadingMore: true })
      try {
        const res = await notificationService.list({ page: nextPage, size: RECENT_LIMIT })
        if (startEpoch !== epoch) return true
        const fetched = res.data.data.map(toNotification)
        set((s) => {
          // Pushes shift server offsets, so a page can overlap what we already hold: dedupe by id.
          const known = new Set(s.notifications.map((n) => n.id))
          const older = fetched.filter((n) => !known.has(n.id))
          return {
            notifications: [...s.notifications, ...older].sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime()),
            nextPage:      s.nextPage + 1,
            hasMore:       res.data.pagination?.hasNext === true,
          }
        })
        return true
      } catch {
        return false // keep the list and the button so the user can retry
      } finally {
        if (startEpoch === epoch) set({ loadingMore: false })
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
      set((s) => {
        const all = [added, ...s.notifications]
        // Keep everything once the user expanded the list; otherwise cap, and note the pushed-out item is now "older".
        const capped = s.nextPage > 1 ? all : all.slice(0, RECENT_LIMIT)
        return {
          notifications: capped,
          unreadCount:   msg.unreadCount,
          hasMore:       s.hasMore || capped.length < all.length,
        }
      })
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
      set({ notifications: [], unreadCount: 0, loading: false, nextPage: 1, hasMore: false, loadingMore: false })
    },
  }
})
