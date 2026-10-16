'use client'

import { useState } from 'react'
import Link from 'next/link'
import { ImageIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { PayNowDialog } from '@/components/payment/PayNowDialog'
import { formatCurrency } from '@/lib/format'
import { formatHoursLeft } from '@/lib/payment-utils'
import { formatShortDate, purchaseNote, statusPresentation } from '@/lib/purchase-utils'
import type { PendingPayment } from '@/types/ui/wallet.ui'
import type { Purchase } from '@/types/ui/purchase.ui'

interface PurchaseRowProps {
  purchase: Purchase
  /** The wallet's hold for this auction, when it is pending and the wallet has already opened it. */
  hold?:    PendingPayment
}

export function PurchaseRow({ purchase, hold }: PurchaseRowProps) {
  const [payOpen, setPayOpen] = useState(false)
  const { label, variant } = statusPresentation(purchase.status)
  const pending = purchase.status === 'PENDING_PAYMENT'
  const payable = pending && hold !== undefined
  // The wallet's server-computed countdown is the authority for a payable row.
  const note = payable ? (hold.expired ? 'Deadline passed' : formatHoursLeft(hold.hoursLeft)) : purchaseNote(purchase)
  // The wallet's remainder is exact (it nets the deposit); fall back to the final price until the hold appears.
  const amount = payable ? hold.remaining : purchase.finalPrice
  const amountCaption = payable ? `of ${formatCurrency(purchase.finalPrice)} due` : 'final price'

  return (
    <div className="flex flex-wrap items-center gap-4 px-5 py-4">
      <div className="relative size-16 shrink-0 overflow-hidden rounded-xl bg-muted">
        {purchase.imageUrl ? (
          <ImageThumbnail src={purchase.imageUrl} alt="" fill className="object-cover" sizes="64px" />
        ) : (
          <div className="flex size-full items-center justify-center text-muted-foreground" aria-hidden="true">
            <ImageIcon className="size-6" />
          </div>
        )}
      </div>

      <div className="min-w-0 flex-1 basis-64">
        <Link
          href={`/auctions/${purchase.auctionId}`}
          className="block truncate text-[15px] font-medium underline-offset-2 hover:underline"
        >
          {purchase.title}
        </Link>
        <p className="text-sm text-muted-foreground">
          {purchase.completedAt ? `Won ${formatShortDate(purchase.completedAt)}` : 'Won'}
        </p>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-4">
        <div className="flex min-w-40 flex-col items-end gap-1">
          <Badge variant={variant}>{label}</Badge>
          <span className="text-xs text-muted-foreground">{note}</span>
        </div>

        <div className="min-w-24 text-right">
          <div className="font-mono text-base font-medium">{formatCurrency(amount)}</div>
          <div className="text-xs text-muted-foreground">{amountCaption}</div>
        </div>

        <div className="flex w-20 justify-end">
          {payable ? (
            <>
              <Button
                variant="brand"
                disabled={hold.expired}
                aria-label={`Pay for ${purchase.title}`}
                onClick={() => setPayOpen(true)}
              >
                Pay
              </Button>
              <PayNowDialog hold={hold} title={purchase.title} open={payOpen} onOpenChange={setPayOpen} />
            </>
          ) : (
            <Button
              variant="link"
              size="sm"
              nativeButton={false}
              render={<Link href={`/auctions/${purchase.auctionId}`} aria-label={`View ${purchase.title}`} />}
            >
              View
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
