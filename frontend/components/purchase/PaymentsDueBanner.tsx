'use client'

import { Clock } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { fromCents, toCents } from '@/lib/money'
import { formatCurrency } from '@/lib/format'
import { formatHoursLeft } from '@/lib/payment-utils'
import type { PendingPayment } from '@/types/ui/wallet.ui'

interface PaymentsDueBannerProps {
  holds:         readonly PendingPayment[]
  onShowPending: () => void
}

/** A summary of what the wallet says is still owed. Renders nothing when nothing is due. */
export function PaymentsDueBanner({ holds, onShowPending }: PaymentsDueBannerProps) {
  if (holds.length === 0) return null

  const owed = fromCents(holds.reduce((sum, h) => sum + toCents(h.remaining), 0))
  const open = holds.filter((h) => !h.expired)
  const soonest = open.length > 0 ? Math.min(...open.map((h) => h.hoursLeft)) : null
  const count = `${holds.length} ${holds.length === 1 ? 'payment' : 'payments'} due`

  return (
    <section
      aria-label="Payments due"
      className="mb-5 flex flex-wrap items-center gap-4 rounded-[20px] border border-[var(--color-auction-ending-border)] bg-[var(--color-auction-ending-bg)] px-5 py-4"
    >
      <span
        aria-hidden="true"
        className="flex size-10 shrink-0 items-center justify-center rounded-full bg-[var(--color-auction-ending-border)]/40 text-[var(--color-auction-ending-text)]"
      >
        <Clock className="size-5" />
      </span>
      <div className="min-w-0 flex-1 basis-72">
        <p className="font-semibold text-[var(--color-auction-ending-text)]">
          {count} &middot; <span className="font-mono">{formatCurrency(owed)}</span> remaining
        </p>
        <p className="text-sm text-[var(--color-auction-ending-text)]">
          {soonest === null ? 'The deadline has passed.' : `Soonest deadline: ${formatHoursLeft(soonest).toLowerCase()}.`}{' '}
          Wins left unpaid after the deadline are forfeited.
        </p>
      </div>
      <Button variant="outline" onClick={onShowPending}>
        Show pending
      </Button>
    </section>
  )
}
