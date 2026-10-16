'use client'

import { useState }     from 'react'
import Link              from 'next/link'
import { Eye, Pencil, Trash2, MoreHorizontal } from 'lucide-react'
import { Button }        from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { SellerStatusBadge, type LiveTone } from './SellerStatusBadge'
import { DeleteAuctionDialog } from './DeleteAuctionDialog'
import { formatCurrency }     from '@/lib/format'
import { formatQueueClock, queueState } from '@/lib/home-queue'
import {
  canDeleteAuction, canEditAuction, formatEndedAgo, sellerGroupOf, sellerPrice, timeLeftShare,
  type SellerGroupKey,
} from '@/lib/seller-auctions'
import { SellerAuctionStatus } from '@/types/ui/seller.ui'
import type { SellerAuction }  from '@/types/ui/seller.ui'
import { cn } from '@/lib/utils'
import { resolveImageUrl } from '@/lib/image-utils'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { Skeleton } from '@/components/ui/skeleton'

const NO_IMAGE_BG = 'repeating-linear-gradient(135deg, #ECEDF2 0 1px, transparent 1px 8px), linear-gradient(180deg, #F4F4F8 0%, #ECEDF2 100%)'

/** Shared by the band header and every row so the columns line up. Below `md` a row is a two-line card. */
export const SELLER_ROW_GRID =
  'grid grid-cols-[48px_minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2.5 md:grid-cols-[48px_minmax(0,1fr)_112px_56px_148px_112px_140px]'

const TONE_TEXT: Record<LiveTone, string> = {
  live:     'text-foreground',
  warning:  'text-[var(--color-auction-ending-text)]',
  critical: 'text-[var(--color-auction-critical-text)]',
}

const TONE_BAR: Record<LiveTone, string> = {
  live:     'bg-[var(--color-auction-active-accent)]',
  warning:  'bg-[var(--color-auction-ending-accent)]',
  critical: 'bg-[var(--color-auction-critical-accent)]',
}

function toneOf(secondsLeft: number): LiveTone {
  const { tone } = queueState(secondsLeft)
  return tone === 'critical' ? 'critical' : tone === 'warning' ? 'warning' : 'live'
}

interface SellerAuctionRowProps {
  auction:    SellerAuction
  /** Milliseconds from `useNow`, shared by every row so the list ticks together. */
  now:        number
  onDeleted(): void
}

export function SellerAuctionRow({ auction, now, onDeleted }: SellerAuctionRowProps) {
  const [deleteOpen, setDeleteOpen] = useState(false)
  const group     = sellerGroupOf(auction.status)
  const editable  = canEditAuction(auction)
  const deletable = canDeleteAuction(auction)
  const isDraft   = auction.status === SellerAuctionStatus.Draft
  const resolvedImageUrl = resolveImageUrl(auction.primaryImageUrl ?? null)
  const isLoadingImage   = Boolean(auction.primaryImageUrl) && !resolvedImageUrl

  const secondsLeft = Math.max(0, Math.floor((auction.endsAt.getTime() - now) / 1000))
  const tone: LiveTone = group === 'live' ? toneOf(secondsLeft) : 'live'

  return (
    <>
      <div className={cn(
        SELLER_ROW_GRID,
        'relative border-t border-[var(--color-border-default)] px-4 py-3.5',
        'transition-colors duration-[var(--duration-tesla)] hover:bg-[var(--color-bg-elevated)]',
      )}>
        {/* Thumbnail */}
        <div className={cn(
          'size-12 shrink-0 overflow-hidden rounded-md border border-[var(--color-border-default)] bg-[var(--color-bg-elevated)]',
          group === 'ended' && 'grayscale-[60%]',
        )}>
          {resolvedImageUrl ? (
            <ImageThumbnail src={resolvedImageUrl} alt={auction.title} width={48} height={48} className="size-full object-cover" />
          ) : isLoadingImage ? (
            <Skeleton className="size-full rounded-none" />
          ) : (
            <div className="size-full" style={{ background: NO_IMAGE_BG }} />
          )}
        </div>

        {/* Title */}
        <div className="min-w-0">
          <Link
            href={`/seller/auctions/${auction.id}/manage`}
            className="block truncate text-sm font-medium leading-snug transition-colors duration-[var(--duration-tesla)] hover:text-[var(--color-text-brand)]"
          >
            {auction.title}
          </Link>
          <p className="mt-0.5 truncate text-xs text-muted-foreground">
            {auction.categoryName ? `${auction.categoryName} · ` : ''}Starting bid {formatCurrency(auction.startingPrice)}
          </p>
        </div>

        {/* Actions: second column on a phone, last on desktop */}
        <div className="relative flex items-center justify-end gap-2 max-md:col-start-3 max-md:row-start-1 md:col-start-7 md:row-start-1">
          <Button
            variant="outline"
            className="h-10 px-3.5 text-[13px]"
            render={<Link href={`/seller/auctions/${auction.id}/manage`} />}
            nativeButton={false}
          >
            {isDraft ? 'Finish setup' : 'Manage'}
          </Button>

          <DropdownMenu>
            <DropdownMenuTrigger
              render={<Button variant="ghost" className="size-10 p-0" aria-label={`More actions for ${auction.title}`} />}
            >
              <MoreHorizontal className="size-4" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="min-w-48 text-sm">
              {!isDraft && (
                <DropdownMenuItem render={<Link href={`/auctions/${auction.id}`} />}>
                  <Eye className="mr-2 size-3.5" />
                  View public page
                </DropdownMenuItem>
              )}
              {editable && (
                <DropdownMenuItem render={<Link href={`/seller/auctions/${auction.id}/manage`} />}>
                  <Pencil className="mr-2 size-3.5" />
                  Edit details
                </DropdownMenuItem>
              )}
              {deletable && (
                <DropdownMenuItem
                  className="text-[var(--color-danger-text)]"
                  onClick={() => setDeleteOpen(true)}
                >
                  <Trash2 className="mr-2 size-3.5" />
                  Delete auction
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {/* Price, bids, time, status. Four columns on desktop, a wrapping strip on a phone. */}
        <div className="flex flex-wrap items-end gap-x-6 gap-y-2 max-md:col-span-3 max-md:row-start-2 md:contents">
          <PriceCell auction={auction} group={group} />

          <div className="hidden text-right md:block">
            <span className="font-mono text-sm">{group === 'upcoming' ? '—' : auction.totalBids}</span>
          </div>

          <TimeCell auction={auction} group={group} now={now} secondsLeft={secondsLeft} tone={tone} />

          <div>
            <SellerStatusBadge status={auction.status} tone={tone} />
          </div>
        </div>
      </div>

      <DeleteAuctionDialog
        open={deleteOpen}
        auctionTitle={auction.title}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => { setDeleteOpen(false); onDeleted() }}
      />
    </>
  )
}

function PriceCell({ auction, group }: { readonly auction: SellerAuction; readonly group: SellerGroupKey }) {
  const hasBids = auction.totalBids > 0
  const text =
    group === 'ended' && !hasBids ? '—' : formatCurrency(sellerPrice(auction))
  const note =
    group === 'live' && !hasBids ? 'No bids yet'
    : auction.status === SellerAuctionStatus.Failed && hasBids ? 'Highest bid'
    : ''
  return (
    <div className="md:text-right">
      <p className="mb-0.5 text-[11px] text-muted-foreground md:hidden">
        {group === 'live' ? 'Current bid' : group === 'ended' ? 'Final price' : 'Starting bid'}
      </p>
      <p className="font-mono text-sm font-medium">{text}</p>
      {note && <p className="mt-0.5 text-xs text-muted-foreground">{note}</p>}
    </div>
  )
}

function TimeCell({ auction, group, now, secondsLeft, tone }: {
  readonly auction:     SellerAuction
  readonly group:       SellerGroupKey
  readonly now:         number
  readonly secondsLeft: number
  readonly tone:        LiveTone
}) {
  const label = group === 'live' ? 'Time left' : group === 'ended' ? 'Ended' : 'Starts in'
  let primary: string
  let note = ''
  let primaryClass = 'text-foreground'

  if (group === 'live') {
    primary = formatQueueClock(secondsLeft)
    primaryClass = TONE_TEXT[tone]
  } else if (group === 'ended') {
    primary = auction.endsAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    note = formatEndedAgo(auction.endsAt, now)
    primaryClass = 'text-muted-foreground'
  } else if (auction.status === SellerAuctionStatus.Draft) {
    primary = '—'
    note = 'Not published'
  } else {
    primary = formatQueueClock(Math.max(0, Math.floor((auction.startsAt.getTime() - now) / 1000)))
  }

  return (
    <div className="min-w-0">
      <p className="mb-0.5 text-[11px] text-muted-foreground md:hidden">{label}</p>
      <p className={cn('font-mono text-sm font-medium', primaryClass)}>{primary}</p>
      {group === 'live' && (
        <div className="mt-1.5 h-[3px] w-28 max-w-full rounded-sm bg-[var(--color-border-default)]">
          <div
            className={cn('h-full rounded-sm', TONE_BAR[tone])}
            style={{ width: `${timeLeftShare(auction, now)}%` }}
          />
        </div>
      )}
      {note && <p className="mt-0.5 text-xs text-muted-foreground">{note}</p>}
    </div>
  )
}
