import { Skeleton }         from '@/components/ui/skeleton'
import { AuctionBrowseCard } from './AuctionBrowseCard'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

interface AuctionBrowseGridProps {
  items:          AuctionBrowseItem[]
  isLoading?:     boolean
  skeletonCount?: number
  className?:     string
}

export function AuctionBrowseGrid({
  items,
  isLoading,
  skeletonCount = 8,
  className,
}: AuctionBrowseGridProps) {
  return (
    <div
      className={cn(
        'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4',
        className,
      )}
    >
      {isLoading
        ? Array.from({ length: skeletonCount }).map((_, i) => (
            <BrowseCardSkeleton key={i} />
          ))
        : items.map((item) => (
            <AuctionBrowseCard key={item.id} item={item} />
          ))}
    </div>
  )
}

function BrowseCardSkeleton() {
  return (
    <div className="flex flex-col gap-2 rounded-xl overflow-hidden">
      <Skeleton className="aspect-[4/3] w-full" />
      <div className="px-4 flex flex-col gap-1.5">
        <Skeleton className="h-3 w-1/3" />
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
