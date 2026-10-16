import { SellerAuctionStatus } from '@/types/ui/seller.ui'
import type { SellerAuction } from '@/types/ui/seller.ui'

/** The three bands of the "My auctions" list, in the order they are shown. */
export type SellerGroupKey = 'live' | 'upcoming' | 'ended'

/** Status chips above the list. `unsold` is every ended auction that did not sell. */
export type SellerStatusFilter = 'all' | 'live' | 'scheduled' | 'draft' | 'sold' | 'unsold'

export type SellerSort = 'time' | 'price' | 'bids'

export const SELLER_STATUS_FILTERS: readonly SellerStatusFilter[] = ['all', 'live', 'scheduled', 'draft', 'sold', 'unsold']
export const SELLER_SORTS: readonly SellerSort[] = ['time', 'price', 'bids']

export const SELLER_STATUS_LABELS: Record<SellerStatusFilter, string> = {
  all:       'All',
  live:      'Live',
  scheduled: 'Scheduled',
  draft:     'Drafts',
  sold:      'Sold',
  unsold:    'Unsold',
}

export const SELLER_SORT_LABELS: Record<SellerSort, string> = {
  time:  'Time',
  price: 'Highest price',
  bids:  'Most bids',
}

/** The Ended band shows this many rows until the seller asks for the rest. */
export const SELLER_ENDED_PREVIEW = 4

export interface SellerFilterState {
  query:      string
  /** Category id, '' = every category. */
  categoryId: string
  status:     SellerStatusFilter
  sort:       SellerSort
}

export const DEFAULT_SELLER_FILTERS: SellerFilterState = {
  query: '', categoryId: '', status: 'all', sort: 'time',
}

export interface SellerGroup {
  key:      SellerGroupKey
  label:    string
  /** What the order of the rows means, which depends on the chosen sort. */
  hint:     string
  /** Column captions, which differ per band. */
  columns:  { price: string; time: string; status: string }
  auctions: SellerAuction[]
}

const ENDED_STATUSES: readonly SellerAuctionStatus[] = [
  SellerAuctionStatus.Completed,
  SellerAuctionStatus.Failed,
  SellerAuctionStatus.Cancelled,
  SellerAuctionStatus.Rejected,
]

const UNSOLD_STATUSES: readonly SellerAuctionStatus[] = [
  SellerAuctionStatus.Failed,
  SellerAuctionStatus.Cancelled,
  SellerAuctionStatus.Rejected,
]

export function sellerGroupOf(status: SellerAuctionStatus): SellerGroupKey {
  if (status === SellerAuctionStatus.Active) return 'live'
  if (ENDED_STATUSES.includes(status)) return 'ended'
  return 'upcoming'
}

export function matchesStatusFilter(a: SellerAuction, filter: SellerStatusFilter): boolean {
  switch (filter) {
    case 'all':       return true
    case 'live':      return a.status === SellerAuctionStatus.Active
    case 'scheduled': return a.status === SellerAuctionStatus.Scheduled
    case 'draft':     return a.status === SellerAuctionStatus.Draft
    case 'sold':      return a.status === SellerAuctionStatus.Completed
    case 'unsold':    return UNSOLD_STATUSES.includes(a.status)
  }
}

/** Search (title, case-insensitive) and category. These narrow the chip counts too. */
export function filterBySearchAndCategory(
  auctions: SellerAuction[],
  query: string,
  categoryId: string,
): SellerAuction[] {
  const needle = query.trim().toLowerCase()
  return auctions.filter((a) =>
    (!needle || a.title.toLowerCase().includes(needle)) &&
    (!categoryId || a.categoryId === categoryId),
  )
}

/** Count per status chip within the search and category the seller has already chosen. */
export function statusCounts(auctions: SellerAuction[]): Record<SellerStatusFilter, number> {
  const counts = { all: 0, live: 0, scheduled: 0, draft: 0, sold: 0, unsold: 0 }
  for (const a of auctions) {
    for (const f of SELLER_STATUS_FILTERS) {
      if (matchesStatusFilter(a, f)) counts[f] += 1
    }
  }
  return counts
}

/** The price a seller thinks of: the leading bid, or the starting bid while nobody has bid. */
export function sellerPrice(a: SellerAuction): number {
  return a.totalBids > 0 ? a.currentBid : a.startingPrice
}

/** Default order inside a band: live by soonest close, upcoming by start (drafts last), ended by most recent. */
function byTime(group: SellerGroupKey) {
  return (x: SellerAuction, y: SellerAuction): number => {
    if (group === 'live')  return x.endsAt.getTime() - y.endsAt.getTime()
    if (group === 'ended') return y.endsAt.getTime() - x.endsAt.getTime()
    const xDraft = x.status === SellerAuctionStatus.Draft
    const yDraft = y.status === SellerAuctionStatus.Draft
    if (xDraft !== yDraft) return xDraft ? 1 : -1
    if (xDraft) return y.createdAt.getTime() - x.createdAt.getTime()
    return x.startsAt.getTime() - y.startsAt.getTime()
  }
}

function comparator(group: SellerGroupKey, sort: SellerSort) {
  const time = byTime(group)
  if (sort === 'price') return (x: SellerAuction, y: SellerAuction) => sellerPrice(y) - sellerPrice(x) || time(x, y)
  if (sort === 'bids')  return (x: SellerAuction, y: SellerAuction) => y.totalBids - x.totalBids || time(x, y)
  return time
}

const GROUP_DEFS: ReadonlyArray<{
  key: SellerGroupKey; label: string; hint: string; columns: SellerGroup['columns']
}> = [
  { key: 'live',     label: 'Live now', hint: 'Closing soonest first',  columns: { price: 'Current bid',  time: 'Time left', status: 'Status' } },
  { key: 'upcoming', label: 'Upcoming', hint: 'Scheduled and drafts',   columns: { price: 'Starting bid', time: 'Starts in', status: 'Status' } },
  { key: 'ended',    label: 'Ended',    hint: 'Most recent first',      columns: { price: 'Final price',  time: 'Ended',     status: 'Result' } },
]

const SORT_HINTS: Record<Exclude<SellerSort, 'time'>, string> = {
  price: 'Highest price first',
  bids:  'Most bids first',
}

/** One list, split into Live / Upcoming / Ended bands. Empty bands are dropped. */
export function buildSellerGroups(auctions: SellerAuction[], filters: SellerFilterState): SellerGroup[] {
  const matched = filterBySearchAndCategory(auctions, filters.query, filters.categoryId)
    .filter((a) => matchesStatusFilter(a, filters.status))

  return GROUP_DEFS
    .map((def): SellerGroup => ({
      key:      def.key,
      label:    def.label,
      hint:     filters.sort === 'time' ? def.hint : SORT_HINTS[filters.sort],
      columns:  def.columns,
      auctions: matched
        .filter((a) => sellerGroupOf(a.status) === def.key)
        .sort(comparator(def.key, filters.sort)),
    }))
    .filter((g) => g.auctions.length > 0)
}

/** The running auction that closes first, for the "Next to close" line. */
export function nextToClose(auctions: SellerAuction[]): SellerAuction | null {
  const live = auctions
    .filter((a) => a.status === SellerAuctionStatus.Active)
    .sort((x, y) => x.endsAt.getTime() - y.endsAt.getTime())
  return live[0] ?? null
}

/** "5 live · 2 scheduled · 1 draft · 6 ended", zero counts left out. */
export function summarizeSellerAuctions(auctions: SellerAuction[]): string {
  const live      = auctions.filter((a) => a.status === SellerAuctionStatus.Active).length
  const scheduled = auctions.filter((a) => a.status === SellerAuctionStatus.Scheduled).length
  const drafts    = auctions.filter((a) => a.status === SellerAuctionStatus.Draft).length
  const ended     = auctions.filter((a) => sellerGroupOf(a.status) === 'ended').length

  const parts = [
    live      && `${live} live`,
    scheduled && `${scheduled} scheduled`,
    drafts    && `${drafts} ${drafts === 1 ? 'draft' : 'drafts'}`,
    ended     && `${ended} ended`,
  ].filter(Boolean)

  const total = `${auctions.length} ${auctions.length === 1 ? 'auction' : 'auctions'}`
  return parts.length ? `${total} · ${parts.join(' · ')}` : total
}

/** Share of the auction's run still left, 2 to 100, for the thin bar under a live countdown. */
export function timeLeftShare(a: SellerAuction, nowMs: number): number {
  const total = a.endsAt.getTime() - a.startsAt.getTime()
  const left  = a.endsAt.getTime() - nowMs
  if (!(total > 0)) return left > 0 ? 100 : 0
  return Math.min(100, Math.max(left > 0 ? 2 : 0, (left / total) * 100))
}

/** "today", "1 day ago", "4 days ago" for the note under an ended auction's date. */
export function formatEndedAgo(endsAt: Date, nowMs: number): string {
  const days = Math.floor((nowMs - endsAt.getTime()) / (24 * 3600 * 1000))
  if (days < 1) return 'today'
  return days === 1 ? '1 day ago' : `${days} days ago`
}

export function canEditAuction(a: SellerAuction): boolean {
  return a.status === SellerAuctionStatus.Draft || a.status === SellerAuctionStatus.Scheduled
}

export function canDeleteAuction(a: SellerAuction): boolean {
  return canEditAuction(a) || a.status === SellerAuctionStatus.Active
}
