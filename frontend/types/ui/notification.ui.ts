/** UI category: drives icon and colour. The raw backend type is kept on `serverType`. */
export type NotificationType =
  | 'outbid'
  | 'won'
  | 'lost'
  | 'ending_soon'
  | 'bid_placed'
  | 'payment_due'
  | 'payment'
  | 'payment_failed'
  | 'auction'
  | 'refund'
  | 'system'

export interface Notification {
  id:         string
  type:       NotificationType
  serverType: string
  title:      string
  message:    string
  isRead:     boolean
  createdAt:  Date
  auctionId?: string
  linkUrl?:   string
}
