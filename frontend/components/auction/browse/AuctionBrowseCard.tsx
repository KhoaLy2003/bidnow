'use client'

import Link  from 'next/link'
import { Card, CardContent } from '@/components/ui/card'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge }    from '@/components/auction/StatusBadge'
import { CountdownTimer } from '@/components/auction/CountdownTimer'
import { formatCurrency }   from '@/lib/format'
import { AuctionStatus }    from '@/lib/design-tokens'
import { useSecureImage }   from '@/hooks/useSecureImage'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

interface AuctionBrowseCardProps {
  item:       AuctionBrowseItem
  className?: string
}

export function AuctionBrowseCard({ item, className }: AuctionBrowseCardProps) {
  const isClosed       = item.status === AuctionStatus.Closed
  const isEnding       =
    item.status === AuctionStatus.EndingSoon ||
    item.status === AuctionStatus.Critical
  const resolvedImage  = useSecureImage(item.primaryImageUrl)
  const hasImage       = Boolean(item.primaryImageUrl)
  const imageUrl       = resolvedImage ?? (hasImage ? undefined : '/placeholder-auction.png')

  return (
    // w-full fills the grid cell; h-full lets the card stretch to row height
    <Link href={`/auctions/${item.id}`} className="group block w-full h-full">
      <Card
        className={cn(
          // h-full + pt-0: card fills row, image flush to card top
          'h-full pt-0 transition-[border-color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:border-brand-300',
          className,
        )}
      >
        {/* Image — edge-to-edge at top, clipped by Card's overflow-hidden rounded-xl */}
        <div className="relative aspect-[4/3] overflow-hidden bg-muted">
          {imageUrl && (
            <ImageThumbnail
              src={imageUrl}
              alt={item.title}
              fill
              className={cn(
                'object-cover transition-transform duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:scale-[1.02]',
                isClosed && 'grayscale-[60%]',
              )}
              sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
            />
          )}
          {!imageUrl && <Skeleton className="absolute inset-0 size-full rounded-none" />}
          <div className="absolute top-2 left-2">
            <StatusBadge status={item.status} />
          </div>
          {isEnding && (
            <div className="absolute bottom-0 inset-x-0 flex items-center justify-center py-1.5 bg-[var(--color-auction-ending-bg)]/90 backdrop-blur-sm">
              <CountdownTimer endsAt={item.endTime} size="sm" />
            </div>
          )}
        </div>

        {/* Content — flex-1 fills remaining height; justify-between anchors
            identity to top and pricing to bottom regardless of title length */}
        <CardContent className="flex-1 flex flex-col justify-between pt-0 pb-0">
          {/* Top: category + title */}
          <div>
            <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
              {item.categoryName}
            </p>
            <h3 className="mt-1 font-medium text-sm leading-snug line-clamp-2">
              {item.title}
            </h3>
          </div>

          {/* Bottom: pricing + meta */}
          <div className="flex flex-col gap-1.5 mt-3">
            {/* Current bid */}
            <div>
              <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground mb-0.5">
                Current Bid
              </p>
              <p className="font-mono font-medium text-[length:var(--font-size-price-sm)] leading-none">
                {formatCurrency(item.currentPrice)}
              </p>
            </div>

            {/* Buy Now — always rendered; invisible spacer when absent */}
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

            {/* Bid count */}
            <p className="text-xs text-muted-foreground">
              {item.totalBids}&nbsp;{item.totalBids === 1 ? 'bid' : 'bids'}
            </p>
          </div>
        </CardContent>
      </Card>
    </Link>
  )
}
