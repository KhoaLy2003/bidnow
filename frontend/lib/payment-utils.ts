import { fromCents, isLessThan, toCents } from '@/lib/money'
import { resolveImageUrl } from '@/lib/image-utils'
import type { AuctionImage } from '@/types/ui/auction.ui'
import type { PaymentHoldStatus } from '@/types/api/wallet.api'
import type { PaymentStatusLookup, PendingPayment } from '@/types/ui/wallet.ui'

/** How often the Won panel refetches while the wallet-service hold has not appeared yet. */
export const PREPARING_POLL_MS = 3_000
/** How long the Won panel keeps waiting for the hold before it gives up. */
export const PREPARING_WINDOW_MS = 30_000
/** An auction completed longer ago than this is not "just won": never show "preparing". */
export const RECENT_WIN_MS = 120_000

const PAYMENT_PUSH_TYPES: ReadonlySet<string> = new Set([
  'PAYMENT_REQUIRED',
  'PAYMENT_RECEIVED',
  'PAYMENT_FAILED',
])

/** True for notification types after which the winner's pending payments may have changed. */
export function isPaymentPush(serverType: string): boolean {
  return PAYMENT_PUSH_TYPES.has(serverType)
}

/**
 * How much more the winner must add to pay, or null when no top-up is needed:
 * the remainder is already held, or the available balance covers it. Compared in integer cents.
 */
export function getShortfall(
  hold: Pick<PendingPayment, 'fundsHeld' | 'remaining'>,
  availableBalance: number,
): number | null {
  if (hold.fundsHeld) return null
  if (!isLessThan(availableBalance, hold.remaining)) return null
  return fromCents(toCents(hold.remaining) - toCents(availableBalance))
}

/** Server-computed floored hours: 0 means under one hour. */
export function formatHoursLeft(hours: number): string {
  if (hours <= 0) return 'Less than 1 hour left'
  return hours === 1 ? '1 hour left' : `${hours} hours left`
}

/** Whether the auction completed recently enough that its hold may still be on its way. */
export function isRecentWin(completedAt: Date | undefined, now: number = Date.now()): boolean {
  if (!completedAt) return true
  return now - completedAt.getTime() < RECENT_WIN_MS
}

/** The stable "not asked yet" lookup. Selectors must return this constant, never a fresh `{ kind: 'idle' }`. */
export const IDLE_LOOKUP: PaymentStatusLookup = { kind: 'idle' }

export type PaymentPanelState =
  | 'loading' | 'error' | 'preparing' | 'due' | 'expired' | 'paid' | 'forfeited' | 'cancelled' | 'none'

export interface PaymentPanelInput {
  holds:          readonly PendingPayment[]
  auctionId:      string
  paidIds:        ReadonlySet<string>
  hasFetched:     boolean
  loadFailed:     boolean
  pollWindowOpen: boolean
  statusLookup:   PaymentStatusLookup
}

function stateForStatus(status: PaymentHoldStatus): PaymentPanelState {
  switch (status) {
    case 'COMPLETED':
      return 'paid'
    case 'FORFEITED':
      return 'forfeited'
    case 'CANCELLED':
      return 'cancelled'
    case 'PENDING_PAYMENT':
      return 'preparing' // a hold exists but the pending list has not caught up yet
    default:
      return 'none' // a status this build does not know: show the neutral state, not nothing
  }
}

/**
 * Precedence: the paid marker, then a pending hold (due / expired), then the pending list's own load state,
 * then what wallet-service says became of the hold.
 */
export function derivePaymentPanelState(
  input: PaymentPanelInput,
): { state: PaymentPanelState; hold: PendingPayment | null } {
  if (input.paidIds.has(input.auctionId)) return { state: 'paid', hold: null }

  const hold = input.holds.find((h) => h.auctionId === input.auctionId) ?? null
  if (hold) return { state: hold.expired ? 'expired' : 'due', hold }

  if (!input.hasFetched) return { state: input.loadFailed ? 'error' : 'loading', hold: null }

  const lookup = input.statusLookup
  switch (lookup.kind) {
    case 'idle':
    case 'loading':
      return { state: 'loading', hold: null }
    case 'failed':
      return { state: 'error', hold: null }
    case 'not-found':
      return { state: input.pollWindowOpen ? 'preparing' : 'none', hold: null }
    case 'found':
      return { state: stateForStatus(lookup.status), hold: null }
  }
}

export interface AuctionPreview {
  title:     string
  imageUrl?: string
}

/** Title plus the primary (else first by display order) image's thumbnail, for list rows. */
export function toAuctionPreview(auction: { title: string; images: AuctionImage[] }): AuctionPreview {
  const image =
    auction.images.find((i) => i.isPrimary) ??
    [...auction.images].sort((a, b) => a.displayOrder - b.displayOrder)[0]
  return {
    title:    auction.title,
    imageUrl: resolveImageUrl(image?.thumbnailUrl ?? image?.imageUrl),
  }
}
