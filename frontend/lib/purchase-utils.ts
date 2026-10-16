import { formatHoursLeft } from '@/lib/payment-utils'
import type { PurchasePaymentStatus } from '@/types/api/purchase.api'
import type { Purchase } from '@/types/ui/purchase.ui'

export type PurchaseFilter = 'all' | 'pending' | 'paid' | 'forfeited'

export const PURCHASE_FILTERS: readonly { value: PurchaseFilter; label: string }[] = [
  { value: 'all',       label: 'All' },
  { value: 'pending',   label: 'Pending payment' },
  { value: 'paid',      label: 'Paid' },
  { value: 'forfeited', label: 'Forfeited' },
]

const FILTER_TO_STATUS: Record<PurchaseFilter, PurchasePaymentStatus | undefined> = {
  all:       undefined,
  pending:   'PENDING_PAYMENT',
  paid:      'PAID',
  forfeited: 'FORFEITED',
}

/** The `status` query value the URL carries for a filter; anything unknown falls back to "all". */
export function parsePurchaseFilter(raw: string | null | undefined): PurchaseFilter {
  const value = raw?.toLowerCase()
  return PURCHASE_FILTERS.some((f) => f.value === value) ? (value as PurchaseFilter) : 'all'
}

export function filterToStatus(filter: PurchaseFilter): PurchasePaymentStatus | undefined {
  return FILTER_TO_STATUS[filter]
}

/** The URL carries a 1-based page; the API is zero-based. Garbage and values below 1 become the first page. */
export function parsePurchasePage(raw: string | null | undefined): number {
  const page = Number.parseInt(raw ?? '', 10)
  return Number.isFinite(page) && page >= 1 ? page - 1 : 0
}

/** `/purchases` with only the non-default parts in the query string. `pageIndex` is zero-based. */
export function buildPurchasesHref(filter: PurchaseFilter, pageIndex: number): string {
  const query = new URLSearchParams()
  if (filter !== 'all') query.set('status', filter)
  if (pageIndex > 0) query.set('page', String(pageIndex + 1))
  const qs = query.toString()
  return qs ? `/purchases?${qs}` : '/purchases'
}

export interface StatusPresentation {
  label:   string
  /** A `Badge` variant. */
  variant: 'ending-soon' | 'won' | 'critical'
}

export function statusPresentation(status: PurchasePaymentStatus): StatusPresentation {
  switch (status) {
    case 'PENDING_PAYMENT':
      return { label: 'Pending payment', variant: 'ending-soon' }
    case 'PAID':
      return { label: 'Paid', variant: 'won' }
    case 'FORFEITED':
      return { label: 'Forfeited', variant: 'critical' }
  }
}

const SHORT_DATE = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

export function formatShortDate(date: Date): string {
  return SHORT_DATE.format(date)
}

/** Whole hours until the deadline, floored and never negative; null when there is no deadline. */
export function hoursUntil(deadline: Date | undefined, now: number = Date.now()): number | null {
  if (!deadline) return null
  return Math.max(0, Math.floor((deadline.getTime() - now) / 3_600_000))
}

/** The second line of a row: what is left to do, or what happened and when. */
export function purchaseNote(purchase: Purchase, now: number = Date.now()): string {
  switch (purchase.status) {
    case 'PENDING_PAYMENT': {
      if (!purchase.paymentDeadline) return 'Payment details are on the way'
      if (purchase.paymentDeadline.getTime() <= now) return 'Deadline passed'
      return formatHoursLeft(hoursUntil(purchase.paymentDeadline, now) ?? 0)
    }
    case 'PAID':
      return purchase.paidAt ? `Paid ${formatShortDate(purchase.paidAt)}` : 'Paid'
    case 'FORFEITED':
      return purchase.paymentDeadline
        ? `Deadline passed ${formatShortDate(purchase.paymentDeadline)}`
        : 'Deadline passed'
  }
}

/** Treats a row as paid as soon as this session has paid it, before the list catches up. */
export function applyPaidIds(items: readonly Purchase[], paidIds: ReadonlySet<string>): Purchase[] {
  return items.map((p) =>
    p.status === 'PENDING_PAYMENT' && paidIds.has(p.auctionId) ? { ...p, status: 'PAID' as const } : p,
  )
}
