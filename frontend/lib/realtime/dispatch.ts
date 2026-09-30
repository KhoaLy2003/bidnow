import {
  REALTIME_MESSAGE_TYPES,
  type AuctionCancelledPayload,
  type AuctionEndedPayload,
  type AuctionExtendedPayload,
  type AuctionRealtimeMessage,
  type BidPlacedPayload,
  type OutbidPayload,
} from '@/types/api/realtime.api'

export interface RealtimeHandlers {
  bidPlaced(p: BidPlacedPayload): void
  extended(p: AuctionExtendedPayload): void
  ended(p: AuctionEndedPayload): void
  cancelled(p: AuctionCancelledPayload): void
  outbid(auctionId: string, p: OutbidPayload): void
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/** Validates the envelope only; payload fields are trusted per the media-service contract (spec §6). */
export function parseRealtimeMessage(body: string): AuctionRealtimeMessage | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(body)
  } catch {
    return null
  }
  if (!isRecord(parsed)) return null
  const { type, auctionId, payload } = parsed
  if (typeof type !== 'string' || typeof auctionId !== 'string' || !isRecord(payload)) return null
  if (!(REALTIME_MESSAGE_TYPES as ReadonlyArray<string>).includes(type)) return null
  return parsed as unknown as AuctionRealtimeMessage
}

/** Returns true when a handler ran. OUTBID arrives on the user queue for any auction. */
export function dispatchRealtimeMessage(
  msg: AuctionRealtimeMessage,
  auctionId: string,
  h: RealtimeHandlers,
): boolean {
  if (msg.type === 'OUTBID') {
    h.outbid(msg.auctionId, msg.payload)
    return true
  }
  if (msg.auctionId !== auctionId) return false
  switch (msg.type) {
    case 'BID_PLACED':        h.bidPlaced(msg.payload); return true
    case 'AUCTION_EXTENDED':  h.extended(msg.payload);  return true
    case 'AUCTION_ENDED':     h.ended(msg.payload);     return true
    case 'AUCTION_CANCELLED': h.cancelled(msg.payload); return true
  }
}
