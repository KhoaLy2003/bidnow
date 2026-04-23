'use client'

import { useEffect, useRef } from 'react'
import { io, type Socket } from 'socket.io-client'
import { useAuctionStore } from '@/store/auctionStore'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Bid } from '@/types/auction'

interface BidNewEvent      { bid: Bid }
interface AuctionStatusEvent { status: AuctionStatus }
interface AuctionEndEvent  { winnerId?: string; finalBid: number }

export function useAuctionSocket(auctionId: string) {
  const { currentBid, bidHistory, status, setBid, addBidToHistory, setStatus } =
    useAuctionStore()
  const socketRef = useRef<Socket | null>(null)

  useEffect(() => {
    const url = process.env.NEXT_PUBLIC_SOCKET_URL
    if (!url) return

    const socket = io(url, { autoConnect: true })
    socketRef.current = socket

    socket.emit('auction:join', auctionId)

    socket.on('bid:new', ({ bid }: BidNewEvent) => {
      setBid(bid.amount)
      addBidToHistory(bid)
    })
    socket.on('auction:status', ({ status }: AuctionStatusEvent) => {
      setStatus(status)
    })
    socket.on('auction:end', ({ finalBid }: AuctionEndEvent) => {
      setBid(finalBid)
      setStatus(AuctionStatus.Closed)
    })

    return () => {
      socket.emit('auction:leave', auctionId)
      socket.disconnect()
      socketRef.current = null
    }
  }, [auctionId, setBid, addBidToHistory, setStatus])

  return { currentBid, bidHistory, status }
}
