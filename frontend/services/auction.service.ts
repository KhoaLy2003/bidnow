import { mapAuctionDetailResponse } from '@/types/mappers/auction.mapper'
import { MOCK_AUCTIONS, MOCK_BIDS } from '@/lib/mock-data'
import type { Auction, BidHistoryItem, AuctionDetail } from '@/types/ui/auction.ui'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  CreateAuctionRequest,
  UpdateAuctionRequest,
  AuctionResponse,
  AuctionSummaryResponse,
  AuctionCategoryResponse,
  AuctionDetailResponse,
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

const MOCK_DETAIL_RESPONSES: AuctionDetailResponse[] = [
  {
    id: '1',
    title: 'Vintage Omega Seamaster 1968',
    description: 'An exceptional example of the iconic Omega Seamaster in near-mint condition. Dial, hands, and case all original. Serviced 2023. Rare cal. 565 movement, original dial, box and papers.',
    category: { id: 'watches', name: 'Watches', slug: 'watches' },
    startingPrice: 500,
    bidIncrement: 10,
    depositAmount: 200,
    currentPrice: 1350,
    totalBids: 14,
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 3_600_000).toISOString(),
    endTime: new Date(Date.now() + 7_200_000).toISOString(),
    originalEndTime: new Date(Date.now() + 7_200_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u1', name: 'Marcus W.', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 86_400_000).toISOString(),
  },
  {
    id: '2',
    title: 'Gibson Les Paul Custom 1974',
    description: 'All-original Black Beauty with case, OHSC.',
    category: { id: 'music', name: 'Music', slug: 'music' },
    startingPrice: 1000,
    bidIncrement: 50,
    buyNowPrice: 3500,
    depositAmount: 500,
    currentPrice: 2800,
    totalBids: 31,
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 7_200_000).toISOString(),
    endTime: new Date(Date.now() + 3_600_000).toISOString(),
    originalEndTime: new Date(Date.now() + 3_600_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u2', name: 'Vintage Strings', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 172_800_000).toISOString(),
  },
  {
    id: '3',
    title: "First Edition Harry Potter Philosopher's Stone",
    description: 'Bloomsbury 1997, first print run, unread condition.',
    category: { id: 'books', name: 'Books & Literature', slug: 'books' },
    startingPrice: 2000,
    bidIncrement: 100,
    depositAmount: 1000,
    currentPrice: 5400,
    totalBids: 57,
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 86_400_000).toISOString(),
    endTime: new Date(Date.now() + 1_800_000).toISOString(),
    originalEndTime: new Date(Date.now() + 1_800_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u3', name: 'RareBooks Co.', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 259_200_000).toISOString(),
  },
]

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
  async getAuctionById(id: string): Promise<AuctionDetail | null> {
    await delay(300)
    // TODO: replace with real API call:
    // const response = await fetch(`${API_URL}/api/v1/auctions/public/${id}`, { cache: 'no-store' })
    // if (!response.ok) return null
    // const body: ApiResponse<AuctionDetailResponse> = await response.json()
    // return mapAuctionDetailResponse(body.data)
    const dto = MOCK_DETAIL_RESPONSES.find((a) => a.id === id)
    return dto ? mapAuctionDetailResponse(dto) : null
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
