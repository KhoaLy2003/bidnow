'use client'

import { useCallback, useEffect, useState } from 'react'
import { filterToStatus, type PurchaseFilter } from '@/lib/purchase-utils'
import { purchaseService } from '@/services/purchase.service'
import { mapPurchasePage } from '@/types/mappers/purchase.mapper'
import type { PurchasePage } from '@/types/ui/purchase.ui'

interface Settled {
  key:    string
  failed: boolean
}

/**
 * One page of the caller's purchases for a filter. Loading is derived (the last settled request is not the current
 * one), and an effect that is cleaned up ignores its response, so a slow response for a filter the user has already
 * left never overwrites the current list. The last successful page stays in `data` while the next one loads.
 */
export function usePurchases(filter: PurchaseFilter, pageIndex: number) {
  const [data, setData] = useState<PurchasePage | null>(null)
  const [settled, setSettled] = useState<Settled | null>(null)
  const [nonce, setNonce] = useState(0)

  const key = `${filter}|${pageIndex}|${nonce}`

  useEffect(() => {
    let cancelled = false
    purchaseService
      .getMyPurchases({ status: filterToStatus(filter), page: pageIndex })
      .then((response) => {
        if (cancelled) return
        setData(mapPurchasePage(response))
        setSettled({ key, failed: false })
      })
      .catch(() => {
        if (!cancelled) setSettled({ key, failed: true })
      })
    return () => {
      cancelled = true
    }
  }, [filter, pageIndex, key])

  const refetch = useCallback(() => setNonce((n) => n + 1), [])

  return {
    data,
    loading: settled?.key !== key,
    failed:  settled?.key === key && settled.failed,
    refetch,
  }
}
