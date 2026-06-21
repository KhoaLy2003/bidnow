'use client'

import Link  from 'next/link'
import { Card }           from '@/components/ui/card'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { Skeleton }       from '@/components/ui/skeleton'
import { StatusBadge }    from '@/components/auction/StatusBadge'
import { CountdownTimer } from '@/components/auction/CountdownTimer'
import { formatCurrency }   from '@/lib/format'
import { AuctionStatus }    from '@/lib/design-tokens'
import { resolveImageUrl }  from '@/lib/image-utils'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

interface AuctionBrowseCardHorizontalProps {
  item:       AuctionBrowseItem
  className?: string
}

export function AuctionBrowseCardHorizontal({ item, className }: AuctionBrowseCardHorizontalProps) {
  const isClosed      = item.status === AuctionStatus.Closed
  const resolvedImage = resolveImageUrl(item.primaryImageUrl)
  const hasImage      = Boolean(item.primaryImageUrl)
  const imageUrl      = resolvedImage ?? (hasImage ? undefined : '/placeholder-auction.png')

  return (
    <Link href={`/auctions/${item.id}`} className="group block">
      <Card
        className={cn(
          'overflow-hidden transition-[border-color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
          'group-hover:border-brand-300',
          className,
        )}
      >
        <div className="flex gap-4 p-4">

          {/* ── Left: image ─────────────────────────────────────── */}
          <div className="relative w-40 aspect-square shrink-0 rounded-lg overflow-hidden bg-muted">
            {imageUrl ? (
              <ImageThumbnail
                src={imageUrl}
                alt={item.title}
                fill
                className={cn(
                  'object-cover transition-transform duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:scale-[1.02]',
                  isClosed && 'grayscale-[60%]',
                )}
                sizes="160px"
              />
            ) : (
              <Skeleton className="absolute inset-0 size-full rounded-none" />
            )}
            <div className="absolute top-2 left-2">
              <StatusBadge status={item.status} />
            </div>
          </div>

          {/* ── Right: info ─────────────────────────────────────── */}
          <div className="flex-1 min-w-0 flex flex-col justify-between py-0.5">

            {/* Top zone: category + title */}
            <div className="min-w-0">
              <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                {item.categoryName}
              </p>
              <h3 className="mt-1 font-medium text-sm leading-snug line-clamp-2">
                {item.title}
              </h3>
            </div>

            {/* Bottom zone: pricing + meta */}
            <div className="flex flex-col gap-1.5">

              {/* Current bid */}
              <div>
                <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground mb-0.5">
                  Current Bid
                </p>
                <p className="font-mono font-medium text-[length:var(--font-size-price-sm)] leading-none">
                  {formatCurrency(item.currentPrice)}
                </p>
              </div>

              {/* Buy Now — always reserves vertical space; invisible when absent */}
              <p
                className={cn(
                  'text-xs text-muted-foreground',
                  item.buyNowPrice === null && 'invisible select-none',
                )}
                aria-hidden={item.buyNowPrice === null ? true : undefined}
              >
                Buy Now&nbsp;·&nbsp;
                <span className="font-mono">
                  {item.buyNowPrice !== null ? formatCurrency(item.buyNowPrice) : '$0'}
                </span>
              </p>

              {/* Meta: bids · countdown */}
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span>
                  {item.totalBids}&nbsp;{item.totalBids === 1 ? 'bid' : 'bids'}
                </span>
                <span aria-hidden>·</span>
                <CountdownTimer endsAt={item.endTime} size="sm" />
              </div>

            </div>
          </div>

        </div>
      </Card>
    </Link>
  )
}
