'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { PayNowDialog } from '@/components/payment/PayNowDialog'
import { useAuctionPreview } from '@/hooks/useAuctionPreview'
import { usePendingPayments } from '@/hooks/usePendingPayments'
import { formatCurrency } from '@/lib/format'
import { formatHoursLeft } from '@/lib/payment-utils'
import type { PendingPayment } from '@/types/ui/wallet.ui'

function PaymentDueRow({ hold }: { hold: PendingPayment }) {
  const preview = useAuctionPreview(hold.auctionId)
  const [open, setOpen] = useState(false)
  const title = preview?.title ?? 'Auction'

  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className="relative size-12 shrink-0 overflow-hidden rounded-md bg-muted">
        {preview?.imageUrl ? (
          <ImageThumbnail src={preview.imageUrl} alt={title} fill className="object-cover" sizes="48px" />
        ) : preview === undefined ? (
          <Skeleton className="absolute inset-0 size-full rounded-none" />
        ) : null}
      </div>

      <div className="min-w-0 flex-1">
        <Link
          href={`/auctions/${hold.auctionId}`}
          className="block truncate text-sm font-medium underline-offset-2 hover:underline"
        >
          {title}
        </Link>
        <p className="text-xs text-muted-foreground">
          {hold.expired ? 'Deadline passed' : formatHoursLeft(hold.hoursLeft)}
        </p>
      </div>

      <div className="flex flex-col items-end gap-1">
        <span className="font-mono text-sm font-medium">{formatCurrency(hold.remaining)}</span>
        <Button
          size="sm"
          variant="brand"
          disabled={hold.expired}
          aria-label={`Pay for ${title}`}
          onClick={() => setOpen(true)}
        >
          Pay
        </Button>
      </div>

      <PayNowDialog hold={hold} title={title} open={open} onOpenChange={setOpen} />
    </div>
  )
}

export function PaymentsDueList() {
  const { holds, hasFetched, loadFailed, refetch } = usePendingPayments()

  if (!hasFetched) {
    if (!loadFailed) return null
    return (
      <div className="flex items-center justify-between rounded-xl border bg-card px-4 py-3 text-sm">
        <span className="text-muted-foreground">Couldn&apos;t load your payments due.</span>
        <Button size="sm" variant="outline" onClick={() => void refetch()}>
          Retry
        </Button>
      </div>
    )
  }
  if (holds.length === 0) return null

  return (
    <section aria-labelledby="payments-due-heading" className="flex flex-col rounded-xl border bg-card">
      <h2 id="payments-due-heading" className="px-4 pb-1 pt-4 text-sm font-medium">
        Payments due
      </h2>
      <ul>
        {holds.map((hold, i) => (
          <li key={hold.auctionId} className={i > 0 ? 'border-t' : undefined}>
            <PaymentDueRow hold={hold} />
          </li>
        ))}
      </ul>
    </section>
  )
}
