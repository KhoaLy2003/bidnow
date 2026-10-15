'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PayNowDialog } from '@/components/payment/PayNowDialog'
import { usePendingPayments } from '@/hooks/usePendingPayments'
import { formatCurrency } from '@/lib/format'
import {
  PREPARING_POLL_MS,
  PREPARING_WINDOW_MS,
  derivePaymentPanelState,
  formatHoursLeft,
  isRecentWin,
} from '@/lib/payment-utils'

interface PaymentStatusBlockProps {
  auctionId:    string
  title:        string
  completedAt?: Date
}

export function PaymentStatusBlock({ auctionId, title, completedAt }: PaymentStatusBlockProps) {
  const { holds, paidIds, hasFetched, loadFailed, refetch } = usePendingPayments()
  const [dialogOpen, setDialogOpen] = useState(false)
  // Only a fresh win can still be waiting for its hold; an old win with no hold is simply "nothing due".
  const [windowOpen, setWindowOpen] = useState(() => isRecentWin(completedAt))

  useEffect(() => {
    if (!windowOpen) return
    const t = setTimeout(() => setWindowOpen(false), PREPARING_WINDOW_MS)
    return () => clearTimeout(t)
  }, [windowOpen])

  const { state, hold } = derivePaymentPanelState({
    holds, auctionId, paidIds, hasFetched, loadFailed, pollWindowOpen: windowOpen,
  })

  useEffect(() => {
    if (state !== 'preparing') return
    const i = setInterval(() => void refetch(), PREPARING_POLL_MS)
    return () => clearInterval(i)
  }, [state, refetch])

  switch (state) {
    case 'loading':
      return <Skeleton className="h-11 w-full" />

    case 'error':
      return (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-muted-foreground">We couldn&apos;t load your payment details.</p>
          <Button variant="outline" className="w-full" onClick={() => void refetch()}>
            Retry
          </Button>
        </div>
      )

    case 'preparing':
      return (
        <div className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
          <Loader2 className="size-4 animate-spin" />
          Preparing your payment…
        </div>
      )

    case 'due':
      if (!hold) return null
      return (
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">Remaining to pay</span>
            <span className="font-mono font-medium">{formatCurrency(hold.remaining)}</span>
          </div>
          <p className="text-xs text-muted-foreground">{formatHoursLeft(hold.hoursLeft)}</p>
          <Button variant="brand" className="h-11 w-full" onClick={() => setDialogOpen(true)}>
            Proceed to payment →
          </Button>
          <PayNowDialog hold={hold} title={title} open={dialogOpen} onOpenChange={setDialogOpen} />
        </div>
      )

    case 'expired':
      return (
        <p role="alert" className="text-sm text-[var(--color-danger-text)]">
          The payment deadline has passed. Your deposit may be forfeited.
        </p>
      )

    case 'paid':
      return (
        <div className="flex items-center gap-2 text-sm font-medium" role="status">
          <CheckCircle2 className="size-4 text-[var(--color-success-default)]" />
          Payment complete
        </div>
      )

    case 'none':
      return (
        <p className="text-sm text-muted-foreground">
          No payment due. See your{' '}
          <Link href="/wallet" className="underline underline-offset-2">
            wallet history
          </Link>{' '}
          for details.
        </p>
      )
  }
}
