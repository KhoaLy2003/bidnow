'use client'

import { useEffect, useRef } from 'react'
import { io, type Socket } from 'socket.io-client'
import { useAuctionStore } from '@/store/auctionStore'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Bid } from '@/types/ui/auction.ui'

interface BidNewEvent      { bid: Bid }
interface AuctionStatusEvent { status: AuctionStatus }
interface AuctionEndEvent  { winnerId?: string; finalBid: number }

export function useAuctionSocket(auctionId: string) {
  const { currentBid, bidHistory, status } = useAuctionStore()
  const socketRef = useRef<Socket | null>(null)

  useEffect(() => {
    const url = process.env.NEXT_PUBLIC_SOCKET_URL
    if (!url) return

    useAuctionStore.getState().reset()

    const socket = io(url, { autoConnect: true })
    socketRef.current = socket

    socket.emit('auction:join', auctionId)

    socket.on('bid:new', ({ bid }: BidNewEvent) => {
      const store = useAuctionStore.getState()
      store.setBid(bid.amount)
      store.addBidToHistory(bid)
    })
    socket.on('auction:status', ({ status: s }: AuctionStatusEvent) => {
      useAuctionStore.getState().setStatus(s)
    })
    socket.on('auction:end', ({ finalBid }: AuctionEndEvent) => {
      const store = useAuctionStore.getState()
      store.setBid(finalBid)
      store.setStatus(AuctionStatus.Closed)
    })

    return () => {
      socket.emit('auction:leave', auctionId)
      socket.disconnect()
      socketRef.current = null
    }
  }, [auctionId])

  return { currentBid, bidHistory, status }
}
