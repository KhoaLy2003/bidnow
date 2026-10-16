import { describe, expect, it } from 'vitest'
import { auctionProgress, formatEndsAt, quickBidAmounts, splitCountdown } from './auction-display'

describe('splitCountdown', () => {
  it('shows hours, minutes and seconds under a day', () => {
    expect(splitCountdown(8077)).toEqual([
      { value: '02', unit: 'h' },
      { value: '14', unit: 'm' },
      { value: '37', unit: 's' },
    ])
  })

  it('shows days, hours and minutes from a day up', () => {
    expect(splitCountdown(86_400 + 3 * 3600 + 5 * 60 + 9)).toEqual([
      { value: '01', unit: 'd' },
      { value: '03', unit: 'h' },
      { value: '05', unit: 'm' },
    ])
  })

  it('clamps negatives to zero', () => {
    expect(splitCountdown(-10).map((u) => u.value)).toEqual(['00', '00', '00'])
  })
})

describe('auctionProgress', () => {
  const start = new Date(2026, 9, 10, 12, 0, 0)
  const end   = new Date(2026, 9, 12, 12, 0, 0)

  it('is the elapsed share of the running time', () => {
    expect(auctionProgress(start, end, new Date(2026, 9, 11, 12, 0, 0).getTime())).toBe(50)
  })

  it('clamps before the start and after the end', () => {
    expect(auctionProgress(start, end, start.getTime() - 1000)).toBe(0)
    expect(auctionProgress(start, end, end.getTime() + 1000)).toBe(100)
  })

  it('treats an empty window as finished', () => {
    expect(auctionProgress(end, end, end.getTime())).toBe(100)
  })
})

describe('formatEndsAt', () => {
  const now = new Date(2026, 9, 15, 18, 0, 0).getTime()

  it('says today for the same calendar day', () => {
    expect(formatEndsAt(new Date(2026, 9, 15, 21, 42), now)).toBe('Ends today · 9:42 PM')
  })

  it('says tomorrow for the next calendar day', () => {
    expect(formatEndsAt(new Date(2026, 9, 16, 9, 5), now)).toBe('Ends tomorrow · 9:05 AM')
  })

  it('uses the weekday and date further out', () => {
    expect(formatEndsAt(new Date(2026, 9, 17, 21, 42), now)).toBe('Ends Sat, Oct 17 · 9:42 PM')
  })
})

describe('quickBidAmounts', () => {
  it('offers the minimum, then two and five increments above it', () => {
    expect(quickBidAmounts(1260, 20)).toEqual([1260, 1300, 1360])
  })

  it('stays exact on fractional increments', () => {
    expect(quickBidAmounts(10.1, 0.1)).toEqual([10.1, 10.3, 10.6])
  })
})
