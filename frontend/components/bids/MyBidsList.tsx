import { MY_BID_COLUMNS, MyBidRow } from '@/components/bids/MyBidRow'
import { cn } from '@/lib/utils'
import type { MyActiveBid } from '@/types/ui/bid.ui'

const HEAD = 'text-[10px] font-medium uppercase tracking-wider text-muted-foreground'

interface MyBidsListProps {
  bids: MyActiveBid[]
}

export function MyBidsList({ bids }: MyBidsListProps) {
  return (
    <div>
      <div className={cn('hidden gap-x-5 px-4 pt-3.5 pb-2.5 md:grid', MY_BID_COLUMNS)} aria-hidden>
        <span />
        <span className={HEAD}>Auction</span>
        <span className={HEAD}>Status</span>
        <span className={cn(HEAD, 'text-right')}>Your bid</span>
        <span className={cn(HEAD, 'text-right')}>Current bid</span>
        <span className={cn(HEAD, 'text-right')}>Time left</span>
        <span />
      </div>
      <ul className="flex flex-col gap-3 md:gap-0 md:divide-y md:rounded-xl md:border">
        {bids.map((bid) => (
          <MyBidRow key={bid.auctionId} bid={bid} />
        ))}
      </ul>
    </div>
  )
}
