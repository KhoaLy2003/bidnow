import { Skeleton }                   from '@/components/ui/skeleton'
import { AuctionBrowseCardHorizontal } from './AuctionBrowseCardHorizontal'
import type { AuctionBrowseItem }      from '@/types/ui/auction-browse.ui'
import { cn } from '@/lib/utils'

interface AuctionBrowseListProps {
  items:          AuctionBrowseItem[]
  isLoading?:     boolean
  skeletonCount?: number
  className?:     string
}

export function AuctionBrowseList({
  items,
  isLoading,
  skeletonCount = 6,
  className,
}: AuctionBrowseListProps) {
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {isLoading
        ? Array.from({ length: skeletonCount }).map((_, i) => (
            <BrowseListSkeleton key={i} />
          ))
        : items.map((item) => (
            <AuctionBrowseCardHorizontal key={item.id} item={item} />
          ))}
    </div>
  )
}

function BrowseListSkeleton() {
  return (
    <div className="flex gap-3 p-3 rounded-xl border border-border">
      <Skeleton className="w-28 aspect-square shrink-0 rounded-lg" />
      <div className="flex-1 flex flex-col justify-between py-0.5">
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3 w-1/4" />
          <Skeleton className="h-4 w-3/4" />
        </div>
        <div className="flex flex-col gap-1">
          <Skeleton className="h-5 w-1/3" />
          <Skeleton className="h-3 w-1/4" />
          <Skeleton className="h-3 w-1/3" />
        </div>
      </div>
    </div>
  )
}
