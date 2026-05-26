'use client'

import { useState }     from 'react'
import Image             from 'next/image'
import Link              from 'next/link'
import { Eye, Pencil, Trash2, MoreHorizontal, Trophy } from 'lucide-react'
import { Button }        from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { CountdownTimer }     from '@/components/auction/CountdownTimer'
import { SellerStatusBadge }  from './SellerStatusBadge'
import { DeleteAuctionDialog } from './DeleteAuctionDialog'
import { formatCurrency }     from '@/lib/format'
import { SellerAuctionStatus } from '@/types/ui/seller.ui'
import type { SellerAuction }  from '@/types/ui/seller.ui'
import { cn } from '@/lib/utils'
import { useSecureImage } from '@/hooks/useSecureImage'

function canEdit(auction: SellerAuction): boolean {
  if (auction.status === SellerAuctionStatus.Draft) return true
  if (auction.status === SellerAuctionStatus.Active && auction.startsAt > new Date()) return true
  return false
}

function canDelete(auction: SellerAuction): boolean {
  return canEdit(auction) && auction.totalBids === 0
}

// ────────────────────────────────────────────────────────────────
// Active-tab row
// ────────────────────────────────────────────────────────────────
interface ActiveRowProps {
  auction:     SellerAuction
  onDeleted(): void
}

export function ActiveAuctionRow({ auction, onDeleted }: ActiveRowProps) {
  const [deleteOpen, setDeleteOpen] = useState(false)
  const editable  = canEdit(auction)
  const deletable = canDelete(auction)
  const imageUrl  = auction.primaryImageUrl ?? null
  const resolvedImageUrl = useSecureImage(imageUrl)
  const isClosed  = [
    SellerAuctionStatus.Completed, SellerAuctionStatus.Failed, SellerAuctionStatus.Cancelled,
  ].includes(auction.status)

  return (
    <>
      <tr className="group border-b border-[var(--color-border-default)] transition-colors duration-[var(--duration-tesla)] hover:bg-[var(--color-bg-elevated)]">
        {/* Thumbnail */}
        <td className="w-14 py-3 pl-6 pr-2">
          <div className={cn(
            'size-12 shrink-0 overflow-hidden rounded-md border border-[var(--color-border-default)] bg-[var(--color-bg-elevated)]',
            isClosed && 'grayscale-[60%]',
          )}>
            {resolvedImageUrl
              ? <Image src={resolvedImageUrl} alt={auction.title} width={48} height={48} className="size-full object-cover" />
              : <div className="size-full" style={{ background: 'repeating-linear-gradient(135deg, #ECEDF2 0 1px, transparent 1px 8px), linear-gradient(180deg, #F4F4F8 0%, #ECEDF2 100%)' }} />
            }
          </div>
        </td>

        {/* Title */}
        <td className="min-w-0 py-3 pr-4">
          <Link
            href={`/seller/auctions/${auction.id}/manage`}
            className="block font-medium text-sm leading-snug line-clamp-1 hover:text-[var(--color-text-brand)] transition-colors duration-[var(--duration-tesla)]"
          >
            {auction.title}
          </Link>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Starting {formatCurrency(auction.startingPrice)} · {auction.totalBids > 0 ? 'Reserve met' : 'No bids yet'}
          </p>
        </td>

        {/* Current bid */}
        <td className="w-32 py-3 pr-4 text-right">
          <p className="font-mono font-medium text-sm">{formatCurrency(auction.currentBid)}</p>
        </td>

        {/* Bid count */}
        <td className="w-16 py-3 pr-4 text-right">
          <span className="font-mono text-sm">{auction.totalBids}</span>
        </td>

        {/* Countdown */}
        <td className="w-28 py-3 pr-4">
          {auction.status !== SellerAuctionStatus.Draft ? (
            <CountdownTimer endsAt={auction.endsAt} size="sm" />
          ) : (
            <span className="text-xs text-muted-foreground">—</span>
          )}
        </td>

        {/* Status */}
        <td className="w-28 py-3 pr-4">
          <SellerStatusBadge status={auction.status} />
        </td>

        {/* Actions */}
        <td className="w-24 py-3 pr-6">
          <div className="flex items-center justify-end gap-1">
            {/* Eye → manage page (public page unavailable with mock IDs) */}
            <Button
              variant="ghost"
              size="sm"
              className="h-7 px-2 text-xs"
              render={<Link href={`/seller/auctions/${auction.id}/manage`} />}
              nativeButton={false}
            >
              <Eye className="size-3.5" />
            </Button>

            {editable && (
              <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs"
                render={<Link href={`/seller/auctions/${auction.id}/manage`} />}
                nativeButton={false}
              >
                <Pencil className="size-3.5" />
              </Button>
            )}

            <DropdownMenu>
              <DropdownMenuTrigger
                render={
                  <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" />
                }
              >
                <MoreHorizontal className="size-3.5" />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="text-sm">
                <DropdownMenuItem
                  render={<Link href={`/auctions/${auction.id}`} />}
                >
                  View public page
                </DropdownMenuItem>
                {deletable && (
                  <DropdownMenuItem
                    className="text-[var(--color-danger-text)]"
                    onClick={() => setDeleteOpen(true)}
                  >
                    <Trash2 className="mr-2 size-3.5" />
                    Delete
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </td>
      </tr>

      <DeleteAuctionDialog
        open={deleteOpen}
        auctionTitle={auction.title}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => { setDeleteOpen(false); onDeleted() }}
      />
    </>
  )
}

// ────────────────────────────────────────────────────────────────
// Historical-tab row
// ────────────────────────────────────────────────────────────────
interface HistoricalRowProps {
  auction: SellerAuction
}

export function HistoricalAuctionRow({ auction }: HistoricalRowProps) {
  const imageUrl = auction.primaryImageUrl ?? null
  const resolvedImageUrl = useSecureImage(imageUrl)
  
  return (
    <tr className="group border-b border-[var(--color-border-default)] transition-colors duration-[var(--duration-tesla)] hover:bg-[var(--color-bg-elevated)]">
      {/* Thumbnail */}
      <td className="w-14 py-3 pl-6 pr-2">
        <div className="size-12 shrink-0 overflow-hidden rounded-md border border-[var(--color-border-default)] bg-[var(--color-bg-elevated)] grayscale-[60%]">
          {resolvedImageUrl
            ? <Image src={resolvedImageUrl} alt={auction.title} width={48} height={48} className="size-full object-cover" />
            : <div className="size-full" style={{ background: 'repeating-linear-gradient(135deg, #ECEDF2 0 1px, transparent 1px 8px), linear-gradient(180deg, #F4F4F8 0%, #ECEDF2 100%)' }} />
          }
        </div>
      </td>

      {/* Title */}
      <td className="min-w-0 py-3 pr-4">
        <p className="font-medium text-sm leading-snug line-clamp-1">{auction.title}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{auction.totalBids} bids · {auction.totalBids > 1 ? auction.totalBids + ' bidders' : '—'}</p>
      </td>

      {/* Final price */}
      <td className="w-32 py-3 pr-4 text-right">
        <span className="font-mono font-medium text-sm">
          {auction.currentBid > 0 ? formatCurrency(auction.currentBid) : '—'}
        </span>
      </td>

      {/* Winner */}
      <td className="w-36 py-3 pr-4">
        <span className="text-sm text-muted-foreground">—</span>
      </td>

      {/* End date */}
      <td className="w-28 py-3 pr-4">
        <span className="text-sm text-muted-foreground">
          {auction.endsAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
        </span>
      </td>

      {/* Outcome */}
      <td className="w-24 py-3 pr-4">
        <SellerStatusBadge status={auction.status} />
      </td>

      {/* Actions */}
      <td className="w-20 py-3 pr-6">
        <div className="flex justify-end">
          <Button
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-xs"
            render={<Link href={`/auctions/${auction.id}`} />}
            nativeButton={false}
          >
            View
          </Button>
        </div>
      </td>
    </tr>
  )
}
