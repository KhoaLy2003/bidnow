import {
  REALTIME_MESSAGE_TYPES,
  type AuctionCancelledPayload,
  type AuctionEndedPayload,
  type AuctionExtendedPayload,
  type AuctionRealtimeMessage,
  type BidPlacedPayload,
} from '@/types/api/realtime.api'
import type { UserNotificationMessage } from '@/types/api/notification.api'

export interface RealtimeHandlers {
  bidPlaced(p: BidPlacedPayload): void
  extended(p: AuctionExtendedPayload): void
  ended(p: AuctionEndedPayload): void
  cancelled(p: AuctionCancelledPayload): void
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseJson(body: string): unknown {
  try {
    return JSON.parse(body)
  } catch {
    return null
  }
}

/** Validates the envelope only; payload fields are trusted per the media-service contract (spec §6). */
export function parseRealtimeMessage(body: string): AuctionRealtimeMessage | null {
  const parsed = parseJson(body)
  if (!isRecord(parsed)) return null
  const { type, auctionId, payload } = parsed
  if (typeof type !== 'string' || typeof auctionId !== 'string' || !isRecord(payload)) return null
  if (!(REALTIME_MESSAGE_TYPES as ReadonlyArray<string>).includes(type)) return null
  return parsed as unknown as AuctionRealtimeMessage
}

/** A `/user/queue/notifications` NOTIFICATION envelope (notification + server unread count), or null. */
export function parseUserNotificationMessage(body: string): UserNotificationMessage | null {
  const parsed = parseJson(body)
  if (!isRecord(parsed) || parsed.type !== 'NOTIFICATION' || !isRecord(parsed.notification)) return null
  if (typeof parsed.unreadCount !== 'number') return null
  const n = parsed.notification
  if (typeof n.id !== 'string' || typeof n.type !== 'string' || typeof n.message !== 'string') return null
  return parsed as unknown as UserNotificationMessage
}

/** Returns true when a handler ran. Messages for another auction are ignored. */
export function dispatchRealtimeMessage(
  msg: AuctionRealtimeMessage,
  auctionId: string,
  h: RealtimeHandlers,
): boolean {
  if (msg.auctionId !== auctionId) return false
  switch (msg.type) {
    case 'BID_PLACED':        h.bidPlaced(msg.payload); return true
    case 'AUCTION_EXTENDED':  h.extended(msg.payload);  return true
    case 'AUCTION_ENDED':     h.ended(msg.payload);     return true
    case 'AUCTION_CANCELLED': h.cancelled(msg.payload); return true
  }
}
