import { describe, expect, it } from 'vitest'
import { formatMemberSince } from './format'

describe('formatMemberSince', () => {
  it('shows the full month name and the year', () => {
    expect(formatMemberSince(new Date(2025, 2, 15))).toBe('March 2025')
  })
})
