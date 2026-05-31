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
  {
    id: '4',
    title: 'Banksy - Girl with Balloon (Signed Print)',
    description: 'Authenticated signed limited edition screenprint. Certificate of authenticity included.',
    category: { id: 'art', name: 'Fine Art', slug: 'art' },
    startingPrice: 5000,
    bidIncrement: 200,
    depositAmount: 2000,
    currentPrice: 12400,
    totalBids: 22,
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 10_800_000).toISOString(),
    endTime: new Date(Date.now() + 14_400_000).toISOString(),
    originalEndTime: new Date(Date.now() + 14_400_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u4', name: 'ArtHouse London', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 432_000_000).toISOString(),
  },
  {
    id: '5',
    title: 'Nike Air Jordan 1 Retro High OG "Chicago" 1985',
    description: 'Dead-stock condition, size 10.5 US. Original box, hang tag intact.',
    category: { id: 'sneakers', name: 'Sneakers', slug: 'sneakers' },
    startingPrice: 800,
    bidIncrement: 25,
    buyNowPrice: 1200,
    depositAmount: 300,
    currentPrice: 950,
    totalBids: 18,
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 5_400_000).toISOString(),
    endTime: new Date(Date.now() + 10_800_000).toISOString(),
    originalEndTime: new Date(Date.now() + 10_800_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u5', name: 'KickVault', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 604_800_000).toISOString(),
  },
  {
    id: '6',
    title: 'Rolex Daytona 116500LN (2021)',
    description: 'Unworn with stickers, full set. Serial 2021.',
    category: { id: 'watches', name: 'Watches', slug: 'watches' },
    startingPrice: 15000,
    bidIncrement: 500,
    depositAmount: 5000,
    currentPrice: 22500,
    totalBids: 9,
    status: 'SCHEDULED',
    startTime: new Date(Date.now() + 86_400_000).toISOString(),
    endTime: new Date(Date.now() + 259_200_000).toISOString(),
    originalEndTime: new Date(Date.now() + 259_200_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u6', name: 'LuxTime', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 172_800_000).toISOString(),
  },
  {
    id: '7',
    title: 'Fender Stratocaster 1957 Sunburst',
    description: 'All original, matching serial headstock. Mild buckle rash on back.',
    category: { id: 'music', name: 'Music', slug: 'music' },
    startingPrice: 8000,
    bidIncrement: 250,
    depositAmount: 3000,
    currentPrice: 9500,
    totalBids: 6,
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 21_600_000).toISOString(),
    endTime: new Date(Date.now() + 43_200_000).toISOString(),
    originalEndTime: new Date(Date.now() + 43_200_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u7', name: 'Tone Chaser Guitars', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 864_000_000).toISOString(),
  },
  {
    id: '8',
    title: 'Leica M3 Double Stroke (1954)',
    description: 'First batch M3, fully CLA\'d, Summicron 50mm f/2 collapsible included.',
    category: { id: 'cameras', name: 'Cameras', slug: 'cameras' },
    startingPrice: 600,
    bidIncrement: 20,
    buyNowPrice: 950,
    depositAmount: 250,
    currentPrice: 780,
    totalBids: 11,
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 14_400_000).toISOString(),
    endTime: new Date(Date.now() + 21_600_000).toISOString(),
    originalEndTime: new Date(Date.now() + 21_600_000).toISOString(),
    extensionCount: 0,
    images: [],
    seller: { id: 'u8', name: 'FilmCamera Archive', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 345_600_000).toISOString(),
  },
  {
    id: '9',
    title: 'Pokémon Base Set Booster Box (1999, Sealed)',
    description: 'Factory sealed, BBCE authenticated. Grade-eligible stock.',
    category: { id: 'books', name: 'Books & Literature', slug: 'books' },
    startingPrice: 10000,
    bidIncrement: 500,
    depositAmount: 4000,
    currentPrice: 14500,
    totalBids: 34,
    status: 'COMPLETED',
    startTime: new Date(Date.now() - 172_800_000).toISOString(),
    endTime: new Date(Date.now() - 3_600_000).toISOString(),
    originalEndTime: new Date(Date.now() - 3_600_000).toISOString(),
    extensionCount: 0,
    completedAt: new Date(Date.now() - 3_600_000).toISOString(),
    images: [],
    seller: { id: 'u9', name: 'Sealed Grail', avatarUrl: undefined },
    createdAt: new Date(Date.now() - 1_296_000_000).toISOString(),
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
    await delay(300)
    // TODO: replace with real API call:
    // const query = new URLSearchParams()
    // if (params.keyword)                  query.set('keyword', params.keyword)
    // if (params.categorySlug)             query.set('categorySlug', params.categorySlug)
    // if (params.minPrice !== undefined)   query.set('minPrice', String(params.minPrice))
    // if (params.maxPrice !== undefined)   query.set('maxPrice', String(params.maxPrice))
    // if (params.endingSoon)               query.set('endingSoon', 'true')
    // if (params.buyNowAvailable)          query.set('buyNowAvailable', 'true')
    // if (params.sortBy)                   query.set('sortBy', params.sortBy)
    // query.set('page', String(params.page ?? 0))
    // query.set('size', String(params.size ?? 20))
    // const response = await fetch(`${API_URL}/api/v1/auctions/public?${query}`, { cache: 'no-store' })
    // if (!response.ok) return { items: [], total: 0, totalPages: 0, page: 0 }
    // const body: ApiResponse<PageResponse<AuctionBrowseItemResponse>> = await response.json()
    // return {
    //   items:      body.data.data.map(mapAuctionBrowseItem),
    //   total:      body.data.pagination.total,
    //   totalPages: body.data.pagination.totalPages,
    //   page:       body.data.pagination.page,
    // }

    // Mock: derive browse items from MOCK_DETAIL_RESPONSES
    let results: AuctionBrowseItemResponse[] = MOCK_DETAIL_RESPONSES.map((d) => ({
      id:              d.id,
      title:           d.title,
      primaryImageUrl: d.images[0]?.imageUrl ?? null,
      currentPrice:    d.currentPrice,
      totalBids:       d.totalBids,
      endTime:         d.endTime,
      status:          d.status,
      buyNowPrice:     d.buyNowPrice ?? null,
      categoryName:    d.category.name,
    }))

    if (params.keyword) {
      const kw = params.keyword.toLowerCase()
      results = results.filter((r) => r.title.toLowerCase().includes(kw))
    }
    if (params.categorySlug) {
      const slug = params.categorySlug.toLowerCase()
      results = results.filter((r) => r.categoryName.toLowerCase() === slug)
    }
    if (params.minPrice !== undefined) {
      results = results.filter((r) => r.currentPrice >= params.minPrice!)
    }
    if (params.maxPrice !== undefined) {
      results = results.filter((r) => r.currentPrice <= params.maxPrice!)
    }
    if (params.endingSoon) {
      results = results.filter((r) => {
        const ms = new Date(r.endTime).getTime() - Date.now()
        return ms > 0 && ms <= 24 * 60 * 60 * 1_000
      })
    }
    if (params.buyNowAvailable) {
      results = results.filter((r) => r.buyNowPrice !== null)
    }

    const sort = params.sortBy ?? 'END_TIME_ASC'
    results = [...results].sort((a, b) => {
      switch (sort) {
        case 'END_TIME_ASC':   return new Date(a.endTime).getTime() - new Date(b.endTime).getTime()
        case 'NEWLY_LISTED':   return new Date(b.endTime).getTime() - new Date(a.endTime).getTime()
        case 'PRICE_LOW_HIGH': return a.currentPrice - b.currentPrice
        case 'PRICE_HIGH_LOW': return b.currentPrice - a.currentPrice
        case 'MOST_BIDS':      return b.totalBids - a.totalBids
        default:               return 0
      }
    })

    const page       = params.page ?? 0
    const size       = params.size ?? 20
    const total      = results.length
    const totalPages = Math.max(1, Math.ceil(total / size))
    const items      = results.slice(page * size, (page + 1) * size).map(mapAuctionBrowseItem)

    return { items, total, totalPages, page }
  },

  /**
   * Fetch active auction counts per category.
   * Independent of search/filter state — always reflects full catalogue counts.
   */
  async getCategoryCounts(): Promise<CategoryCount[]> {
    await delay(100)
    // TODO: replace with real API call:
    // const response = await fetch(`${API_URL}/api/v1/auctions/public/category-counts`, { cache: 'no-store' })
    // if (!response.ok) return []
    // const body: ApiResponse<CategoryCountResponse[]> = await response.json()
    // return body.data.map(mapCategoryCount)

    // Mock: count by category from MOCK_DETAIL_RESPONSES
    const counts: Record<string, number> = {}
    for (const d of MOCK_DETAIL_RESPONSES) {
      const name = d.category.name
      counts[name] = (counts[name] ?? 0) + 1
    }
    return Object.entries(counts).map(([categoryName, count]) => ({ categoryName, count }))
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
