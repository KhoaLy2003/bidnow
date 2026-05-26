import type { AuctionStatus } from '@/lib/design-tokens'

export interface AuctionSeller {
  id:            string
  name:          string
  avatarUrl?:    string
  rating:        number
  totalAuctions: number
}

export interface Auction {
  id:            string
  title:         string
  description:   string
  imageUrls:     string[]
  categoryId:    string
  sellerId:      string
  winnerId?:     string
  startingPrice: number        // cents
  currentBid:    number        // cents
  reservePrice?: number        // cents
  buyNowPrice?:  number        // cents
  totalBids:     number
  watchers:      number
  startsAt:      Date
  endsAt:        Date
  status:        AuctionStatus
  isFeatured:    boolean
  condition:     string
  reserveMet:    boolean
  seller:        AuctionSeller
}

export interface Bid {
  id:        string
  auctionId: string
  bidderId:  string
  amount:    number            // cents
  placedAt:  Date
  isAutoBid: boolean
}

export interface BidHistoryItem extends Bid {
  bidderName:      string
  bidderAvatarUrl?: string
  isCurrentUser:   boolean
  isWinning:       boolean
}
