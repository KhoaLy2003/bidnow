import { NOTIFICATION_SERVER_TYPES, type NotificationListParams } from '@/types/api/notification.api'

export const PAGE_SIZE = 20
const MAX_PAGE = 10000
const MAX_QUERY_LENGTH = 100

export interface NotificationFilters {
  tab:  'all' | 'unread'
  type: string | null
  q:    string
  /** 1-based (URL) */
  page: number
}

/** User-facing types offered in the filter (account/system types omitted). */
export const NOTIFICATION_TYPE_FILTERS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'BID_OUTBID',          label: 'Outbid' },
  { value: 'AUCTION_ENDING_SOON', label: 'Ending soon' },
  { value: 'AUCTION_WON',         label: 'Won' },
  { value: 'AUCTION_LOST',        label: 'Lost' },
  { value: 'PAYMENT_REQUIRED',    label: 'Payment required' },
  { value: 'PAYMENT_REMINDER',    label: 'Payment reminder' },
  { value: 'PAYMENT_RECEIVED',    label: 'Payment received' },
  { value: 'PAYMENT_FAILED',      label: 'Payment failed' },
  { value: 'DEPOSIT_REFUNDED',    label: 'Deposit refunded' },
  { value: 'NEW_BID',             label: 'New bid (seller)' },
  { value: 'FIRST_BID',           label: 'First bid (seller)' },
  { value: 'AUCTION_CREATED',     label: 'Auction live' },
  { value: 'AUCTION_EXTENDED',    label: 'Auction extended' },
  { value: 'AUCTION_CANCELLED',   label: 'Auction cancelled' },
  { value: 'AUCTION_UNSOLD',      label: 'Auction unsold' },
]

const KNOWN_TYPES: ReadonlySet<string> = new Set(NOTIFICATION_SERVER_TYPES)

export function parseNotificationFilters(params: URLSearchParams): NotificationFilters {
  const type = params.get('type')
  const page = Number.parseInt(params.get('page') ?? '', 10)
  return {
    tab:  params.get('tab') === 'unread' ? 'unread' : 'all',
    type: type && KNOWN_TYPES.has(type) ? type : null,
    q:    (params.get('q') ?? '').slice(0, MAX_QUERY_LENGTH),
    page: Number.isFinite(page) && page >= 1 ? Math.min(page, MAX_PAGE) : 1,
  }
}

export function toNotificationSearchParams(f: NotificationFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (f.tab === 'unread') params.set('tab', 'unread')
  if (f.type) params.set('type', f.type)
  if (f.q) params.set('q', f.q)
  if (f.page > 1) params.set('page', String(f.page))
  return params
}

export function toListParams(f: NotificationFilters, size: number): NotificationListParams {
  const params: NotificationListParams = { page: f.page - 1, size }
  if (f.tab === 'unread') params.read = false
  if (f.type) params.types = [f.type]
  const search = f.q.trim()
  if (search) params.search = search
  return params
}
