import Link from 'next/link'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { buttonVariants } from '@/components/ui/button'
import { BidStatusPill } from '@/components/bids/BidStatusPill'
import { BidTimeLeft } from '@/components/bids/BidTimeLeft'
import { formatCurrency } from '@/lib/format'
import { resolveImageUrl } from '@/lib/image-utils'
import { cn } from '@/lib/utils'
import type { MyActiveBid } from '@/types/ui/bid.ui'

/** Column template shared with the header row in MyBidsList (md and up). */
export const MY_BID_COLUMNS = 'md:grid-cols-[72px_minmax(0,1fr)_104px_112px_112px_112px_112px]'

const LABEL = 'text-[10px] font-medium uppercase tracking-wider text-muted-foreground md:hidden'

interface MyBidRowProps {
  bid: MyActiveBid
}

/**
 * One active bid. Below md it is a card (image + title, a three-figure strip, a full-width action); from md up the
 * strip's children become columns of the row, so the same markup serves both layouts.
 */
export function MyBidRow({ bid }: MyBidRowProps) {
  const image = resolveImageUrl(bid.primaryImageUrl) ?? '/placeholder-auction.png'
  const href = `/auctions/${bid.auctionId}`

  return (
    <li
      className={cn(
        'grid grid-cols-[64px_minmax(0,1fr)] gap-x-3 gap-y-3 rounded-xl border p-3',
        'md:items-center md:gap-x-5 md:rounded-none md:border-0 md:p-4',
        MY_BID_COLUMNS,
      )}
    >
      <Link href={href} className="relative size-16 overflow-hidden rounded-lg border bg-muted md:size-[72px]" tabIndex={-1} aria-hidden>
        <ImageThumbnail src={image} alt="" fill className="object-cover" sizes="72px" />
      </Link>

      <div className="min-w-0">
        <Link href={href} className="block truncate text-sm font-medium hover:underline md:text-[15px]">
          {bid.title}
        </Link>
        <p className="mt-1 text-xs text-muted-foreground">
          {bid.totalBids}&nbsp;{bid.totalBids === 1 ? 'bid' : 'bids'}
        </p>
        <BidStatusPill isWinning={bid.isWinning} className="mt-2 md:hidden" />
      </div>

      <div className="hidden md:block">
        <BidStatusPill isWinning={bid.isWinning} />
      </div>

      <div className="col-span-2 grid grid-cols-3 gap-2 border-y py-2.5 md:contents">
        <div className="md:text-right">
          <p className={LABEL}>Your bid</p>
          <p className="mt-0.5 font-mono text-sm md:mt-0 md:text-[15px]">{formatCurrency(bid.myHighestBid)}</p>
        </div>
        <div className="md:text-right">
          <p className={LABEL}>Current</p>
          <p className="mt-0.5 font-mono text-sm font-medium md:mt-0 md:text-[15px]">{formatCurrency(bid.currentPrice)}</p>
        </div>
        <div className="text-right">
          <p className={LABEL}>Time left</p>
          <BidTimeLeft endsAt={bid.endTime} className="mt-0.5 block text-sm md:mt-0 md:text-[15px]" />
        </div>
      </div>

      <div className="col-span-2 md:col-span-1 md:text-right">
        <Link
          href={href}
          className={cn(
            buttonVariants({ variant: bid.isWinning ? 'outline' : 'brand', size: 'xl' }),
            'w-full md:w-auto',
          )}
        >
          {bid.isWinning ? 'View' : 'Bid again'}
        </Link>
      </div>
    </li>
  )
}
