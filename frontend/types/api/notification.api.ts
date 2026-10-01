/** A stored notification, as listed by /api/v1/notifications and pushed on /user/queue/notifications. */
export interface NotificationDto {
  id: string
  /** media-service NotificationType, e.g. BID_OUTBID, NEW_BID, AUCTION_WON */
  type: string
  title: string
  message: string
  actionUrl: string | null
  auctionId: string | null
  metadata: Record<string, unknown> | null
  read: boolean
  /** ISO local date-time */
  createdAt: string
}

/** STOMP envelope on /user/queue/notifications. */
export interface UserNotificationMessage {
  type: 'NOTIFICATION'
  notification: NotificationDto
  unreadCount: number
}
