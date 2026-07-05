import { AuctionStatus } from '@/lib/design-tokens'

const WARNING_THRESHOLD_SECONDS  = 5 * 60
const CRITICAL_THRESHOLD_SECONDS = 60

export type TimerState = 'normal' | 'warning' | 'critical'

export function deriveTimerState(endsAt: Date): TimerState {
  const secondsLeft = Math.floor((endsAt.getTime() - Date.now()) / 1000)
  if (secondsLeft <= 0)                            return 'normal'
  if (secondsLeft <= CRITICAL_THRESHOLD_SECONDS)   return 'critical'
  if (secondsLeft <= WARNING_THRESHOLD_SECONDS)    return 'warning'
  return 'normal'
}

export function getAuctionStatus(auction: { status: AuctionStatus; endsAt: Date }): AuctionStatus {
  const terminal: AuctionStatus[] = [
    AuctionStatus.Draft,
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
