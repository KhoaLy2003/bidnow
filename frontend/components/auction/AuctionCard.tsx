import Image from 'next/image'
import Link from 'next/link'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { StatusBadge } from './StatusBadge'
import { CountdownTimer } from './CountdownTimer'
import { formatCurrency } from '@/lib/format'
import { getAuctionStatus } from '@/lib/auction-utils'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Auction } from '@/types/auction'
import { cn } from '@/lib/utils'

interface AuctionCardProps {
  auction:    Auction
  className?: string
}

export function AuctionCard({ auction, className }: AuctionCardProps) {
  const status     = getAuctionStatus(auction)
  const isClosed   = status === AuctionStatus.Closed
  const isEnding   = status === AuctionStatus.EndingSoon || status === AuctionStatus.Critical
  const imageUrl   = auction.imageUrls[0] ?? '/placeholder-auction.png'

  return (
    <Link href={`/auctions/${auction.id}`} className="group block min-w-[240px] max-w-[360px]">
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
            alt={auction.title}
            fill
            className={cn(
              'object-cover transition-transform duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:scale-[1.02]',
              isClosed && 'grayscale-[60%]',
            )}
            sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
          />

          {/* Status badge overlay */}
          <div className="absolute top-2 left-2">
            <StatusBadge status={status} />
          </div>

          {/* Ending-soon banner */}
          {isEnding && (
            <div className="absolute bottom-0 inset-x-0 flex items-center justify-center gap-1.5 py-1.5 bg-[var(--color-auction-ending-bg)]/90 backdrop-blur-sm">
              <CountdownTimer endsAt={auction.endsAt} size="sm" />
            </div>
          )}
        </div>

        <CardContent className="pt-3">
          <h3 className="font-medium text-sm leading-snug line-clamp-2">
            {auction.title}
          </h3>
          <p className="mt-1 font-mono font-medium text-[length:var(--font-size-price-sm)]">
            {formatCurrency(auction.currentBid)}
          </p>
          <p className="text-xs text-muted-foreground">
            {auction.totalBids} bid{auction.totalBids !== 1 ? 's' : ''}
          </p>
        </CardContent>

        <CardFooter className="pt-0 pb-3 px-4">
          {isClosed ? (
            <Button variant="outline" size="sm" className="w-full">
              View Item
            </Button>
          ) : (
            <Button variant="brand" size="sm" className="w-full">
              Place Bid
            </Button>
          )}
        </CardFooter>
      </Card>
    </Link>
  )
}
