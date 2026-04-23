import { MOCK_AUCTIONS, MOCK_BIDS } from '@/lib/mock-data'
import type { Auction, BidHistoryItem } from '@/types/auction'

// Optional: Introduce a small artificial delay to simulate network latency
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export interface GetAuctionsParams {
  q?: string
  category?: string
  featured?: boolean
}

export const auctionService = {
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
