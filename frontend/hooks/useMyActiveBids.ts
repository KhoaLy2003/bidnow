'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { bidService } from '@/services/bid.service'
import { useNotificationStore } from '@/store/notificationStore'
import { isBidStatusPush, MY_BIDS_POLL_MS } from '@/lib/my-bids'
import type { MyActiveBid } from '@/types/ui/bid.ui'

interface MyActiveBidsState {
  /** null until the first load succeeds. */
  bids:    MyActiveBid[] | null
  /** The latest load failed; `bids` still holds the last good list, if any. */
  error:   boolean
  reload:  () => void
}

/**
 * The signed-in user's active bids. Refetches every {@link MY_BIDS_POLL_MS} while the page is visible, when the
 * tab becomes visible again, and when an outbid/won/lost notification arrives. A failed refetch keeps the last list.
 */
export function useMyActiveBids(): MyActiveBidsState {
  const [bids, setBids] = useState<MyActiveBid[] | null>(null)
  const [error, setError] = useState(false)
  /** Only the newest request may write its result; older ones (and ones after unmount) are dropped. */
  const latestRequest = useRef(0)

  const dropInFlight = useCallback(() => { latestRequest.current++ }, [])

  const load = useCallback(() => {
    const request = ++latestRequest.current
    bidService.getMyActiveBids().then(
      (next) => {
        if (request !== latestRequest.current) return
        setBids(next)
        setError(false)
      },
      () => {
        if (request !== latestRequest.current) return
        setError(true)
      },
    )
  }, [])

  useEffect(() => {
    load()

    const refreshIfVisible = () => {
      if (document.visibilityState === 'visible') load()
    }
    const poll = setInterval(refreshIfVisible, MY_BIDS_POLL_MS)
    document.addEventListener('visibilitychange', refreshIfVisible)

    let lastSeenId = useNotificationStore.getState().notifications[0]?.id
    const unsubscribe = useNotificationStore.subscribe((state) => {
      const latest = state.notifications[0]
      if (!latest || latest.id === lastSeenId) return
      lastSeenId = latest.id
      if (isBidStatusPush(latest.type)) load()
    })

    return () => {
      dropInFlight()
      clearInterval(poll)
      document.removeEventListener('visibilitychange', refreshIfVisible)
      unsubscribe()
    }
  }, [load, dropInFlight])

  return { bids, error, reload: load }
}
