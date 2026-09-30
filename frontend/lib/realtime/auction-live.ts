import { AuctionStatus } from '@/lib/design-tokens'
import { getAuctionStatus } from '@/lib/auction-utils'
import { toCents } from '@/lib/money'
import { UNKNOWN_BIDDER } from '@/types/mappers/bid.mapper'
import type { AuctionDetail, BidEntry } from '@/types/ui/auction.ui'
import type { PlaceBidResponse } from '@/types/api/bid.api'
import type {
  AuctionEndedPayload,
  AuctionExtendedPayload,
  BidPlacedPayload,
} from '@/types/api/realtime.api'

/**
 * Live view of one auction. Topics are not mutually ordered and REST snapshots can race
 * STOMP pushes, so every reducer is monotonic: price, bid count, end time and extension
 * count only grow, Closed is terminal, and bids are deduplicated by id.
 */
export interface LiveAuctionState {
  auctionId: string
  currentBid: number
  totalBids: number
  endsAt: Date
  status: AuctionStatus
  currentWinnerId?: string
  winnerId?: string
  extensionCount: number
  bids: BidEntry[]
  isOutbid: boolean
}

function later(a: Date, b: Date): Date {
  return b.getTime() > a.getTime() ? b : a
}

function maxDollars(a: number, b: number): number {
  return toCents(b) > toCents(a) ? b : a
}

function mergeStatus(prev: AuctionStatus, next: AuctionStatus): AuctionStatus {
  return prev === AuctionStatus.Closed || next === AuctionStatus.Closed ? AuctionStatus.Closed : next
}

export function upsertBids(existing: BidEntry[], incoming: BidEntry[]): BidEntry[] {
  const byId = new Map<string, BidEntry>()
  for (const b of existing) byId.set(b.id, b)
  for (const b of incoming) byId.set(b.id, b)
  return [...byId.values()].sort(
    (x, y) => y.placedAt.getTime() - x.placedAt.getTime() || toCents(y.amount) - toCents(x.amount),
  )
}

export function seedLiveState(auction: AuctionDetail, bids: BidEntry[]): LiveAuctionState {
  return {
    auctionId: auction.id,
    currentBid: auction.currentBid,
    totalBids: auction.totalBids,
    endsAt: auction.endsAt,
    status: auction.status,
    currentWinnerId: auction.currentWinnerId,
    winnerId: auction.winnerId,
    extensionCount: auction.extensionCount,
    bids: upsertBids([], bids),
    isOutbid: false,
  }
}

export function mergeSnapshot(prev: LiveAuctionState, next: LiveAuctionState): LiveAuctionState {
  const nextLeads = toCents(next.currentBid) >= toCents(prev.currentBid)
  return {
    ...prev,
    currentBid: maxDollars(prev.currentBid, next.currentBid),
    totalBids: Math.max(prev.totalBids, next.totalBids),
    endsAt: later(prev.endsAt, next.endsAt),
    status: mergeStatus(prev.status, next.status),
    currentWinnerId: nextLeads ? next.currentWinnerId ?? prev.currentWinnerId : prev.currentWinnerId,
    winnerId: next.winnerId ?? prev.winnerId,
    extensionCount: Math.max(prev.extensionCount, next.extensionCount),
    bids: upsertBids(prev.bids, next.bids),
  }
}

export function applyBidPlaced(s: LiveAuctionState, p: BidPlacedPayload, meId: string | null): LiveAuctionState {
  const raisesPrice = toCents(p.amount) > toCents(s.currentBid)
  const leads = toCents(p.amount) >= toCents(s.currentBid)
  let isOutbid = s.isOutbid
  if (meId !== null) {
    if (p.bidderId === meId && leads) isOutbid = false
    else if (raisesPrice && s.currentWinnerId === meId) isOutbid = true
  }
  const entry: BidEntry = {
    id: p.bidId,
    auctionId: s.auctionId,
    bidderId: p.bidderId,
    bidderName: p.bidderName ?? UNKNOWN_BIDDER,
    amount: p.amount,
    placedAt: p.placedAt ? new Date(p.placedAt) : new Date(),
    isAutoBid: false,
  }
  const existing = s.bids.find((b) => b.id === p.bidId)
  return {
    ...s,
    currentBid: maxDollars(s.currentBid, p.amount),
    totalBids: Math.max(s.totalBids, p.totalBids ?? 0),
    endsAt: p.endTime ? later(s.endsAt, new Date(p.endTime)) : s.endsAt,
    currentWinnerId: leads ? p.bidderId : s.currentWinnerId,
    // A repeat of a known bid keeps its original timestamp so replays are idempotent.
    bids: upsertBids(s.bids, [existing ? { ...entry, placedAt: existing.placedAt } : entry]),
    isOutbid,
  }
}

export function applyOwnBid(
  s: LiveAuctionState,
  r: PlaceBidResponse,
  me: { id: string; name: string },
): LiveAuctionState {
  if (r.auctionId !== s.auctionId) return s
  const leads = toCents(r.currentPrice) >= toCents(s.currentBid)
  return {
    ...s,
    currentBid: maxDollars(s.currentBid, r.currentPrice),
    totalBids: Math.max(s.totalBids, r.totalBids),
    endsAt: later(s.endsAt, new Date(r.endTime)),
    currentWinnerId: leads ? me.id : s.currentWinnerId,
    isOutbid: leads ? false : s.isOutbid,
    bids: upsertBids(s.bids, [{
      id: r.bidId, auctionId: s.auctionId, bidderId: me.id, bidderName: me.name,
      amount: r.amount, placedAt: new Date(r.placedAt), isAutoBid: false,
    }]),
  }
}

export function applyAuctionExtended(s: LiveAuctionState, p: AuctionExtendedPayload): LiveAuctionState {
  return {
    ...s,
    endsAt: later(s.endsAt, new Date(p.newEndTime)),
    extensionCount: Math.max(s.extensionCount, p.extensionCount ?? 0),
  }
}

export function applyAuctionEnded(s: LiveAuctionState, p: AuctionEndedPayload): LiveAuctionState {
  return {
    ...s,
    status: AuctionStatus.Closed,
    winnerId: p.winnerId ?? undefined,
    currentBid: p.finalPrice !== null ? maxDollars(s.currentBid, p.finalPrice) : s.currentBid,
  }
}

export function applyAuctionCancelled(s: LiveAuctionState): LiveAuctionState {
  return { ...s, status: AuctionStatus.Closed, winnerId: undefined }
}

export function applyOutbid(s: LiveAuctionState): LiveAuctionState {
  return { ...s, isOutbid: true }
}

export function appendOlderBids(s: LiveAuctionState, older: BidEntry[]): LiveAuctionState {
  return { ...s, bids: upsertBids(s.bids, older) }
}

/** Merges live state over the server snapshot and derives the viewer-specific status. */
export function deriveDisplayAuction(
  base: AuctionDetail,
  live: LiveAuctionState | null,
  userId: string | null,
): { auction: AuctionDetail; isOutbid: boolean } {
  const current = live && live.auctionId === base.id ? live : null
  const merged: AuctionDetail = current
    ? {
        ...base,
        currentBid: current.currentBid,
        totalBids: current.totalBids,
        endsAt: current.endsAt,
        status: current.status,
        currentWinnerId: current.currentWinnerId,
        winnerId: current.winnerId,
        extensionCount: current.extensionCount,
      }
    : base

  if (merged.status === AuctionStatus.Closed) {
    const won = userId !== null && merged.winnerId === userId
    return { auction: { ...merged, status: won ? AuctionStatus.Won : AuctionStatus.Closed }, isOutbid: false }
  }

  const isOutbid = !!current?.isOutbid && userId !== null && merged.currentWinnerId !== userId
  const status = isOutbid ? AuctionStatus.Outbid : getAuctionStatus(merged)
  return { auction: { ...merged, status }, isOutbid }
}
