import type { AuctionStatus } from '@/lib/design-tokens'

export interface AuctionBrowseItem {
  id:              string
  title:           string
  primaryImageUrl: string | null
  currentPrice:    number        // cents
  totalBids:       number
  endTime:         Date
  status:          AuctionStatus
  buyNowPrice:     number | null // cents, null if not available
  categoryName:    string
}
