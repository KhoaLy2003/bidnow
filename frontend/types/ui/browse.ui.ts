export const SORT_OPTIONS = [
  'END_TIME_ASC',
  'NEWLY_LISTED',
  'PRICE_LOW_HIGH',
  'PRICE_HIGH_LOW',
  'MOST_BIDS',
] as const

export type SortOption = typeof SORT_OPTIONS[number]

export const SORT_LABELS: Record<SortOption, string> = {
  END_TIME_ASC:   'Ending Soonest',
  NEWLY_LISTED:   'Newly Listed',
  PRICE_LOW_HIGH: 'Price: Low to High',
  PRICE_HIGH_LOW: 'Price: High to Low',
  MOST_BIDS:      'Most Bids',
}

export const BROWSE_CATEGORIES = [
  { id: 'all',      label: 'All' },
  { id: 'Watches',  label: 'Watches' },
  { id: 'Music',    label: 'Music' },
  { id: 'Books',    label: 'Books' },
  { id: 'Sneakers', label: 'Sneakers' },
  { id: 'Cameras',  label: 'Cameras' },
  { id: 'Art',      label: 'Art' },
] as const

export const DEFAULT_MAX_PRICE = 100_000

export interface PriceRange {
  min: number
  max: number
}

export interface BrowseFilters {
  categoryName: string      // 'all' = no filter; matches AuctionBrowseItem.categoryName
  priceRange:   PriceRange
  endingSoon:   boolean
  buyNow:       boolean
}

export const DEFAULT_SORT: SortOption = 'END_TIME_ASC'

export const DEFAULT_FILTERS: BrowseFilters = {
  categoryName: 'all',
  priceRange:   { min: 0, max: DEFAULT_MAX_PRICE },
  endingSoon:   false,
  buyNow:       false,
}
