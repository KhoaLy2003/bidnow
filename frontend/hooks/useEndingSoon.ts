'use client'

import { useEffect, useState } from 'react'
import { auctionService } from '@/services/auction.service'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

const REFRESH_MS = 60_000

/** Auctions closing soonest, refreshed once a minute. Empty on failure so callers can just hide. */
export function useEndingSoon(limit = 5): AuctionBrowseItem[] {
  const [items, setItems] = useState<AuctionBrowseItem[]>([])

  useEffect(() => {
    let cancelled = false

    const load = () => {
      auctionService
        .getBrowseAuctions({ endingSoon: true, size: limit })
        .then((res) => { if (!cancelled) setItems(res.items) })
        .catch(() => { if (!cancelled) setItems([]) })
    }

    load()
    const id = setInterval(load, REFRESH_MS)
    return () => { cancelled = true; clearInterval(id) }
  }, [limit])

  return items
}
