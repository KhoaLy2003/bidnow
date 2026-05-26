export type NotificationType =
  | 'outbid'
  | 'won'
  | 'lost'
  | 'ending_soon'
  | 'bid_placed'
  | 'payment_due'
  | 'system'

export interface Notification {
  id:         string
  userId:     string
  type:       NotificationType
  title:      string
  message:    string
  isRead:     boolean
  createdAt:  Date
  auctionId?: string
  linkUrl?:   string
}
