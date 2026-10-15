'use client'

import Link from 'next/link'
import { StatusBadge } from '@/components/auction/StatusBadge'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { resolveImageUrl } from '@/lib/image-utils'
import { cn } from '@/lib/utils'
import type { AuctionDetail } from '@/types/ui/auction.ui'

const NO_IMAGE_BG =
  'repeating-linear-gradient(135deg, #ECEDF2 0 1px, transparent 1px 8px), linear-gradient(180deg, #F4F4F8 0%, #ECEDF2 100%)'

function coverImage(auction: AuctionDetail): string | undefined {
  const cover =
    auction.images.find((i) => i.isPrimary) ??
    [...auction.images].sort((a, b) => a.displayOrder - b.displayOrder)[0]
  return resolveImageUrl(cover?.thumbnailUrl ?? cover?.imageUrl)
}

interface ManageHeaderProps {
  auction:   AuctionDetail
  /** Dims the cover when the auction is over. */
  muted?:    boolean
  /** Page actions, right-aligned (view, edit, delete, cancel). */
  children?: React.ReactNode
}

export function ManageHeader({ auction, muted, children }: ManageHeaderProps) {
  const cover = coverImage(auction)

  return (
    <div className="flex flex-col gap-4">
      <Link
        href="/seller/auctions"
        className="self-start text-[13px] text-muted-foreground transition-colors duration-[var(--duration-tesla)] hover:text-foreground"
      >
        ← My auctions
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-4">
          <div
            className={cn(
              'size-16 shrink-0 overflow-hidden rounded-md border border-[var(--color-border-default)]',
              muted && 'grayscale-[60%]',
            )}
            style={cover ? undefined : { background: NO_IMAGE_BG }}
          >
            {cover && (
              <ImageThumbnail src={cover} alt="" width={64} height={64} className="size-full object-cover" />
            )}
          </div>
          <div className="min-w-0">
            <h1 className="font-display text-[28px] font-medium leading-tight break-words">{auction.title}</h1>
            <div className="mt-2 flex flex-wrap items-center gap-2.5">
              <StatusBadge status={auction.status} />
              <span className="text-[13px] text-muted-foreground">{auction.categoryName}</span>
              <span className="font-mono text-xs text-muted-foreground">ID {auction.id.slice(0, 8)}</span>
            </div>
          </div>
        </div>

        {children && <div className="flex flex-wrap gap-2">{children}</div>}
      </div>
    </div>
  )
}
