import { MOCK_AUCTIONS, MOCK_BIDS } from '@/lib/mock-data'
import type { Auction, BidHistoryItem } from '@/types/ui/auction.ui'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  CreateAuctionRequest,
  UpdateAuctionRequest,
  AuctionResponse,
  AuctionSummaryResponse,
  AuctionCategoryResponse
} from '@/types/api/auction.api'

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"

// Optional: Introduce a small artificial delay to simulate network latency
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export interface GetAuctionsParams {
  q?: string
  category?: string
  featured?: boolean
}

export interface GetBrowseAuctionsParams {
  q?: string
}

const CATEGORY_NAMES: Record<string, string> = {
  watches:  'Watches',
  music:    'Music',
  books:    'Books',
  sneakers: 'Sneakers',
  cameras:  'Cameras',
  art:      'Art',
}

// Buy now prices added to select mock items for demo purposes
const MOCK_BUY_NOW_PRICES: Record<string, number> = {
  '2': 350_000,   // Gibson Les Paul
  '5': 120_000,   // Nike Air Jordan
  '8': 95_000,    // Leica M3
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
      } catch (e) {
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
   * Fetch auction summaries for the public browse page.
   * Returns AuctionBrowseItem[] shaped for the browse UI.
   * Replace this mock with a real API call when the browse endpoint is available.
   */
  async getBrowseAuctions(params?: GetBrowseAuctionsParams): Promise<AuctionBrowseItem[]> {
    await delay(300)

    let results = [...MOCK_AUCTIONS]

    if (params?.q) {
      const query = params.q.toLowerCase()
      results = results.filter((a) => a.title.toLowerCase().includes(query))
    }

    return results.map((a): AuctionBrowseItem => ({
      id:              a.id,
      title:           a.title,
      primaryImageUrl: a.imageUrls[0] ?? null,
      currentPrice:    a.currentBid,
      totalBids:       a.totalBids,
      endTime:         a.endsAt,
      status:          a.status,
      buyNowPrice:     a.buyNowPrice ?? MOCK_BUY_NOW_PRICES[a.id] ?? null,
      categoryName:    CATEGORY_NAMES[a.categoryId] ?? a.categoryId,
    }))
  },

  /**
   * Fetch a single auction by its ID.
   */
  async getAuctionById(id: string): Promise<Auction | null> {
    await delay(300)
    return MOCK_AUCTIONS.find((a) => a.id === id) ?? null
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
