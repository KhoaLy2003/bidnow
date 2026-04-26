import { create } from 'zustand'
import type { Notification } from '@/types/notification'

interface NotificationState {
  notifications:      Notification[]
  unreadCount:        number
  addNotification:    (n: Notification) => void
  markRead:           (id: string) => void
  markAllRead:        () => void
  removeNotification: (id: string) => void
}

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: [],
  unreadCount:   0,

  addNotification: (n) =>
    set((state) => ({
      notifications: [n, ...state.notifications],
      unreadCount:   state.unreadCount + (n.isRead ? 0 : 1),
    })),

  markRead: (id) =>
    set((state) => {
      const notifications = state.notifications.map((n) =>
        n.id === id ? { ...n, isRead: true } : n
      )
      return {
        notifications,
        unreadCount: notifications.filter((n) => !n.isRead).length,
      }
    }),

  markAllRead: () =>
    set((state) => ({
      notifications: state.notifications.map((n) => ({ ...n, isRead: true })),
      unreadCount:   0,
    })),

  removeNotification: (id) =>
    set((state) => {
      const notifications = state.notifications.filter((n) => n.id !== id)
      return {
        notifications,
        unreadCount: notifications.filter((n) => !n.isRead).length,
      }
    }),
}))
