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

export interface AuctionDetailSeller {
  id: string
  name: string
  avatarUrl?: string
}

export interface AuctionImage {
  id: string
  imageUrl: string
  thumbnailUrl?: string
  displayOrder: number
  isPrimary: boolean
}

export interface AuctionDetail {
  id: string
  title: string
  description: string
  categoryId: string
  categoryName: string
  startingPrice: number
  bidIncrement: number
  buyNowPrice?: number
  depositAmount: number
  currentBid: number
  currentWinnerId?: string
  totalBids: number
  status: AuctionStatus
  startsAt: Date
  endsAt: Date
  originalEndAt: Date
  extensionCount: number
  completedAt?: Date
  winnerId?: string
  images: AuctionImage[]
  seller: AuctionDetailSeller | null
  createdAt: Date
}
