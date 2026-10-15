import { fromCents, isLessThan, toCents } from '@/lib/money'
import { resolveImageUrl } from '@/lib/image-utils'
import type { AuctionImage } from '@/types/ui/auction.ui'
import type { PendingPayment } from '@/types/ui/wallet.ui'

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

export type PaymentPanelState = 'loading' | 'error' | 'preparing' | 'due' | 'expired' | 'paid' | 'none'

export interface PaymentPanelInput {
  holds:          readonly PendingPayment[]
  auctionId:      string
  paidIds:        ReadonlySet<string>
  hasFetched:     boolean
  loadFailed:     boolean
  pollWindowOpen: boolean
}

export function derivePaymentPanelState(
  input: PaymentPanelInput,
): { state: PaymentPanelState; hold: PendingPayment | null } {
  if (input.paidIds.has(input.auctionId)) return { state: 'paid', hold: null }

  const hold = input.holds.find((h) => h.auctionId === input.auctionId) ?? null
  if (hold) return { state: hold.expired ? 'expired' : 'due', hold }

  if (!input.hasFetched) return { state: input.loadFailed ? 'error' : 'loading', hold: null }
  if (input.pollWindowOpen) return { state: 'preparing', hold: null }
  return { state: 'none', hold: null }
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
