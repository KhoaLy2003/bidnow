'use client'

import Image from 'next/image'
import Link  from 'next/link'
import { Tag } from 'lucide-react'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Badge }          from '@/components/ui/badge'
import { Button }         from '@/components/ui/button'
import { StatusBadge }    from '@/components/auction/StatusBadge'
import { CountdownTimer } from '@/components/auction/CountdownTimer'
import { formatCurrency } from '@/lib/format'
import { AuctionStatus }  from '@/lib/design-tokens'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

interface AuctionBrowseCardProps {
  item:       AuctionBrowseItem
  className?: string
}

export function AuctionBrowseCard({ item, className }: AuctionBrowseCardProps) {
  const isClosed  = item.status === AuctionStatus.Closed
  const isEnding  =
    item.status === AuctionStatus.EndingSoon ||
    item.status === AuctionStatus.Critical
  const imageUrl  = item.primaryImageUrl ?? '/placeholder-auction.png'

  return (
    <Link href={`/auctions/${item.id}`} className="group block min-w-[240px] max-w-[360px]">
      <Card
        className={cn(
          'transition-[border-color,opacity] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
          className,
        )}
      >
        {/* Image */}
        <div className="relative aspect-[4/3] overflow-hidden rounded-t-xl bg-muted">
          <Image
            src={imageUrl}
            alt={item.title}
            fill
            className={cn(
              'object-cover transition-transform duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:scale-[1.02]',
              isClosed && 'grayscale-[60%]',
            )}
            sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
          />

          {/* Status badge */}
          <div className="absolute top-2 left-2">
            <StatusBadge status={item.status} />
          </div>

          {/* Buy Now badge */}
          {item.buyNowPrice !== null && (
            <div className="absolute top-2 right-2">
              <Badge variant="secondary" className="gap-1 text-[10px]">
                <Tag className="size-2.5" />
                Buy Now
              </Badge>
            </div>
          )}

          {/* Countdown banner when ending */}
          {isEnding && (
            <div className="absolute bottom-0 inset-x-0 flex items-center justify-center gap-1.5 py-1.5 bg-[var(--color-auction-ending-bg)]/90 backdrop-blur-sm">
              <CountdownTimer endsAt={item.endTime} size="sm" />
            </div>
          )}
        </div>

        <CardContent className="pt-3">
          <p className="text-xs text-muted-foreground mb-0.5">{item.categoryName}</p>
          <h3 className="font-medium text-sm leading-snug line-clamp-2">
            {item.title}
          </h3>
          <p className="mt-1 font-mono font-medium text-[length:var(--font-size-price-sm)]">
            {formatCurrency(item.currentPrice)}
          </p>
          <p className="text-xs text-muted-foreground">
            {item.totalBids} bid{item.totalBids !== 1 ? 's' : ''}
          </p>
        </CardContent>

        <CardFooter className="pt-0 pb-3 px-4 flex flex-col gap-1.5">
          {isClosed ? (
            <Button variant="outline" size="sm" className="w-full">
              View Item
            </Button>
          ) : (
            <Button variant="brand" size="sm" className="w-full">
              Place Bid
            </Button>
          )}
          {item.buyNowPrice !== null && !isClosed && (
            <Button variant="outline" size="sm" className="w-full">
              Buy Now · {formatCurrency(item.buyNowPrice)}
            </Button>
          )}
        </CardFooter>
      </Card>
    </Link>
  )
}
