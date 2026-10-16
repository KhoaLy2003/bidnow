import { addDollars } from '@/lib/money'

const DAY_SECONDS = 86_400

export interface CountdownUnit {
  /** Zero-padded value, e.g. "07". */
  value: string
  /** Unit suffix: d, h, m or s. */
  unit:  'd' | 'h' | 'm' | 's'
}

/** Splits a seconds countdown into the units the bid panel shows: d/h/m past a day, h/m/s below. */
export function splitCountdown(seconds: number): CountdownUnit[] {
  const total = Math.max(0, Math.floor(seconds))
  const pad   = (n: number) => String(n).padStart(2, '0')

  const days    = Math.floor(total / DAY_SECONDS)
  const hours   = Math.floor((total % DAY_SECONDS) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const secs    = total % 60

  if (days > 0) {
    return [
      { value: pad(days),    unit: 'd' },
      { value: pad(hours),   unit: 'h' },
      { value: pad(minutes), unit: 'm' },
    ]
  }
  return [
    { value: pad(hours),   unit: 'h' },
    { value: pad(minutes), unit: 'm' },
    { value: pad(secs),    unit: 's' },
  ]
}

/** Percentage (0 to 100) of the auction's running time that has elapsed. */
export function auctionProgress(startsAt: Date, endsAt: Date, now: number): number {
  const total = endsAt.getTime() - startsAt.getTime()
  if (total <= 0) return 100
  const elapsed = (now - startsAt.getTime()) / total
  return Math.min(100, Math.max(0, elapsed * 100))
}

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

/** "Ends today · 9:42 PM", "Ends tomorrow · …" or "Ends Sat, Oct 17 · …", in the viewer's time zone. */
export function formatEndsAt(endsAt: Date, now: number): string {
  // Newer ICU builds put a narrow no-break space before AM/PM; normalise so output is stable.
  const time = endsAt
    .toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
    .replace(/ /g, ' ')

  const dayDiff = Math.round((startOfDay(endsAt) - startOfDay(new Date(now))) / (DAY_SECONDS * 1000))
  if (dayDiff === 0) return `Ends today · ${time}`
  if (dayDiff === 1) return `Ends tomorrow · ${time}`

  const date = endsAt.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })
  return `Ends ${date} · ${time}`
}

/** Quick-bid shortcuts: the minimum bid, then two and five increments above it. */
export function quickBidAmounts(minimum: number, increment: number): number[] {
  return [
    minimum,
    addDollars(minimum, increment * 2),
    addDollars(minimum, increment * 5),
  ]
}
