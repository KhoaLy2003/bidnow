import { describe, expect, it } from 'vitest'
import { formatTimeLeft } from './time-left'
import { timerStateFromSeconds } from './auction-utils'

describe('formatTimeLeft', () => {
  it('formats under an hour as mm:ss', () => {
    expect(formatTimeLeft(134)).toBe('02:14')
    expect(formatTimeLeft(59)).toBe('00:59')
  })

  it('formats an hour or more as "Xh MMm"', () => {
    expect(formatTimeLeft(3600)).toBe('1h 00m')
    expect(formatTimeLeft(4320)).toBe('1h 12m')
  })

  it('says Ended at or below zero', () => {
    expect(formatTimeLeft(0)).toBe('Ended')
    expect(formatTimeLeft(-5)).toBe('Ended')
  })
})

describe('timerStateFromSeconds', () => {
  it('is critical at one minute or less', () => {
    expect(timerStateFromSeconds(60)).toBe('critical')
    expect(timerStateFromSeconds(1)).toBe('critical')
  })

  it('is warning between one and five minutes', () => {
    expect(timerStateFromSeconds(61)).toBe('warning')
    expect(timerStateFromSeconds(300)).toBe('warning')
  })

  it('is normal beyond five minutes and once ended', () => {
    expect(timerStateFromSeconds(301)).toBe('normal')
    expect(timerStateFromSeconds(0)).toBe('normal')
  })
})
