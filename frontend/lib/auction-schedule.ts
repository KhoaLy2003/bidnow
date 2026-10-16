export type StartType = 'now' | 'scheduled'

const HOUR_MS = 3_600_000
const DAY_MS  = 24 * HOUR_MS

/** The auction-service accepts runs from one hour to 30 days (same limits as the draft editor). */
export const MIN_AUCTION_MS = HOUR_MS
export const MAX_AUCTION_MS = 30 * DAY_MS

/** One-tap end times, counted from the start. */
export const END_PRESET_DAYS = [1, 3, 5, 7, 14, 30] as const

export interface ScheduleInput {
  startType:          StartType
  scheduledStartTime: Date | null
  endTime:            Date | null
}

export interface ScheduleErrors {
  startTime?: string
  endTime?:   string
}

/** When the auction opens: the picked time, or the moment of publishing. Null while a scheduled start is unset. */
export function scheduleStart(input: Pick<ScheduleInput, 'startType' | 'scheduledStartTime'>, now: number): Date | null {
  return input.startType === 'scheduled' ? input.scheduledStartTime : new Date(now)
}

export function validateSchedule(input: ScheduleInput, now: number = Date.now()): ScheduleErrors {
  const e: ScheduleErrors = {}

  if (input.startType === 'scheduled') {
    if (!input.scheduledStartTime) e.startTime = 'Please select a start date and time.'
    else if (input.scheduledStartTime.getTime() <= now) e.startTime = 'Scheduled start time must be in the future.'
  }

  if (!input.endTime) {
    e.endTime = 'Pick when the auction ends.'
    return e
  }

  const end   = input.endTime.getTime()
  const start = scheduleStart(input, now)

  if (end <= now) e.endTime = 'The end time must be in the future.'
  else if (start) {
    const duration = end - start.getTime()
    if (duration <= 0)                   e.endTime = 'The end time must be after the start time.'
    else if (duration < MIN_AUCTION_MS)  e.endTime = 'The auction must run for at least 1 hour.'
    else if (duration > MAX_AUCTION_MS)  e.endTime = 'The auction can run for at most 30 days.'
  }

  return e
}

/** `base` plus whole days, as a new Date. */
export function addDays(base: Date | number, days: number): Date {
  return new Date((typeof base === 'number' ? base : base.getTime()) + days * DAY_MS)
}

const DURATION_FORMAT = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 })

/** "6 hours", "1.5 days". A non-positive span is "—". */
export function describeDuration(ms: number): string {
  if (ms <= 0) return '—'
  const hours = ms / HOUR_MS
  if (hours < 24) return `${DURATION_FORMAT.format(hours)} ${hours === 1 ? 'hour' : 'hours'}`
  const days = hours / 24
  return `${DURATION_FORMAT.format(days)} ${days === 1 ? 'day' : 'days'}`
}
