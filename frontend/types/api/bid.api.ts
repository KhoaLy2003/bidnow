/** Payloads of bidding-service `/api/v1/bids`. Amounts are dollars; timestamps are ISO-8601 with offset. */
export interface PlaceBidRequest {
  auctionId: string
  amount: number
}

export interface PlaceBidResponse {
  bidId: string
  auctionId: string
  amount: number
  placedAt: string
  currentPrice: number
  totalBids: number
  endTime: string
  extended: boolean
}

export interface BidHistoryResponse {
  id: string
  auctionId: string
  bidderId: string
  bidderName: string | null
  bidderAvatarUrl: string | null
  amount: number
  placedAt: string
  isAutoBid: boolean
  isAntiSnipingTriggered: boolean
}

export type BidErrorCode =
  | 'AUCTION_NOT_FOUND'
  | 'AUCTION_NOT_OPEN'
  | 'BID_TOO_LOW'
  | 'BID_OWN_AUCTION'
  | 'SERVICE_UNAVAILABLE'
  | 'BID_INSUFFICIENT_BALANCE'
  | 'WALLET_NOT_ACTIVE'
  | 'WALLET_NOT_FOUND'
  | 'DEPOSIT_LOCK_CLOSED'
  | 'INVALID_INPUT'

/** Common backend error body (`com.bidnow.common.dto.ErrorResponse`). */
export interface ApiErrorResponse {
  timestamp?: string
  status: number
  errorCode?: string
  message?: string
  path?: string
  errors?: Record<string, string>
}
