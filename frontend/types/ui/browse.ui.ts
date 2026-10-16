export const SORT_OPTIONS = [
  'END_TIME_ASC',
  'NEWLY_LISTED',
  'PRICE_LOW_HIGH',
  'PRICE_HIGH_LOW',
  'MOST_BIDS',
] as const

export type SortOption = typeof SORT_OPTIONS[number]

export const SORT_LABELS: Record<SortOption, string> = {
  END_TIME_ASC:   'Ending soonest',
  NEWLY_LISTED:   'Newly listed',
  PRICE_LOW_HIGH: 'Price: low to high',
  PRICE_HIGH_LOW: 'Price: high to low',
  MOST_BIDS:      'Most bids',
}

/** Upper bound of the price filter. A max at this value means "no maximum" and is left out of the URL. */
export const DEFAULT_MAX_PRICE = 100_000

export interface PriceRange {
  min: number
  max: number
}

export interface BrowseFilters {
  q:            string      // search keyword, '' = none
  categorySlug: string      // 'all' = no filter; matches CategoryCount.slug
  priceRange:   PriceRange
  endingSoon:   boolean
  buyNow:       boolean
}

/** Everything the browse URL encodes except the page number. */
export interface BrowseQuery {
  filters: BrowseFilters
  sort:    SortOption
}

export const DEFAULT_SORT: SortOption = 'END_TIME_ASC'

export const DEFAULT_FILTERS: BrowseFilters = {
  q:            '',
  categorySlug: 'all',
  priceRange:   { min: 0, max: DEFAULT_MAX_PRICE },
  endingSoon:   false,
  buyNow:       false,
}
