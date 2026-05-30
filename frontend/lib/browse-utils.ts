import { AuctionStatus } from '@/lib/design-tokens'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import type { BrowseFilters, SortOption } from '@/types/ui/browse.ui'

export function applyFilters(
  items: AuctionBrowseItem[],
  filters: BrowseFilters,
): AuctionBrowseItem[] {
  return items.filter((item) => {
    if (filters.categoryName !== 'all' && item.categoryName !== filters.categoryName) return false
    if (item.currentPrice < filters.priceRange.min) return false
    if (item.currentPrice > filters.priceRange.max) return false
    if (filters.endingSoon) {
      const isEnding =
        item.status === AuctionStatus.EndingSoon ||
        item.status === AuctionStatus.Critical
      if (!isEnding) return false
    }
    if (filters.buyNow && item.buyNowPrice === null) return false
    return true
  })
}

export function applySort(items: AuctionBrowseItem[], sort: SortOption): AuctionBrowseItem[] {
  const copy = [...items]
  switch (sort) {
    case 'END_TIME_ASC':
      return copy.sort((a, b) => a.endTime.getTime() - b.endTime.getTime())
    case 'NEWLY_LISTED':
      return copy.sort((a, b) => b.endTime.getTime() - a.endTime.getTime())
    case 'PRICE_LOW_HIGH':
      return copy.sort((a, b) => a.currentPrice - b.currentPrice)
    case 'PRICE_HIGH_LOW':
      return copy.sort((a, b) => b.currentPrice - a.currentPrice)
    case 'MOST_BIDS':
      return copy.sort((a, b) => b.totalBids - a.totalBids)
    default:
      return copy
  }
}

export function deriveMaxPrice(items: AuctionBrowseItem[]): number {
  if (items.length === 0) return 10_000_00
  return Math.max(...items.map((a) => a.currentPrice))
}

export function countActiveFilters(filters: BrowseFilters, maxPrice: number): number {
  let count = 0
  if (filters.categoryName !== 'all') count++
  if (filters.priceRange.min > 0 || filters.priceRange.max < maxPrice) count++
  if (filters.endingSoon) count++
  if (filters.buyNow) count++
  return count
}

export function computeCategoryCounts(
  items: AuctionBrowseItem[],
): Record<string, number> {
  const counts: Record<string, number> = { all: items.length }
  for (const item of items) {
    counts[item.categoryName] = (counts[item.categoryName] ?? 0) + 1
  }
  return counts
}
