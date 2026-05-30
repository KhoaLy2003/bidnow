'use client'

import Image from 'next/image'
import Link  from 'next/link'
import { Card }           from '@/components/ui/card'
import { StatusBadge }    from '@/components/auction/StatusBadge'
import { CountdownTimer } from '@/components/auction/CountdownTimer'
import { formatCurrency } from '@/lib/format'
import { AuctionStatus }  from '@/lib/design-tokens'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

interface AuctionBrowseCardHorizontalProps {
  item:       AuctionBrowseItem
  className?: string
}

export function AuctionBrowseCardHorizontal({ item, className }: AuctionBrowseCardHorizontalProps) {
  const isClosed = item.status === AuctionStatus.Closed
  const imageUrl = item.primaryImageUrl ?? '/placeholder-auction.png'

  return (
    <Link href={`/auctions/${item.id}`} className="group block">
      <Card
        className={cn(
          'transition-[border-color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
          className,
        )}
      >
        <div className="flex gap-3 p-3">
          {/* Left: image */}
          <div className="relative w-28 aspect-square shrink-0 rounded-lg overflow-hidden bg-muted">
            <Image
              src={imageUrl}
              alt={item.title}
              fill
              className={cn(
                'object-cover transition-transform duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:scale-[1.02]',
                isClosed && 'grayscale-[60%]',
              )}
              sizes="112px"
            />
            <div className="absolute top-1.5 left-1.5">
              <StatusBadge status={item.status} />
            </div>
          </div>

          {/* Right: info */}
          <div className="flex-1 min-w-0 flex flex-col justify-between py-0.5">
            {/* Top: category + title */}
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground truncate">{item.categoryName}</p>
              <h3 className="font-medium text-sm leading-snug line-clamp-2">{item.title}</h3>
            </div>

            {/* Bottom: pricing + meta */}
            <div className="flex flex-col gap-0.5">
              <p className="font-mono font-semibold text-[length:var(--font-size-price-sm)]">
                {formatCurrency(item.currentPrice)}
              </p>

              {/* Buy Now — always rendered; invisible spacer when absent to keep uniform height */}
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

              {/* Bids + countdown */}
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span>{item.totalBids} bid{item.totalBids !== 1 ? 's' : ''}</span>
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
