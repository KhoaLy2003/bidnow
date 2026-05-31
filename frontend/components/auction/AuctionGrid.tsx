import { AuctionCard } from './AuctionCard'
import { Skeleton } from '@/components/ui/skeleton'
import type { Auction } from '@/types/ui/auction.ui'
import { cn } from '@/lib/utils'

interface AuctionGridProps {
  auctions:    Auction[]
  isLoading?:  boolean
  skeletonCount?: number
  className?:  string
}

export function AuctionGrid({
  auctions,
  isLoading,
  skeletonCount = 8,
  className,
}: AuctionGridProps) {
  return (
    <div
      className={cn(
        'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4',
        className,
      )}
    >
      {isLoading
        ? Array.from({ length: skeletonCount }).map((_, i) => (
            <AuctionCardSkeleton key={i} />
          ))
        : auctions.map((auction) => (
            <AuctionCard key={auction.id} auction={auction} />
          ))}
    </div>
  )
}

function AuctionCardSkeleton() {
  return (
    <div className="flex flex-col gap-2 rounded-xl overflow-hidden">
      <Skeleton className="aspect-[4/3] w-full" />
      <div className="px-4 flex flex-col gap-1.5">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-3 w-1/4" />
      </div>
      <div className="px-4 pb-3">
        <Skeleton className="h-8 w-full" />
      </div>
    </div>
  )
}
