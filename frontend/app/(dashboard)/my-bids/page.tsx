import type { Metadata } from 'next'
import { ListOrdered } from 'lucide-react'
import { AuctionBrowseGrid } from '@/components/auction/browse'
import { AuctionStatus }     from '@/lib/design-tokens'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

export const metadata: Metadata = { title: 'My Bids' }

const MY_BIDS: AuctionBrowseItem[] = [
  {
    id:              '1',
    title:           'Vintage Omega Seamaster 1968',
    primaryImageUrl: null,
    currentPrice:    1350,
    totalBids:       14,
    endTime:         new Date(Date.now() + 7_200_000),
    status:          AuctionStatus.Active,
    buyNowPrice:     null,
    categoryName:    'Watches',
  },
  {
    id:              '2',
    title:           'Gibson Les Paul Custom 1974',
    primaryImageUrl: null,
    currentPrice:    2800,
    totalBids:       31,
    endTime:         new Date(Date.now() - 3_600_000),
    status:          AuctionStatus.Won,
    buyNowPrice:     null,
    categoryName:    'Music',
  },
]

export default function MyBidsPage() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2">
        <ListOrdered className="size-5 text-[var(--color-text-brand)]" />
        <h1 className="font-display font-medium text-[length:var(--font-size-2xl)]">My Bids</h1>
      </div>
      <AuctionBrowseGrid items={MY_BIDS} />
    </div>
  )
}
