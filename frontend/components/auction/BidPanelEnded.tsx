'use client'

import Link from 'next/link'
import { AuctionResultBanner } from './AuctionResultBanner'
import { AuctionStatus } from '@/lib/design-tokens'
import { PaymentStatusBlock } from '@/components/payment/PaymentStatusBlock'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface BidPanelEndedProps {
  auction: AuctionDetail
}

export function BidPanelEnded({ auction }: BidPanelEndedProps) {
  const { id, title, status, categoryId, currentBid, completedAt } = auction

  return (
    <div className="rounded-[20px] border bg-card overflow-hidden flex flex-col shadow-[0_1px_2px_rgb(20_22_26/0.04),0_12px_32px_rgb(20_22_26/0.06)]">
      <AuctionResultBanner
        status={status as AuctionStatus.Won | AuctionStatus.Lost | AuctionStatus.Closed}
        finalBid={currentBid}
      />

      {status === AuctionStatus.Won && (
        <div className="flex flex-col gap-3 p-[18px]">
          <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground">
            Congratulations! Next steps:
          </p>
          <ol className="flex flex-col gap-2 text-sm text-muted-foreground">
            <li className="flex gap-2">
              <span className="font-mono text-muted-foreground/50">01</span>
              <span>Complete payment within <span className="font-medium text-foreground">48 hours</span></span>
            </li>
            <li className="flex gap-2">
              <span className="font-mono text-muted-foreground/50">02</span>
              <span>Confirm shipping with seller</span>
            </li>
            <li className="flex gap-2">
              <span className="font-mono text-muted-foreground/50">03</span>
              <span>Track delivery in My Bids</span>
            </li>
          </ol>
          <PaymentStatusBlock auctionId={id} title={title} completedAt={completedAt} />
          <button
            type="button"
            className="w-full h-11 rounded px-4 text-sm font-medium bg-background border border-[var(--color-border-default)] text-foreground transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
            onClick={() => {}}
          >
            Contact seller
          </button>
        </div>
      )}

      {status === AuctionStatus.Lost && (
        <div className="flex flex-col gap-3 p-[18px]">
          <div className="flex flex-col gap-1">
            <p className="text-sm font-medium">You were outbid.</p>
            <p className="text-sm text-muted-foreground">Find similar auctions ending soon.</p>
          </div>
          <Link
            href={`/auctions?category=${categoryId}`}
            className="w-full h-11 rounded px-4 text-sm font-medium bg-background border border-foreground text-foreground flex items-center justify-center transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
          >
            Browse similar items →
          </Link>
          <p className="text-[10.5px] text-center text-muted-foreground">
            Or <span className="underline underline-offset-2 cursor-pointer">save this seller</span> to get notified next time.
          </p>
        </div>
      )}

      {status === AuctionStatus.Closed && (
        <div className="flex flex-col gap-3 p-[18px]">
          <div className="flex flex-col gap-1">
            <p className="text-sm font-medium">This auction has ended.</p>
            <p className="text-sm text-muted-foreground">Discover more items in this category.</p>
          </div>
          <Link
            href={`/auctions?category=${categoryId}`}
            className="w-full h-11 rounded px-4 text-sm font-medium bg-background border border-[var(--color-border-default)] text-foreground flex items-center justify-center transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
          >
            Browse similar items →
          </Link>
        </div>
      )}
    </div>
  )
}
