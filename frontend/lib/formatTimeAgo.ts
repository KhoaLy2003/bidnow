const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function isPreviousCalendarDay(date: Date, now: Date): boolean {
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  return date.getFullYear() === yesterday.getFullYear()
    && date.getMonth() === yesterday.getMonth()
    && date.getDate() === yesterday.getDate()
}

/** "just now", "5 min ago", "2 hours ago", "yesterday", "Sep 28" (or "Sep 28, 2025" in another year). */
export function formatTimeAgo(date: Date, now: Date = new Date()): string {
  const diff = now.getTime() - date.getTime()
  if (diff < MINUTE) return 'just now'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} min ago`
  if (diff < DAY) {
    const hours = Math.floor(diff / HOUR)
    return hours === 1 ? '1 hour ago' : `${hours} hours ago`
  }
  if (isPreviousCalendarDay(date, now)) return 'yesterday'
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    ...(date.getFullYear() === now.getFullYear() ? {} : { year: 'numeric' }),
  })
}
