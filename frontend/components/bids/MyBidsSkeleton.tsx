import { Skeleton } from '@/components/ui/skeleton'

/** Placeholder rows shaped like MyBidRow while the first load is in flight. */
export function MyBidsSkeleton() {
  return (
    <div className="flex flex-col gap-3 md:gap-0 md:divide-y md:rounded-xl md:border" aria-busy="true" aria-label="Loading your bids">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-3 rounded-xl border p-3 md:gap-5 md:rounded-none md:border-0 md:p-4">
          <Skeleton className="size-16 shrink-0 rounded-lg md:size-[72px]" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-3/5" />
            <Skeleton className="h-3 w-1/5" />
          </div>
          <Skeleton className="hidden h-6 w-20 rounded-full md:block" />
          <Skeleton className="hidden h-4 w-[72px] md:block" />
          <Skeleton className="hidden h-4 w-[72px] md:block" />
          <Skeleton className="hidden h-4 w-[72px] md:block" />
          <Skeleton className="hidden h-11 w-24 rounded-md md:block" />
        </div>
      ))}
    </div>
  )
}
