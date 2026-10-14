/** media-service NotificationType names (backend enum). New backend values still parse; the UI maps them to "system". */
export const NOTIFICATION_SERVER_TYPES = [
  'USER_REGISTERED', 'OTP_VERIFICATION', 'BID_PLACED', 'BID_OUTBID', 'AUCTION_ENDING_SOON', 'AUCTION_WON',
  'AUCTION_LOST', 'AUCTION_CANCELLED', 'PAYMENT_REMINDER', 'PAYMENT_RECEIVED', 'DEPOSIT_REFUNDED',
  'DEPOSIT_FORFEITED', 'WATCHLIST_ITEM_STARTING', 'SYSTEM_ANNOUNCEMENT', 'FIRST_BID', 'NEW_BID',
  'AUCTION_EXTENDED', 'AUCTION_CREATED', 'PAYMENT_REQUIRED', 'PAYMENT_FAILED', 'AUCTION_UNSOLD',
] as const

export type NotificationServerType = (typeof NOTIFICATION_SERVER_TYPES)[number]

/** A stored notification, as listed by /api/v1/notifications and pushed on /user/queue/notifications. */
export interface NotificationDto {
  id: string
  /** media-service NotificationType, e.g. BID_OUTBID, NEW_BID, AUCTION_WON (string: tolerate new values) */
  type: string
  title: string
  message: string
  actionUrl: string | null
  auctionId: string | null
  metadata: Record<string, unknown> | null
  read: boolean
  /** ISO-8601 with offset, e.g. 2026-10-01T10:00:00Z */
  createdAt: string
}

/** STOMP envelope on /user/queue/notifications. `unreadCount` is the server's count after this notification. */
export interface UserNotificationMessage {
  type: 'NOTIFICATION'
  notification: NotificationDto
  unreadCount: number
}

/** Query for GET /api/v1/notifications (page is 0-based). */
export interface NotificationListParams {
  page?: number
  size?: number
  read?: boolean
  types?: string[]
  search?: string
}

export interface UnreadCountDto {
  count: number
}

export interface BulkUpdateDto {
  updated: number
}
