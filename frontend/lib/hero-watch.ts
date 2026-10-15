import { runningLots } from '@/lib/home-queue'
import type { LiveAuctionState } from '@/lib/realtime/auction-live'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

/** The LED ring on the hero watch is a 60-segment gauge for the final stretch of an auction. */
export const RING_SEGMENTS = 60
export const RING_WINDOW_SECONDS = 5 * 60

/** Mirrors `useCountdown`: warning at 5 minutes, critical at 1 minute. */
const WARNING_SECONDS  = 5 * 60
const CRITICAL_SECONDS = 60

export type RingTone = 'calm' | 'warning' | 'critical' | 'closed'

const TWO_PI = Math.PI * 2

/** Number of lit LED segments: all 60 until the final 5 minutes, then draining to 0. */
export function litSegments(secondsLeft: number): number {
  if (!Number.isFinite(secondsLeft)) return RING_SEGMENTS
  const clamped = Math.min(Math.max(secondsLeft, 0), RING_WINDOW_SECONDS)
  return Math.ceil((clamped / RING_WINDOW_SECONDS) * RING_SEGMENTS)
}

export function ringTone(secondsLeft: number): RingTone {
  if (!Number.isFinite(secondsLeft)) return 'calm'
  if (secondsLeft <= 0) return 'closed'
  if (secondsLeft <= CRITICAL_SECONDS) return 'critical'
  if (secondsLeft <= WARNING_SECONDS) return 'warning'
  return 'calm'
}

export interface HandAngles {
  /** Radians, clockwise from 12 o'clock. */
  second: number
  minute: number
  hour:   number
}

/**
 * Hands for a countdown: each hand moves clockwise as the auction runs down, and all three sit at 12
 * when the clock hits zero. `secondsLeft` may be fractional so the second hand can sweep smoothly.
 */
export function countdownHandAngles(secondsLeft: number): HandAngles {
  const s = Math.max(0, secondsLeft)
  const sec = s % 60
  const min = (s / 60) % 60
  const hr  = (s / 3600) % 12
  return {
    second: ((60 - sec) % 60) / 60 * TWO_PI,
    minute: ((60 - min) % 60) / 60 * TWO_PI,
    hour:   ((12 - hr) % 12) / 12 * TWO_PI,
  }
}

/** Hands showing the wall-clock time; used when the hero has no live lot to follow. */
export function wallClockHandAngles(now: Date): HandAngles {
  const sec = now.getSeconds() + now.getMilliseconds() / 1000
  const min = now.getMinutes() + sec / 60
  const hr  = (now.getHours() % 12) + min / 60
  return {
    second: sec / 60 * TWO_PI,
    minute: min / 60 * TWO_PI,
    hour:   hr / 12 * TWO_PI,
  }
}

/** Seeds the hero's live view of one lot from the browse snapshot the server rendered. */
export function seedHeroLot(item: AuctionBrowseItem): LiveAuctionState {
  return {
    auctionId:      item.id,
    currentBid:     item.currentPrice,
    totalBids:      item.totalBids,
    endsAt:         item.endTime,
    status:         item.status,
    extensionCount: 0,
    bids:           [],
    isOutbid:       false,
  }
}

/** The lot the hero follows: the soonest-ending auction that is still running. */
export function pickHeroLot(items: AuctionBrowseItem[], now: number): AuctionBrowseItem | null {
  return runningLots(items, now)[0] ?? null
}
