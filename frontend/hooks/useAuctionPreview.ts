'use client'

import { useEffect, useState } from 'react'
import { auctionService } from '@/services/auction.service'
import { toAuctionPreview, type AuctionPreview } from '@/lib/payment-utils'

/** Per-session cache of the in-flight/finished lookups; failures are evicted so the next mount retries. */
const cache = new Map<string, Promise<AuctionPreview | null>>()

function loadPreview(auctionId: string): Promise<AuctionPreview | null> {
  const cached = cache.get(auctionId)
  if (cached) return cached
  const request = auctionService
    .getAuctionById(auctionId)
    .then((auction) => (auction ? toAuctionPreview(auction) : null))
    .catch(() => null)
  cache.set(auctionId, request)
  void request.then((preview) => {
    if (!preview) cache.delete(auctionId)
  })
  return request
}

/** Title and image of an auction. `undefined` while loading, `null` when it could not be loaded. */
export function useAuctionPreview(auctionId: string): AuctionPreview | null | undefined {
  const [preview, setPreview] = useState<AuctionPreview | null | undefined>(undefined)

  useEffect(() => {
    let cancelled = false
    void loadPreview(auctionId).then((p) => {
      if (!cancelled) setPreview(p)
    })
    return () => {
      cancelled = true
    }
  }, [auctionId])

  return preview
}
