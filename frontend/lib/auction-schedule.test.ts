import { describe, expect, it } from 'vitest'
import {
  addDays, describeDuration, scheduleStart, validateSchedule,
  MAX_AUCTION_MS, MIN_AUCTION_MS,
} from './auction-schedule'

const NOW = new Date('2026-10-16T12:00:00Z').getTime()
const HOUR = 3_600_000
const DAY = 24 * HOUR

const at = (ms: number) => new Date(NOW + ms)

describe('validateSchedule: start now', () => {
  const now = { startType: 'now' as const, scheduledStartTime: null }

  it('requires an end time', () => {
    expect(validateSchedule({ ...now, endTime: null }, NOW).endTime).toMatch(/Pick when/)
  })

  it('accepts an end between one hour and 30 days from now', () => {
    expect(validateSchedule({ ...now, endTime: at(3 * DAY) }, NOW)).toEqual({})
    expect(validateSchedule({ ...now, endTime: at(MIN_AUCTION_MS) }, NOW)).toEqual({})
    expect(validateSchedule({ ...now, endTime: at(MAX_AUCTION_MS) }, NOW)).toEqual({})
  })

  it('rejects an end in the past or exactly now', () => {
    expect(validateSchedule({ ...now, endTime: at(-HOUR) }, NOW).endTime).toMatch(/future/)
    expect(validateSchedule({ ...now, endTime: at(0) }, NOW).endTime).toMatch(/future/)
  })

  it('rejects runs shorter than an hour or longer than 30 days', () => {
    expect(validateSchedule({ ...now, endTime: at(MIN_AUCTION_MS - 1) }, NOW).endTime).toMatch(/at least 1 hour/)
    expect(validateSchedule({ ...now, endTime: at(MAX_AUCTION_MS + 1) }, NOW).endTime).toMatch(/at most 30 days/)
  })
})

describe('validateSchedule: scheduled start', () => {
  it('needs a start time, and it must be in the future', () => {
    expect(validateSchedule({ startType: 'scheduled', scheduledStartTime: null, endTime: at(2 * DAY) }, NOW).startTime).toMatch(/select a start/)
    expect(validateSchedule({ startType: 'scheduled', scheduledStartTime: at(-HOUR), endTime: at(2 * DAY) }, NOW).startTime).toMatch(/in the future/)
  })

  it('measures the run from the scheduled start, not from now', () => {
    const ok = { startType: 'scheduled' as const, scheduledStartTime: at(10 * DAY), endTime: at(12 * DAY) }
    expect(validateSchedule(ok, NOW)).toEqual({})
    // 35 days from now is fine as long as the run itself is under 30 days.
    expect(validateSchedule({ ...ok, scheduledStartTime: at(33 * DAY), endTime: at(35 * DAY) }, NOW)).toEqual({})
    expect(validateSchedule({ ...ok, endTime: at(10 * DAY + 31 * DAY) }, NOW).endTime).toMatch(/at most 30 days/)
  })

  it('rejects an end at or before the start', () => {
    const base = { startType: 'scheduled' as const, scheduledStartTime: at(3 * DAY) }
    expect(validateSchedule({ ...base, endTime: at(2 * DAY) }, NOW).endTime).toMatch(/after the start/)
    expect(validateSchedule({ ...base, endTime: at(3 * DAY) }, NOW).endTime).toMatch(/after the start/)
  })

  it('does not judge the end against a start that is still missing', () => {
    const errs = validateSchedule({ startType: 'scheduled', scheduledStartTime: null, endTime: at(2 * DAY) }, NOW)
    expect(errs.endTime).toBeUndefined()
  })
})

describe('scheduleStart', () => {
  it('is the moment of publishing when starting now, the picked time when scheduled', () => {
    expect(scheduleStart({ startType: 'now', scheduledStartTime: null }, NOW)?.getTime()).toBe(NOW)
    const picked = at(DAY)
    expect(scheduleStart({ startType: 'scheduled', scheduledStartTime: picked }, NOW)).toBe(picked)
    expect(scheduleStart({ startType: 'scheduled', scheduledStartTime: null }, NOW)).toBeNull()
  })
})

describe('addDays', () => {
  it('adds whole days to a Date or a timestamp', () => {
    expect(addDays(NOW, 3).getTime()).toBe(NOW + 3 * DAY)
    expect(addDays(new Date(NOW), 7).getTime()).toBe(NOW + 7 * DAY)
  })
})

describe('describeDuration', () => {
  it('uses hours under a day and days from a day up', () => {
    expect(describeDuration(6 * HOUR)).toBe('6 hours')
    expect(describeDuration(HOUR)).toBe('1 hour')
    expect(describeDuration(DAY)).toBe('1 day')
    expect(describeDuration(36 * HOUR)).toBe('1.5 days')
    expect(describeDuration(3 * DAY)).toBe('3 days')
  })

  it('shows a dash for an empty or negative span', () => {
    expect(describeDuration(0)).toBe('—')
    expect(describeDuration(-HOUR)).toBe('—')
  })
})
