/**
 * Format an amount in cents as a locale currency string.
 * e.g. formatCurrency(150000) → "$1,500"
 */
export function formatCurrency(
  cents: number,
  locale = 'en-US',
  currency = 'USD',
): string {
  const dollars = cents / 100
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: dollars % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(dollars)
}

const SECOND = 1000
const MINUTE = 60 * SECOND
const HOUR   = 60 * MINUTE
const DAY    = 24 * HOUR

/**
 * Format a date as a human-readable relative time string.
 * e.g. "2 minutes ago", "in 3 hours"
 */
export function formatRelativeTime(date: Date): string {
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  const diff = date.getTime() - Date.now()
  const absDiff = Math.abs(diff)

  if (absDiff < MINUTE)  return rtf.format(Math.round(diff / SECOND), 'second')
  if (absDiff < HOUR)    return rtf.format(Math.round(diff / MINUTE), 'minute')
  if (absDiff < DAY)     return rtf.format(Math.round(diff / HOUR),   'hour')
  return rtf.format(Math.round(diff / DAY), 'day')
}

/**
 * Format a seconds countdown into a display string.
 * < 1 hour:  "M:SS"  (e.g. "4:59", "0:23")
 * >= 1 hour: "H:MM:SS"
 * >= 1 day:  "Xd Xh"
 */
export function formatCountdown(seconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(seconds))

  if (totalSeconds >= 86400) {
    const days  = Math.floor(totalSeconds / 86400)
    const hours = Math.floor((totalSeconds % 86400) / 3600)
    return `${days}d ${hours}h`
  }

  const h  = Math.floor(totalSeconds / 3600)
  const m  = Math.floor((totalSeconds % 3600) / 60)
  const s  = totalSeconds % 60
  const ss = s.toString().padStart(2, '0')

  if (h > 0) {
    const mm = m.toString().padStart(2, '0')
    return `${h}:${mm}:${ss}`
  }

  return `${m}:${ss}`
}
