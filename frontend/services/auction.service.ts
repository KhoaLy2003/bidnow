import { mapAuctionDetailResponse, mapAuctionBrowseItem, mapCategoryCount } from '@/types/mappers/auction.mapper'
import { MOCK_AUCTIONS, MOCK_BIDS } from '@/lib/mock-data'
import type { Auction, BidHistoryItem, AuctionDetail } from '@/types/ui/auction.ui'
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

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"

// Optional: Introduce a small artificial delay to simulate network latency
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export interface GetAuctionsParams {
  q?: string
  category?: string
  featured?: boolean
}



export const auctionService = {
  /**
   * Fetch auction categories.
   */
  async getCategories(): Promise<ApiResponse<AuctionCategoryResponse[]>> {
    const response = await fetch(`${API_URL}/api/v1/categories`, {
      method: "GET",
    });

    if (!response.ok) {
      // Safely handle empty error responses
      const errorText = await response.text();
      try {
        throw JSON.parse(errorText);
      } catch {
        throw new Error(errorText || `Failed with status ${response.status}`);
      }
    }

    return response.json();
  },

  /**
   * Create a new auction listing (Seller).
   */
  async createAuction(data: CreateAuctionRequest, token: string): Promise<ApiResponse<AuctionResponse>> {
    const response = await fetch(`${API_URL}/api/v1/auctions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    return response.json();
  },

  /**
   * List auctions owned by the authenticated seller.
   */
  async getMyAuctions(
    params: { type?: string; categoryId?: string; page?: number; size?: number },
    token: string
  ): Promise<ApiResponse<PageResponse<AuctionSummaryResponse>>> {
    const query = new URLSearchParams();
    if (params.type) query.append("type", params.type);
    if (params.categoryId) query.append("categoryId", params.categoryId);
    if (params.page !== undefined) query.append("page", params.page.toString());
    if (params.size !== undefined) query.append("size", params.size.toString());

    const url = `${API_URL}/api/v1/auctions/me${query.toString() ? '?' + query.toString() : ''}`;
    const response = await fetch(url, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`
      }
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    return response.json();
  },

  /**
   * Update an existing auction listing (Seller).
   */
  async updateAuction(id: string, data: UpdateAuctionRequest, token: string): Promise<ApiResponse<AuctionResponse>> {
    const response = await fetch(`${API_URL}/api/v1/auctions/${id}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    return response.json();
  },

  /**
   * Delete (soft) an auction (Seller).
   */
  async deleteAuction(id: string, token: string): Promise<void> {
    const response = await fetch(`${API_URL}/api/v1/auctions/${id}`, {
      method: "DELETE",
      headers: {
        "Authorization": `Bearer ${token}`
      }
    });

    if (!response.ok && response.status !== 204) {
      const error = await response.json();
      throw error;
    }
  },

  /**
   * Fetch a list of auctions with optional filtering.
   */
  async getAuctions(params?: GetAuctionsParams): Promise<Auction[]> {
    await delay(300) // Simulate network latency

    let results = [...MOCK_AUCTIONS]

    if (params?.featured) {
      results = results.filter((a) => a.isFeatured)
    }

    if (params?.category) {
      results = results.filter((a) => a.categoryId === params.category)
    }

    if (params?.q) {
      const query = params.q.toLowerCase()
      results = results.filter((a) => a.title.toLowerCase().includes(query))
    }

    return results
  },

  /**
   * Browse active auctions with filtering, sorting, and pagination.
   */
  async getBrowseAuctions(params: BrowseAuctionParams): Promise<{
    items:      AuctionBrowseItem[]
    total:      number
    totalPages: number
    page:       number
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

    const response = await fetch(`${API_URL}/api/v1/auctions/public?${query}`, { cache: 'no-store' })
    if (!response.ok) return { items: [], total: 0, totalPages: 0, page: 0 }

    const body: ApiResponse<PageResponse<AuctionBrowseItemResponse>> = await response.json()
    return {
      items:      body.data.data.map(mapAuctionBrowseItem),
      total:      body.data.pagination.total,
      totalPages: body.data.pagination.totalPages,
      page:       body.data.pagination.page,
    }
  },

  /**
   * Fetch active auction counts per category.
   * Independent of search/filter state — always reflects full catalogue counts.
   */
  async getCategoryCounts(): Promise<CategoryCount[]> {
    const response = await fetch(`${API_URL}/api/v1/auctions/public/category-counts`, { cache: 'no-store' })
    if (!response.ok) return []

    const body: ApiResponse<CategoryCountResponse[]> = await response.json()
    return body.data.map(mapCategoryCount)
  },

  /**
   * Fetch a single auction by its ID.
   */
  async getAuctionById(id: string): Promise<AuctionDetail | null> {
    const response = await fetch(`${API_URL}/api/v1/auctions/public/${id}`, { cache: 'no-store' })
    if (!response.ok) return null

    const body: ApiResponse<AuctionDetailResponse> = await response.json()
    return mapAuctionDetailResponse(body.data)
  },

  /**
   * Fetch bid history for a given auction.
   */
  async getBidHistory(auctionId: string): Promise<BidHistoryItem[]> {
    await delay(200)
    // Return mock bids matching the auction id, or fallback to the same mock bids for now
    return MOCK_BIDS.filter((b) => b.auctionId === auctionId).length > 0 
      ? MOCK_BIDS.filter((b) => b.auctionId === auctionId)
      : MOCK_BIDS.map(b => ({ ...b, auctionId })) // Just for visual purposes on other items
  },
}
