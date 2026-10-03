'use client'

import { useEffect, useRef } from 'react'
import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'
import { toast } from 'sonner'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuthStore } from '@/store/authStore'
import { getFreshAccessToken } from '@/lib/apiClient'
import { dispatchRealtimeMessage, parseRealtimeMessage, type RealtimeHandlers } from '@/lib/realtime/dispatch'
import { resolveWsEndpoint, withAccessToken } from '@/lib/realtime/ws-url'

/**
 * Live auction updates over STOMP (spec §6). Subscribes to the auction topic; personal notifications
 * (incl. outbid toasts) come from `useUserNotifications`. Reconnects with exponential backoff; each (re)connect fetches a
 * fresh token, and every (re)connect calls `onResync` to recover events missed before the subscription.
 * Re-runs (disconnects and reconnects) when the auction or the logged-in user changes.
 */
export function useAuctionSocket(auctionId: string, onResync?: () => void): void {
  const userId = useAuthStore((s) => s.user?.id ?? null)
  const onResyncRef = useRef(onResync)

  useEffect(() => {
    onResyncRef.current = onResync
  }, [onResync])

  useEffect(() => {
    const endpoint = resolveWsEndpoint({
      wsUrl:  process.env.NEXT_PUBLIC_WS_URL,
      apiUrl: process.env.NEXT_PUBLIC_API_URL,
    })

    try {
      new URL(endpoint)
    } catch {
      console.error('Invalid WebSocket URL for live auction updates:', endpoint)
      return
    }

    const handlers: RealtimeHandlers = {
      bidPlaced: (p) => useAuctionStore.getState().bidPlaced(auctionId, p, userId),
      extended: (p) => {
        useAuctionStore.getState().extended(auctionId, p)
        toast.info(`Auction extended — now ends at ${new Date(p.newEndTime).toLocaleTimeString()}`)
      },
      ended: (p) => useAuctionStore.getState().ended(auctionId, p),
      cancelled: () => useAuctionStore.getState().cancelled(auctionId),
    }

    const onMessage = (message: IMessage) => {
      const parsed = parseRealtimeMessage(message.body)
      if (parsed) dispatchRealtimeMessage(parsed, auctionId, handlers)
    }

    const client = new Client({
      reconnectDelay:    1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 0, // the broker has no heartbeats configured (see roadmap risks)
      heartbeatOutgoing: 0,
      beforeConnect: async (c) => {
        const token = userId ? await getFreshAccessToken() : null
        c.brokerURL = withAccessToken(endpoint, token)
      },
      onConnect: () => {
        client.subscribe(`/topic/auctions/${auctionId}`, onMessage)
        onResyncRef.current?.()
      },
    })

    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [auctionId, userId])
}
