/** STOMP messages pushed by media-service. Envelope `{ type, auctionId, payload }`; amounts in dollars; ISO timestamps. */
export interface BidPlacedPayload {
  bidId: string
  bidderId: string
  bidderName: string | null
  amount: number
  placedAt: string | null
  totalBids: number | null
  endTime: string | null
  antiSnipingTriggered: boolean
}

export interface AuctionExtendedPayload {
  previousEndTime: string | null
  newEndTime: string
  extensionCount: number | null
}

export interface AuctionEndedPayload {
  winnerId: string | null
  finalPrice: number | null
  endedAt: string | null
}

export interface AuctionCancelledPayload {
  reason: string | null
}

export type AuctionRealtimeMessage =
  | { type: 'BID_PLACED'; auctionId: string; payload: BidPlacedPayload }
  | { type: 'AUCTION_EXTENDED'; auctionId: string; payload: AuctionExtendedPayload }
  | { type: 'AUCTION_ENDED'; auctionId: string; payload: AuctionEndedPayload }
  | { type: 'AUCTION_CANCELLED'; auctionId: string; payload: AuctionCancelledPayload }

export const REALTIME_MESSAGE_TYPES: ReadonlyArray<AuctionRealtimeMessage['type']> = [
  'BID_PLACED', 'AUCTION_EXTENDED', 'AUCTION_ENDED', 'AUCTION_CANCELLED',
]
