export enum SellerAuctionStatus {
  Draft      = 'draft',
  Active     = 'active',
  EndingSoon = 'ending-soon',
  Critical   = 'critical',
  Closed     = 'closed',
  Won        = 'won',
  Failed     = 'failed',
  Cancelled  = 'cancelled',
}

export interface SellerAuction {
  id:            string
  title:         string
  description:   string
  imageUrls:     string[]
  categoryId:    string
  categoryName?: string
  sellerId:      string
  startingPrice: number   // cents
  currentBid:    number   // cents
  bidIncrement:  number   // cents
  buyNowPrice?:  number   // cents
  depositAmount: number   // cents
  reservePrice?: number   // cents
  totalBids:     number
  watchers:      number
  startsAt:      Date
  endsAt:        Date
  createdAt:     Date
  status:        SellerAuctionStatus
  winnerId?:     string
  winnerName?:   string
}

export interface SellerBidItem {
  id:         string
  bidderName: string
  amount:     number   // cents
  placedAt:   Date
  isAutoBid:  boolean
  isWinning:  boolean
}

export interface AuditEvent {
  timestamp: Date
  message:   string
}

export interface CreateAuctionFormData {
  // Step 1 — Basics
  title:       string
  description: string
  categoryId:  string
  condition:   string
  tags:        string[]
  // Step 2 — Images
  images:      File[]
  // Step 3 — Pricing & Duration
  startingPrice: number   // cents
  bidIncrement:  number   // cents
  buyNowPrice:   number   // cents (0 = not set)
  depositAmount: number   // cents
  durationDays:  number
  endsAt?:       Date
}

export const INITIAL_FORM_DATA: CreateAuctionFormData = {
  title:         '',
  description:   '',
  categoryId:    '',
  condition:     '',
  tags:          [],
  images:        [],
  startingPrice: 0,
  bidIncrement:  0,
  buyNowPrice:   0,
  depositAmount: 0,
  durationDays:  3,
}
