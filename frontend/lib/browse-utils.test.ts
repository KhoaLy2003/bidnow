import { describe, expect, it } from 'vitest'
import {
  buildBrowseUrl,
  clearFilter,
  countActiveFilters,
  countSheetFilters,
  formatPriceRangeLabel,
  getActiveFilterChips,
  parseBrowseFilters,
  parseBrowseQuery,
  parseSort,
} from './browse-utils'
import { DEFAULT_FILTERS, DEFAULT_MAX_PRICE, DEFAULT_SORT } from '@/types/ui/browse.ui'

describe('parseBrowseFilters', () => {
  it('returns the defaults for empty params', () => {
    expect(parseBrowseFilters({})).toEqual(DEFAULT_FILTERS)
  })

  it('reads every filter, trimming the keyword', () => {
    expect(
      parseBrowseFilters({ q: '  leica ', category: 'cameras', minPrice: '250', maxPrice: '1000', endingSoon: 'true', buyNow: 'true' }),
    ).toEqual({
      q: 'leica',
      categorySlug: 'cameras',
      priceRange: { min: 250, max: 1000 },
      endingSoon: true,
      buyNow: true,
    })
  })

  it('never lets max fall below min', () => {
    expect(parseBrowseFilters({ minPrice: '500', maxPrice: '100' }).priceRange).toEqual({ min: 500, max: 500 })
  })

  it('ignores junk prices', () => {
    expect(parseBrowseFilters({ minPrice: 'abc', maxPrice: 'xyz' }).priceRange).toEqual({ min: 0, max: DEFAULT_MAX_PRICE })
  })
})

describe('parseSort / parseBrowseQuery', () => {
  it('falls back to the default sort', () => {
    expect(parseSort('nope')).toBe(DEFAULT_SORT)
    expect(parseSort(undefined)).toBe(DEFAULT_SORT)
    expect(parseSort('MOST_BIDS')).toBe('MOST_BIDS')
  })

  it('parses filters and sort together', () => {
    expect(parseBrowseQuery({ q: 'omega', sort: 'PRICE_LOW_HIGH' })).toEqual({
      filters: { ...DEFAULT_FILTERS, q: 'omega' },
      sort: 'PRICE_LOW_HIGH',
    })
  })
})

describe('buildBrowseUrl', () => {
  it('is bare for defaults', () => {
    expect(buildBrowseUrl(DEFAULT_FILTERS, DEFAULT_SORT)).toBe('/auctions')
  })

  it('keeps the search term and the other filters', () => {
    const url = buildBrowseUrl(
      { ...DEFAULT_FILTERS, q: 'vintage watch', categorySlug: 'watches', endingSoon: true, priceRange: { min: 100, max: 2000 } },
      'MOST_BIDS',
    )
    expect(url).toBe('/auctions?q=vintage+watch&category=watches&minPrice=100&maxPrice=2000&endingSoon=true&sort=MOST_BIDS')
  })

  it('drops a blank search and a no-maximum price', () => {
    expect(buildBrowseUrl({ ...DEFAULT_FILTERS, q: '   ', priceRange: { min: 0, max: DEFAULT_MAX_PRICE } }, DEFAULT_SORT)).toBe('/auctions')
  })

  it('adds the page only past the first', () => {
    expect(buildBrowseUrl(DEFAULT_FILTERS, DEFAULT_SORT, 0)).toBe('/auctions')
    expect(buildBrowseUrl({ ...DEFAULT_FILTERS, q: 'leica' }, DEFAULT_SORT, 2)).toBe('/auctions?q=leica&page=2')
  })

  it('round-trips through parseBrowseFilters', () => {
    const filters = { ...DEFAULT_FILTERS, q: 'sealed', buyNow: true, priceRange: { min: 0, max: 250 } }
    const url = new URL(buildBrowseUrl(filters, DEFAULT_SORT), 'http://x')
    expect(parseBrowseFilters(Object.fromEntries(url.searchParams))).toEqual(filters)
  })
})

describe('active filter counts', () => {
  it('counts search and category in the total but not in the sheet', () => {
    const filters = { ...DEFAULT_FILTERS, q: 'leica', categorySlug: 'cameras', endingSoon: true, priceRange: { min: 10, max: DEFAULT_MAX_PRICE } }
    expect(countActiveFilters(filters)).toBe(4)
    expect(countSheetFilters(filters)).toBe(2)
  })

  it('is zero for the defaults', () => {
    expect(countActiveFilters(DEFAULT_FILTERS)).toBe(0)
  })
})

describe('getActiveFilterChips / clearFilter', () => {
  const filters = {
    q: 'leica', categorySlug: 'cameras', priceRange: { min: 250, max: 1000 }, endingSoon: true, buyNow: true,
  }

  it('lists one chip per active filter in control order', () => {
    const chips = getActiveFilterChips(filters, () => 'Cameras')
    expect(chips.map((c) => c.key)).toEqual(['q', 'category', 'price', 'endingSoon', 'buyNow'])
    expect(chips.map((c) => c.label)).toEqual(['“leica”', 'Cameras', '$250 – $1,000', 'Ending soon', 'Buy Now'])
  })

  it('has no chips for the defaults', () => {
    expect(getActiveFilterChips(DEFAULT_FILTERS, () => '')).toEqual([])
  })

  it('resets exactly one filter', () => {
    expect(clearFilter(filters, 'price').priceRange).toEqual({ min: 0, max: DEFAULT_MAX_PRICE })
    expect(clearFilter(filters, 'price').q).toBe('leica')
    expect(clearFilter(filters, 'q').q).toBe('')
    expect(clearFilter(filters, 'category').categorySlug).toBe('all')
    expect(clearFilter(filters, 'endingSoon').endingSoon).toBe(false)
    expect(clearFilter(filters, 'buyNow').buyNow).toBe(false)
  })
})

describe('formatPriceRangeLabel', () => {
  it('describes open and closed ranges', () => {
    expect(formatPriceRangeLabel(0, DEFAULT_MAX_PRICE)).toBe('Price')
    expect(formatPriceRangeLabel(250, DEFAULT_MAX_PRICE)).toBe('$250 and up')
    expect(formatPriceRangeLabel(0, 1000)).toBe('Up to $1,000')
    expect(formatPriceRangeLabel(250, 1000)).toBe('$250 – $1,000')
  })
})
