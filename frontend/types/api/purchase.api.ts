/** Where a won auction stands in the winner's payment flow (auction-service `PurchasePaymentStatus`). */
export type PurchasePaymentStatus = 'PENDING_PAYMENT' | 'PAID' | 'FORFEITED'

/** `GET /api/v1/auctions/me/purchases` item. Timestamps are ISO-8601 strings with an offset. */
export interface PurchaseResponse {
  auctionId:       string
  title:           string
  primaryImageUrl: string | null
  finalPrice:      number
  /** Null only for a row that is not a purchase, which the endpoint does not return. */
  paymentStatus:   PurchasePaymentStatus | null
  completedAt:     string | null
  paymentDeadline: string | null
  paidAt:          string | null
}

export interface GetPurchasesParams {
  status?: PurchasePaymentStatus
  page?:   number
  size?:   number
}
