import { BROWSE_CATEGORIES, DEFAULT_FILTERS, DEFAULT_MAX_PRICE, DEFAULT_SORT } from '@/types/ui/browse.ui'
import type { BrowseFilters, SortOption } from '@/types/ui/browse.ui'

/**
 * Converts raw URL search params to a typed BrowseFilters object.
 * Category name is matched case-insensitively against BROWSE_CATEGORIES.
 */
export function parseBrowseFilters(
  params: Record<string, string | undefined>,
): BrowseFilters {
  const matchedCategory = BROWSE_CATEGORIES.find(
    (c) => c.id.toLowerCase() === (params.category ?? '').toLowerCase(),
  )

  return {
    categoryName: matchedCategory?.id ?? 'all',
    priceRange: {
      min: params.minPrice ? Number(params.minPrice) : 0,
      max: params.maxPrice ? Number(params.maxPrice) : DEFAULT_MAX_PRICE,
    },
    endingSoon: params.endingSoon === 'true',
    buyNow:     params.buyNow === 'true',
  }
}

/**
 * Serialises BrowseFilters + sort + optional page to a /auctions URL string.
 * Omitting page resets to page 0. Only non-default values are included.
 */
export function buildBrowseUrl(
  filters: BrowseFilters,
  sort: SortOption,
  page?: number,
): string {
  const params = new URLSearchParams()

  if (filters.categoryName !== 'all') {
    params.set('category', filters.categoryName.toLowerCase())
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

/**
 * Counts active (non-default) filter selections.
 * Used for the badge count on the mobile filter trigger button.
 */
export function countActiveFilters(filters: BrowseFilters): number {
  let count = 0
  if (filters.categoryName !== 'all') count++
  if (filters.priceRange.min > 0 || filters.priceRange.max < DEFAULT_MAX_PRICE) count++
  if (filters.endingSoon) count++
  if (filters.buyNow) count++
  return count
}
