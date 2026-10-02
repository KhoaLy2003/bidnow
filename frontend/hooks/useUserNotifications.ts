'use client'

import { useEffect } from 'react'
import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'
import { useAuthStore } from '@/store/authStore'
import { useNotificationStore } from '@/store/notificationStore'
import { getFreshAccessToken } from '@/lib/apiClient'
import { parseUserNotificationMessage } from '@/lib/realtime/dispatch'
import { resolveWsEndpoint, withAccessToken } from '@/lib/realtime/ws-url'
import { shouldToast } from '@/types/mappers/notification.mapper'
import { showNotificationToast } from '@/components/notification/NotificationToast'

const USER_QUEUE = '/user/queue/notifications'
const FOCUS_RESYNC_MIN_MS = 10_000

/**
 * Personal notifications for the logged-in (non-admin) user: loads the recent list and unread count, keeps them live
 * from `/user/queue/notifications`, toasts the important types, and resyncs on every (re)connect and window focus
 * (which also picks up changes made in another tab). Mount once (UserNotificationsBridge in the root layout).
 */
export function useUserNotifications(): void {
  const userId = useAuthStore((s) => s.user?.id ?? null)
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN')

  useEffect(() => {
    const store = useNotificationStore.getState()
    if (!userId || isAdmin) {
      store.reset()
      return
    }

    let lastResync = 0
    const resync = () => {
      lastResync = Date.now()
      const s = useNotificationStore.getState()
      void s.loadRecent()
      void s.refreshCount()
    }
    resync()
    const onFocus = () => {
      if (Date.now() - lastResync >= FOCUS_RESYNC_MIN_MS) resync()
    }
    window.addEventListener('focus', onFocus)

    const endpoint = resolveWsEndpoint({
      wsUrl:  process.env.NEXT_PUBLIC_WS_URL,
      apiUrl: process.env.NEXT_PUBLIC_API_URL,
    })

    const onMessage = (message: IMessage) => {
      const parsed = parseUserNotificationMessage(message.body)
      if (!parsed) return
      const added = useNotificationStore.getState().applyPush(parsed)
      if (added && shouldToast(added)) showNotificationToast(added)
    }

    const client = new Client({
      reconnectDelay:    1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 0, // the broker has no heartbeats configured
      heartbeatOutgoing: 0,
      beforeConnect: async (c) => {
        const token = await getFreshAccessToken()
        if (!token) {
          // Session gone: stompjs checks `active` after beforeConnect and will not connect (no anonymous reconnect loop).
          void client.deactivate()
          return
        }
        c.brokerURL = withAccessToken(endpoint, token)
      },
      onConnect: () => {
        client.subscribe(USER_QUEUE, onMessage)
        resync() // recover anything pushed while disconnected
      },
    })
    client.activate()

    return () => {
      window.removeEventListener('focus', onFocus)
      void client.deactivate()
      useNotificationStore.getState().reset() // drop this user's data; late responses are ignored (epoch)
    }
  }, [userId, isAdmin])
}
