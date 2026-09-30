import { apiFetch } from '@/lib/apiClient'
import { mapBidHistoryResponse } from '@/types/mappers/bid.mapper'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  ApiErrorResponse,
  BidHistoryResponse,
  PlaceBidRequest,
  PlaceBidResponse,
} from '@/types/api/bid.api'
import type { BidEntry } from '@/types/ui/auction.ui'

export const BID_PAGE_SIZE = 20

export interface BidPage {
  items:   BidEntry[]
  page:    number
  hasNext: boolean
  total:   number
}

export const EMPTY_BID_PAGE: BidPage = { items: [], page: 0, hasNext: false, total: 0 }

/** A non-2xx bidding API response; `body` is the backend ErrorResponse when it was JSON. */
export class BidRequestError extends Error {
  constructor(readonly status: number, readonly body: ApiErrorResponse | null) {
    super(body?.message ?? `Request failed with status ${status}`)
    this.name = 'BidRequestError'
  }
}

async function toBidRequestError(response: Response): Promise<BidRequestError> {
  let body: ApiErrorResponse | null = null
  try {
    body = (await response.json()) as ApiErrorResponse
  } catch {
    body = null
  }
  return new BidRequestError(response.status, body)
}

export const bidService = {
  async placeBid(request: PlaceBidRequest): Promise<PlaceBidResponse> {
    const response = await apiFetch('/api/v1/bids', {
      method: 'POST',
      body: JSON.stringify(request),
    })
    if (!response.ok) throw await toBidRequestError(response)
    const body: ApiResponse<PlaceBidResponse> = await response.json()
    return body.data
  },

  async getAuctionBids(
    auctionId: string,
    { page = 0, size = BID_PAGE_SIZE }: { page?: number; size?: number } = {},
  ): Promise<BidPage> {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    const response = await apiFetch(`/api/v1/bids/auction/${auctionId}?${query}`, { cache: 'no-store' })
    if (!response.ok) throw await toBidRequestError(response)
    const body: ApiResponse<PageResponse<BidHistoryResponse>> = await response.json()
    return {
      items:   body.data.data.map(mapBidHistoryResponse),
      page:    body.data.pagination.page,
      hasNext: body.data.pagination.hasNext,
      total:   body.data.pagination.total,
    }
  },
}
