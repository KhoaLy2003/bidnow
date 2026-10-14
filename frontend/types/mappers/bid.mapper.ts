import type { BidHistoryResponse } from '@/types/api/bid.api'
import type { BidEntry, BidHistoryItem } from '@/types/ui/auction.ui'
import { toCents } from '@/lib/money'

export const UNKNOWN_BIDDER = 'Unknown bidder'

export function mapBidHistoryResponse(dto: BidHistoryResponse): BidEntry {
  return {
    id:              dto.id,
    auctionId:       dto.auctionId,
    bidderId:        dto.bidderId,
    bidderName:      dto.bidderName ?? UNKNOWN_BIDDER,
    bidderAvatarUrl: dto.bidderAvatarUrl ?? undefined,
    amount:          dto.amount,
    placedAt:        new Date(dto.placedAt),
    isAutoBid:       dto.isAutoBid,
  }
}

/** Bids on one auction strictly increase, so only the highest bid equals the current price. */
export function toBidHistoryItem(
  entry: BidEntry,
  ctx: { currentUserId: string | null; currentPrice: number },
): BidHistoryItem {
  return {
    ...entry,
    isCurrentUser: ctx.currentUserId !== null && entry.bidderId === ctx.currentUserId,
    isWinning:     toCents(entry.amount) === toCents(ctx.currentPrice),
  }
}
