import { describe, expect, it } from 'vitest'
import { formatTimeAgo } from './formatTimeAgo'

// Local-time constructors keep the calendar-day rules independent of the machine's zone.
const NOW = new Date(2026, 9, 2, 12, 0, 0) // 2 Oct 2026 12:00 local

describe('formatTimeAgo', () => {
  it('says "just now" under a minute (and for small clock skew into the future)', () => {
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 59, 30), NOW)).toBe('just now')
    expect(formatTimeAgo(new Date(2026, 9, 2, 12, 0, 20), NOW)).toBe('just now')
  })

  it('counts minutes under an hour', () => {
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 55), NOW)).toBe('5 min ago')
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 1), NOW)).toBe('59 min ago')
  })

  it('counts hours under a day', () => {
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 0), NOW)).toBe('1 hour ago')
    expect(formatTimeAgo(new Date(2026, 9, 2, 10, 0), NOW)).toBe('2 hours ago')
    expect(formatTimeAgo(new Date(2026, 9, 1, 13, 0), NOW)).toBe('23 hours ago')
  })

  it('says "yesterday" for the previous calendar day beyond 24 hours', () => {
    expect(formatTimeAgo(new Date(2026, 9, 1, 9, 0), NOW)).toBe('yesterday')
  })

  it('shows a short date for older items, with the year only when it differs', () => {
    expect(formatTimeAgo(new Date(2026, 8, 28, 9, 0), NOW)).toBe('Sep 28')
    expect(formatTimeAgo(new Date(2025, 11, 31, 9, 0), NOW)).toBe('Dec 31, 2025')
  })
})
