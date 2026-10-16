import { apiFetch } from '@/lib/apiClient'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type { GetPurchasesParams, PurchaseResponse } from '@/types/api/purchase.api'

export const PURCHASES_PAGE_SIZE = 10

export const purchaseService = {
  /** The caller's won auctions (bid wins and Buy It Now), newest first, with their payment status. */
  async getMyPurchases(params: GetPurchasesParams = {}): Promise<PageResponse<PurchaseResponse>> {
    const query = new URLSearchParams()
    if (params.status) query.set('status', params.status)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? PURCHASES_PAGE_SIZE))
    const response = await apiFetch(`/api/v1/auctions/me/purchases?${query}`)
    if (!response.ok) throw new Error(`Failed to load purchases (${response.status})`)
    const body: ApiResponse<PageResponse<PurchaseResponse>> = await response.json()
    return body.data
  },
}
