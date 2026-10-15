'use client'

import { useEffect, useState } from 'react'
import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'
import { seedHeroLot } from '@/lib/hero-watch'
import {
  applyAuctionCancelled,
  applyAuctionEnded,
  applyAuctionExtended,
  applyBidPlaced,
  type LiveAuctionState,
} from '@/lib/realtime/auction-live'
import { dispatchRealtimeMessage, parseRealtimeMessage, type RealtimeHandlers } from '@/lib/realtime/dispatch'
import { resolveWsEndpoint } from '@/lib/realtime/ws-url'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import type { BidEntry } from '@/types/ui/auction.ui'

export interface LiveLot {
  state:       LiveAuctionState
  /** The newest bid received over the socket since the page loaded, if any. */
  lastBid:     BidEntry | null
  /** Bids received live; changes on every new bid so effects can react to it. */
  bidCount:    number
  /** Times the end time moved (anti-sniping) since the page loaded. */
  extendCount: number
}

function endTime(s: LiveAuctionState): number {
  return s.endsAt.getTime()
}

function seed(lot: AuctionBrowseItem | null): LiveLot | null {
  return lot ? { state: seedHeroLot(lot), lastBid: null, bidCount: 0, extendCount: 0 } : null
}

/**
 * Follows one auction for the homepage hero. It listens to the same public topic as the detail page
 * but keeps its state local, so it never touches `auctionStore` and shows no toasts. The server
 * snapshot seeds the state; every push goes through the same monotonic reducers the detail page uses.
 */
export function useLiveLot(lot: AuctionBrowseItem | null): LiveLot | null {
  const [view, setView] = useState<LiveLot | null>(() => seed(lot))
  const lotId = lot?.id ?? null

  useEffect(() => {
    if (!lotId) return

    const endpoint = resolveWsEndpoint({
      wsUrl:  process.env.NEXT_PUBLIC_WS_URL,
      apiUrl: process.env.NEXT_PUBLIC_API_URL,
    })
    try {
      new URL(endpoint)
    } catch {
      return
    }

    const update = (next: (prev: LiveLot) => LiveLot) => setView((prev) => (prev ? next(prev) : prev))

    const handlers: RealtimeHandlers = {
      bidPlaced: (p) =>
        update((prev) => {
          const state = applyBidPlaced(prev.state, p, null)
          return {
            state,
            lastBid:     state.bids[0] ?? prev.lastBid,
            bidCount:    prev.bidCount + 1,
            extendCount: prev.extendCount + (endTime(state) > endTime(prev.state) ? 1 : 0),
          }
        }),
      extended: (p) =>
        update((prev) => {
          const state = applyAuctionExtended(prev.state, p)
          return { ...prev, state, extendCount: prev.extendCount + (endTime(state) > endTime(prev.state) ? 1 : 0) }
        }),
      ended:     (p) => update((prev) => ({ ...prev, state: applyAuctionEnded(prev.state, p) })),
      cancelled: () => update((prev) => ({ ...prev, state: applyAuctionCancelled(prev.state) })),
    }

    const client = new Client({
      brokerURL:         endpoint,
      reconnectDelay:    1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 0, // the broker has no heartbeats configured
      heartbeatOutgoing: 0,
      onConnect: () => {
        client.subscribe(`/topic/auctions/${lotId}`, (message: IMessage) => {
          const parsed = parseRealtimeMessage(message.body)
          if (parsed) dispatchRealtimeMessage(parsed, lotId, handlers)
        })
      },
    })

    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [lotId])

  return view
}
