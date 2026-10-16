import type { PurchasePaymentStatus } from '@/types/api/purchase.api'

export interface Purchase {
  auctionId:       string
  title:           string
  imageUrl?:       string
  finalPrice:      number
  status:          PurchasePaymentStatus
  completedAt?:    Date
  paymentDeadline?: Date
  paidAt?:         Date
}

export interface PurchasePage {
  items:      Purchase[]
  page:       number
  total:      number
  totalPages: number
  hasNext:    boolean
  hasPrev:    boolean
}
