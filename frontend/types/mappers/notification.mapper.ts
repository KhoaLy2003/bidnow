import type { NotificationDto, NotificationServerType } from '@/types/api/notification.api'
import type { Notification, NotificationType } from '@/types/ui/notification.ui'

const CATEGORY: Record<NotificationServerType, NotificationType> = {
  USER_REGISTERED:         'system',
  OTP_VERIFICATION:        'system',
  SYSTEM_ANNOUNCEMENT:     'system',
  BID_PLACED:              'bid_placed',
  FIRST_BID:               'bid_placed',
  NEW_BID:                 'bid_placed',
  BID_OUTBID:              'outbid',
  AUCTION_ENDING_SOON:     'ending_soon',
  AUCTION_WON:             'won',
  AUCTION_LOST:            'lost',
  AUCTION_CANCELLED:       'auction',
  AUCTION_EXTENDED:        'auction',
  AUCTION_CREATED:         'auction',
  AUCTION_UNSOLD:          'auction',
  WATCHLIST_ITEM_STARTING: 'auction',
  PAYMENT_REQUIRED:        'payment_due',
  PAYMENT_REMINDER:        'payment_due',
  PAYMENT_RECEIVED:        'payment',
  PAYMENT_FAILED:          'payment_failed',
  DEPOSIT_FORFEITED:       'payment_failed',
  DEPOSIT_REFUNDED:        'refund',
}

/** Server types that also pop a toast when they arrive live. */
const TOASTED: ReadonlySet<string> = new Set([
  'BID_OUTBID', 'AUCTION_WON', 'PAYMENT_REQUIRED', 'PAYMENT_REMINDER', 'AUCTION_ENDING_SOON',
])

export function toNotificationType(serverType: string): NotificationType {
  return (CATEGORY as Record<string, NotificationType | undefined>)[serverType] ?? 'system'
}

export function toNotification(dto: NotificationDto): Notification {
  return {
    id:         dto.id,
    type:       toNotificationType(dto.type),
    serverType: dto.type,
    title:      dto.title,
    message:    dto.message,
    isRead:     dto.read,
    createdAt:  new Date(dto.createdAt),
    auctionId:  dto.auctionId ?? undefined,
    linkUrl:    dto.actionUrl ?? undefined,
  }
}

export function shouldToast(notification: Notification): boolean {
  return TOASTED.has(notification.serverType)
}
