import { AuctionStatus } from '@/lib/design-tokens'
import { fromCents, toCents } from '@/lib/money'
import type { BidEntry } from '@/types/ui/auction.ui'

// ── Which view the manage page shows ────────────────────────────────

export type ManageMode = 'draft' | 'live' | 'sold' | 'ended'

/**
 * Draft and Scheduled share the editor; every timer state is "live"; a closed auction with a winner is a
 * sale (paid, awaiting payment or forfeited); a closed auction without one ended with nothing to collect.
 */
export function getManageMode(status: AuctionStatus, winnerId?: string): ManageMode {
  switch (status) {
    case AuctionStatus.Draft:
    case AuctionStatus.Scheduled:
      return 'draft'
    case AuctionStatus.Active:
    case AuctionStatus.EndingSoon:
    case AuctionStatus.Critical:
      return 'live'
    default:
      return winnerId ? 'sold' : 'ended'
  }
}

/** Edit and delete exist only before the auction starts (server rule). */
export function canEditAuction(status: AuctionStatus): boolean {
  return status === AuctionStatus.Draft || status === AuctionStatus.Scheduled
}

/** Cancel exists for draft, scheduled and live auctions (server rule); ended ones are final. */
export function canCancelAuction(status: AuctionStatus): boolean {
  return canEditAuction(status) || getManageMode(status) === 'live'
}

// ── Bids ────────────────────────────────────────────────────────────

export function countBidders(bids: readonly BidEntry[]): number {
  return new Set(bids.map((b) => b.bidderId)).size
}

export interface FeedRow {
  bid:   BidEntry
  /** Rise over the next older loaded bid, in dollars; null for the oldest loaded bid. */
  delta: number | null
}

/** `bids` is newest first. The oldest loaded bid has no known predecessor, so no delta. */
export function withDeltas(bids: readonly BidEntry[]): FeedRow[] {
  return bids.map((bid, i) => {
    const older = bids[i + 1]
    return { bid, delta: older ? fromCents(toCents(bid.amount) - toCents(older.amount)) : null }
  })
}

// ── Draft editor validation ─────────────────────────────────────────

export interface DraftFields {
  title:         string
  description:   string
  categoryId:    string
  imageCount:    number
  startingPrice: number
  bidIncrement:  number
  /** 0 = no Buy It Now. */
  buyNowPrice:   number
  depositAmount: number
  startTime:     Date
  endTime:       Date
}

export type DraftField =
  | 'title' | 'description' | 'categoryId' | 'images'
  | 'startingPrice' | 'bidIncrement' | 'buyNowPrice' | 'depositAmount' | 'schedule'

export type DraftErrors = Partial<Record<DraftField, string>>

const HOUR_MS = 3_600_000
const MIN_DURATION_MS = HOUR_MS
const MAX_DURATION_MS = 30 * 24 * HOUR_MS

function hasAtMostTwoDecimals(dollars: number): boolean {
  return Math.abs(dollars * 100 - Math.round(dollars * 100)) < 1e-6
}

/** Deposit bounds in dollars: 5% rounded up to the cent, 20% rounded down. */
export function depositBounds(startingPrice: number): { min: number; max: number } {
  const cents = toCents(startingPrice)
  return { min: fromCents(Math.ceil((cents * 5) / 100)), max: fromCents(Math.floor((cents * 20) / 100)) }
}

export function validateDraft(f: DraftFields, now: number = Date.now()): DraftErrors {
  const e: DraftErrors = {}
  const title = f.title.trim()
  const description = f.description.trim()

  if (title.length < 5) e.title = 'Title must be at least 5 characters.'
  else if (title.length > 255) e.title = 'Title cannot exceed 255 characters.'

  if (description.length < 20) {
    e.description = `Description needs at least 20 characters (currently ${description.length}).`
  }
  if (!f.categoryId.trim()) e.categoryId = 'Choose a category.'
  if (f.imageCount < 1) e.images = 'Add at least one photo to publish.'

  if (!(f.startingPrice > 0)) e.startingPrice = 'Starting price must be greater than zero.'
  else if (!hasAtMostTwoDecimals(f.startingPrice)) e.startingPrice = 'Use at most 2 decimal places.'

  if (!(f.bidIncrement >= 0.01)) e.bidIncrement = 'Bid increment must be at least $0.01.'
  else if (!hasAtMostTwoDecimals(f.bidIncrement)) e.bidIncrement = 'Use at most 2 decimal places.'

  if (f.buyNowPrice > 0 && !(toCents(f.buyNowPrice) > toCents(f.startingPrice))) {
    e.buyNowPrice = 'Buy It Now must be greater than the starting price.'
  }

  if (f.startingPrice > 0) {
    const { min, max } = depositBounds(f.startingPrice)
    const deposit = toCents(f.depositAmount)
    if (deposit < toCents(min) || deposit > toCents(max)) {
      e.depositAmount = `Deposit must be between $${min.toFixed(2)} and $${max.toFixed(2)} (5–20% of the starting price).`
    }
  }

  const duration = f.endTime.getTime() - f.startTime.getTime()
  if (f.endTime.getTime() <= now) e.schedule = 'The end time must be in the future.'
  else if (duration < MIN_DURATION_MS) e.schedule = 'The auction must run for at least 1 hour.'
  else if (duration > MAX_DURATION_MS) e.schedule = 'The auction can run for at most 30 days.'

  return e
}

export type ChecklistKey = 'title' | 'description' | 'category' | 'images' | 'pricing' | 'schedule'

export interface ChecklistItem {
  key:   ChecklistKey
  label: string
  done:  boolean
}

export function draftChecklist(errors: DraftErrors): ChecklistItem[] {
  const pricing = !(errors.startingPrice || errors.bidIncrement || errors.buyNowPrice || errors.depositAmount)
  return [
    { key: 'title',       label: 'Title (5–255 characters)',    done: !errors.title },
    { key: 'description', label: 'Description (20+ characters)', done: !errors.description },
    { key: 'category',    label: 'Category selected',           done: !errors.categoryId },
    { key: 'images',      label: 'At least one photo',          done: !errors.images },
    { key: 'pricing',     label: 'Pricing complete and valid',  done: pricing },
    { key: 'schedule',    label: 'Schedule valid (1 hour–30 days)', done: !errors.schedule },
  ]
}
