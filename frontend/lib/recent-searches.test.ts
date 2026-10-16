import { describe, expect, it } from 'vitest'
import { MAX_RECENT_SEARCHES, addRecentSearch, parseRecentSearches } from './recent-searches'

describe('addRecentSearch', () => {
  it('puts the newest term first', () => {
    expect(addRecentSearch(['leica'], 'omega')).toEqual(['omega', 'leica'])
  })

  it('moves a repeated term to the front without duplicating it', () => {
    expect(addRecentSearch(['omega', 'Leica'], 'leica')).toEqual(['leica', 'omega'])
  })

  it('ignores blank terms', () => {
    expect(addRecentSearch(['omega'], '   ')).toEqual(['omega'])
  })

  it('caps the list', () => {
    const full = Array.from({ length: MAX_RECENT_SEARCHES }, (_, i) => `t${i}`)
    const next = addRecentSearch(full, 'new')
    expect(next).toHaveLength(MAX_RECENT_SEARCHES)
    expect(next[0]).toBe('new')
  })
})

describe('parseRecentSearches', () => {
  it('returns [] for missing or corrupt data', () => {
    expect(parseRecentSearches(null)).toEqual([])
    expect(parseRecentSearches('{oops')).toEqual([])
    expect(parseRecentSearches('{"a":1}')).toEqual([])
  })

  it('keeps only non-empty strings', () => {
    expect(parseRecentSearches('["a", 1, "", "b"]')).toEqual(['a', 'b'])
  })
})
