import { AuctionStatus } from '@/lib/design-tokens'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

/** The progress bar in the "Closing next" queue shows time left out of this window. */
export const QUEUE_BAR_WINDOW_SECONDS = 60 * 60

const CRITICAL_SECONDS = 60
const WARNING_SECONDS  = 5 * 60
const DAY_SECONDS      = 24 * 60 * 60

export type QueueTone = 'live' | 'warning' | 'critical' | 'closed'

export interface QueueState {
  tone:     QueueTone
  /** Written out so the state never relies on colour alone. */
  label:    string
  /** Width of the time bar, 0 to 100. */
  progress: number
}

export function queueState(secondsLeft: number): QueueState {
  if (secondsLeft <= 0) return { tone: 'closed', label: 'Closed', progress: 0 }

  const share = Math.min(1, secondsLeft / QUEUE_BAR_WINDOW_SECONDS)
  const progress = Math.max(2, share * 100)

  if (secondsLeft <= CRITICAL_SECONDS) return { tone: 'critical', label: 'Final minute', progress }
  if (secondsLeft <= WARNING_SECONDS)  return { tone: 'warning',  label: 'Ending soon',  progress }
  return { tone: 'live', label: 'Live', progress }
}

/** "01:12:07" under a day, "2d 04h" beyond. */
export function formatQueueClock(secondsLeft: number): string {
  const total = Math.max(0, Math.floor(secondsLeft))
  const pad = (n: number) => String(n).padStart(2, '0')

  if (total >= DAY_SECONDS) {
    const days  = Math.floor(total / DAY_SECONDS)
    const hours = Math.floor((total % DAY_SECONDS) / 3600)
    return `${days}d ${pad(hours)}h`
  }
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

/** Auctions that are still running, soonest-ending first. */
export function runningLots(items: AuctionBrowseItem[], now: number): AuctionBrowseItem[] {
  return items
    .filter((a) => a.status !== AuctionStatus.Closed && a.endTime.getTime() > now)
    .sort((a, b) => a.endTime.getTime() - b.endTime.getTime())
}

/** The first `limit` running lots by closing time, without repeating an auction. */
export function closingNext(items: AuctionBrowseItem[], now: number, limit: number): AuctionBrowseItem[] {
  const seen = new Set<string>()
  const unique = items.filter((a) => (seen.has(a.id) ? false : (seen.add(a.id), true)))
  return runningLots(unique, now).slice(0, limit)
}
