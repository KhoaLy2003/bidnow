export enum SellerAuctionStatus {
  Draft      = 'draft',
  Scheduled  = 'scheduled',
  Active     = 'active',
  Completed  = 'completed',
  Failed     = 'failed',
  Cancelled  = 'cancelled',
}

export interface SellerAuction {
  id:            string
  title:         string
  primaryImageUrl?: string
  categoryId:    string
  categoryName?: string
  sellerId:      string
  startingPrice: number   // dollars
  currentBid:    number   // dollars
  totalBids:     number
  startsAt:      Date
  endsAt:        Date
  createdAt:     Date
  status:        SellerAuctionStatus
}

export interface SellerBidItem {
  id:         string
  bidderName: string
  amount:     number   // dollars
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
  // Step 2 — Images
  images:      File[]
  // Step 3 — Pricing & Duration
  startingPrice: number   // dollars
  bidIncrement:  number   // dollars
  buyNowPrice:   number   // dollars (0 = not set)
  depositAmount: number   // dollars
  durationDays:  number
  startType:     'now' | 'scheduled'
  scheduledStartTime: Date | null
  endsAt?:       Date
}

export const INITIAL_FORM_DATA: CreateAuctionFormData = {
  title:         '',
  description:   '',
  categoryId:    '',
  images:        [],
  startingPrice: 0,
  bidIncrement:  0,
  buyNowPrice:   0,
  depositAmount: 0,
  durationDays:  3,
  startType:     'now',
  scheduledStartTime: null,
}
