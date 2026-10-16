import type { MyActiveBidsResponse } from '@/types/api/bid.api'
import type { MyActiveBid } from '@/types/ui/bid.ui'

/** The server already sorts soonest-ending first and returns per-tab counts; the UI derives counts from the list. */
export function mapMyActiveBids(dto: MyActiveBidsResponse): MyActiveBid[] {
  return dto.items.map((item) => ({
    auctionId:       item.auctionId,
    title:           item.title,
    primaryImageUrl: item.primaryImageUrl,
    currentPrice:    item.currentPrice,
    myHighestBid:    item.myHighestBid,
    totalBids:       item.totalBids,
    endTime:         new Date(item.endTime),
    lastBidAt:       new Date(item.lastBidAt),
    isWinning:       item.isWinning,
  }))
}
