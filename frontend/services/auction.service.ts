import { mapAuctionDetailResponse, mapAuctionBrowseItem, mapCategoryCount } from '@/types/mappers/auction.mapper'
import { MOCK_BIDS } from '@/lib/mock-data'
import { apiFetch } from '@/lib/apiClient'
import type { BidHistoryItem, AuctionDetail } from '@/types/ui/auction.ui'
import type { AuctionBrowseItem, CategoryCount } from '@/types/ui/auction-browse.ui'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  CreateAuctionRequest,
  UpdateAuctionRequest,
  AuctionResponse,
  AuctionSummaryResponse,
  AuctionCategoryResponse,
  AuctionDetailResponse,
  AuctionBrowseItemResponse,
  CategoryCountResponse,
  BrowseAuctionParams,
} from '@/types/api/auction.api'

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export interface GetAuctionsParams {
  q?: string
  category?: string
  featured?: boolean
}

export const auctionService = {
  async getCategories(): Promise<ApiResponse<AuctionCategoryResponse[]>> {
    const response = await apiFetch('/api/v1/categories')
    if (!response.ok) {
      const errorText = await response.text()
      try {
        throw JSON.parse(errorText)
      } catch {
        throw new Error(errorText || `Failed with status ${response.status}`)
      }
    }
    return response.json()
  },

  async createAuction(data: CreateAuctionRequest): Promise<ApiResponse<AuctionResponse>> {
    const response = await apiFetch('/api/v1/auctions', {
      method: 'POST',
      body: JSON.stringify(data),
    })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async getMyAuctions(
    params: { type?: string; categoryId?: string; page?: number; size?: number }
  ): Promise<ApiResponse<PageResponse<AuctionSummaryResponse>>> {
    const query = new URLSearchParams()
    if (params.type) query.append('type', params.type)
    if (params.categoryId) query.append('categoryId', params.categoryId)
    if (params.page !== undefined) query.append('page', params.page.toString())
    if (params.size !== undefined) query.append('size', params.size.toString())
    const url = `/api/v1/auctions/me${query.toString() ? '?' + query.toString() : ''}`
    const response = await apiFetch(url, { method: 'GET' })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async updateAuction(
    id: string,
    data: UpdateAuctionRequest
  ): Promise<ApiResponse<AuctionResponse>> {
    const response = await apiFetch(`/api/v1/auctions/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async deleteAuction(id: string): Promise<void> {
    const response = await apiFetch(`/api/v1/auctions/${id}`, { method: 'DELETE' })
    if (!response.ok && response.status !== 204) {
      const error = await response.json()
      throw error
    }
  },

  async getBrowseAuctions(params: BrowseAuctionParams): Promise<{
    items: AuctionBrowseItem[]
    total: number
    totalPages: number
    page: number
  }> {
    const query = new URLSearchParams()
    if (params.keyword)                query.set('keyword', params.keyword)
    if (params.categorySlug)           query.set('categorySlug', params.categorySlug)
    if (params.minPrice !== undefined) query.set('minPrice', String(params.minPrice))
    if (params.maxPrice !== undefined) query.set('maxPrice', String(params.maxPrice))
    if (params.endingSoon)             query.set('endingSoon', 'true')
    if (params.buyNowAvailable)        query.set('buyNowAvailable', 'true')
    if (params.sortBy)                 query.set('sortBy', params.sortBy)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 20))
    const response = await apiFetch(`/api/v1/auctions/public?${query}`, { cache: 'no-store' })
    if (!response.ok) return { items: [], total: 0, totalPages: 0, page: 0 }
    const body: ApiResponse<PageResponse<AuctionBrowseItemResponse>> = await response.json()
    return {
      items:      body.data.data.map(mapAuctionBrowseItem),
      total:      body.data.pagination.total,
      totalPages: body.data.pagination.totalPages,
      page:       body.data.pagination.page,
    }
  },

  async getCategoryCounts(): Promise<CategoryCount[]> {
    const response = await apiFetch('/api/v1/auctions/public/category-counts', { cache: 'no-store' })
    if (!response.ok) return []
    const body: ApiResponse<CategoryCountResponse[]> = await response.json()
    return body.data.map(mapCategoryCount)
  },

  async getAuctionById(id: string): Promise<AuctionDetail | null> {
    const response = await apiFetch(`/api/v1/auctions/public/${id}`, { cache: 'no-store' })
    if (!response.ok) return null
    const body: ApiResponse<AuctionDetailResponse> = await response.json()
    return mapAuctionDetailResponse(body.data)
  },

  async getBidHistory(auctionId: string): Promise<BidHistoryItem[]> {
    await delay(200)
    return MOCK_BIDS.filter((b) => b.auctionId === auctionId).length > 0
      ? MOCK_BIDS.filter((b) => b.auctionId === auctionId)
      : MOCK_BIDS.map(b => ({ ...b, auctionId }))
  },
}
