import { DEFAULT_MAX_PRICE, DEFAULT_SORT, SORT_OPTIONS } from '@/types/ui/browse.ui'
import type { BrowseFilters, BrowseQuery, SortOption } from '@/types/ui/browse.ui'
import { formatCurrency } from '@/lib/format'
import { isPriceFiltered } from '@/lib/browse-price'

export function parseSort(raw: string | undefined): SortOption {
  return SORT_OPTIONS.includes(raw as SortOption) ? (raw as SortOption) : DEFAULT_SORT
}

export function parseBrowseFilters(
  params: Record<string, string | undefined>,
): BrowseFilters {
  const min = params.minPrice ? (Number(params.minPrice) || 0) : 0
  const max = params.maxPrice ? (Number(params.maxPrice) || DEFAULT_MAX_PRICE) : DEFAULT_MAX_PRICE

  return {
    q:            (params.q ?? '').trim(),
    categorySlug: params.category ?? 'all',
    priceRange:   { min: Math.max(0, min), max: Math.max(Math.max(0, min), max) },
    endingSoon:   params.endingSoon === 'true',
    buyNow:       params.buyNow === 'true',
  }
}

export function parseBrowseQuery(params: Record<string, string | undefined>): BrowseQuery {
  return { filters: parseBrowseFilters(params), sort: parseSort(params.sort) }
}

/**
 * Serialises BrowseFilters + sort + optional page to a /auctions URL string.
 * Omitting page resets to page 0. Only non-default values are included, so equal
 * filters always produce the same string and the URL doubles as a comparison key.
 */
export function buildBrowseUrl(
  filters: BrowseFilters,
  sort: SortOption,
  page?: number,
): string {
  const params = new URLSearchParams()

  const q = filters.q.trim()
  if (q) params.set('q', q)
  if (filters.categorySlug !== 'all') {
    params.set('category', filters.categorySlug)
  }
  if (filters.priceRange.min > 0) {
    params.set('minPrice', String(filters.priceRange.min))
  }
  if (filters.priceRange.max < DEFAULT_MAX_PRICE) {
    params.set('maxPrice', String(filters.priceRange.max))
  }
  if (filters.endingSoon) params.set('endingSoon', 'true')
  if (filters.buyNow)     params.set('buyNow', 'true')
  if (sort !== DEFAULT_SORT) params.set('sort', sort)
  if (page && page > 0)   params.set('page', String(page))

  const qs = params.toString()
  return `/auctions${qs ? '?' + qs : ''}`
}

export function hasPriceFilter(filters: BrowseFilters): boolean {
  return isPriceFiltered(filters.priceRange)
}

/** Active filters that live in the mobile filter sheet: price, ending soon, buy now. */
export function countSheetFilters(filters: BrowseFilters): number {
  return (hasPriceFilter(filters) ? 1 : 0) + (filters.endingSoon ? 1 : 0) + (filters.buyNow ? 1 : 0)
}

/** Counts every active (non-default) filter selection, search included. */
export function countActiveFilters(filters: BrowseFilters): number {
  return (
    countSheetFilters(filters) +
    (filters.q.trim() ? 1 : 0) +
    (filters.categorySlug !== 'all' ? 1 : 0)
  )
}

export type ActiveFilterKey = 'q' | 'category' | 'price' | 'endingSoon' | 'buyNow'

export interface ActiveFilterChip {
  key:   ActiveFilterKey
  label: string
}

/** "$250 – $1,000", "$250 and up" or "Up to $1,000". */
export function formatPriceRangeLabel(min: number, max: number): string {
  const hasMin = min > 0
  const hasMax = max < DEFAULT_MAX_PRICE
  if (hasMin && hasMax) return `${formatCurrency(min)} – ${formatCurrency(max)}`
  if (hasMin) return `${formatCurrency(min)} and up`
  if (hasMax) return `Up to ${formatCurrency(max)}`
  return 'Price'
}

/** One removable chip per active filter, in the order the controls appear on the page. */
export function getActiveFilterChips(
  filters: BrowseFilters,
  categoryName: (slug: string) => string,
): ActiveFilterChip[] {
  const chips: ActiveFilterChip[] = []
  const q = filters.q.trim()
  if (q) chips.push({ key: 'q', label: `“${q}”` })
  if (filters.categorySlug !== 'all') {
    chips.push({ key: 'category', label: categoryName(filters.categorySlug) })
  }
  if (hasPriceFilter(filters)) {
    chips.push({ key: 'price', label: formatPriceRangeLabel(filters.priceRange.min, filters.priceRange.max) })
  }
  if (filters.endingSoon) chips.push({ key: 'endingSoon', label: 'Ending soon' })
  if (filters.buyNow)     chips.push({ key: 'buyNow', label: 'Buy Now' })
  return chips
}

/** Returns the filters with one filter reset to its default. */
export function clearFilter(filters: BrowseFilters, key: ActiveFilterKey): BrowseFilters {
  switch (key) {
    case 'q':          return { ...filters, q: '' }
    case 'category':   return { ...filters, categorySlug: 'all' }
    case 'price':      return { ...filters, priceRange: { min: 0, max: DEFAULT_MAX_PRICE } }
    case 'endingSoon': return { ...filters, endingSoon: false }
    case 'buyNow':     return { ...filters, buyNow: false }
  }
}
