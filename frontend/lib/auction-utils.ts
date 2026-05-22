import { AuctionStatus } from '@/lib/design-tokens'
import type { Auction } from '@/types/auction'

const WARNING_THRESHOLD_SECONDS  = 5 * 60   // 5 minutes
const CRITICAL_THRESHOLD_SECONDS = 60        // 1 minute

export type TimerState = 'normal' | 'warning' | 'critical'

/**
 * Derive the visual timer state based on how much time is left.
 * Always computed from the server-provided UTC endsAt — never local clock drift.
 */
export function deriveTimerState(endsAt: Date): TimerState {
  const secondsLeft = Math.floor((endsAt.getTime() - Date.now()) / 1000)
  if (secondsLeft <= 0)                            return 'normal'  // expired; let status handle it
  if (secondsLeft <= CRITICAL_THRESHOLD_SECONDS)   return 'critical'
  if (secondsLeft <= WARNING_THRESHOLD_SECONDS)    return 'warning'
  return 'normal'
}

/**
 * Derive the display AuctionStatus from an auction object.
 * Server-side terminal states (won, lost, outbid, closed) are preserved.
 * Active auctions are further classified by remaining time.
 */
export function getAuctionStatus(auction: Auction): AuctionStatus {
  const terminal: AuctionStatus[] = [
    AuctionStatus.Scheduled,
    AuctionStatus.Closed,
    AuctionStatus.Won,
    AuctionStatus.Lost,
    AuctionStatus.Outbid,
  ]

  if (terminal.includes(auction.status)) {
    return auction.status
  }

  const timerState = deriveTimerState(auction.endsAt)
  if (timerState === 'critical') return AuctionStatus.Critical
  if (timerState === 'warning')  return AuctionStatus.EndingSoon
  return AuctionStatus.Active
}
