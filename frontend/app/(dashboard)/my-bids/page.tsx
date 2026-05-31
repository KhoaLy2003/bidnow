import type { Metadata } from 'next'
import { ListOrdered } from 'lucide-react'
import { AuctionGrid }   from '@/components/auction/AuctionGrid'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Auction }  from '@/types/ui/auction.ui'

export const metadata: Metadata = { title: 'My Bids' }

const MY_BIDS: Auction[] = [
  {
    id: '1', title: 'Vintage Omega Seamaster 1968', description: '',
    imageUrls: [], categoryId: 'watches', sellerId: 'u1',
    startingPrice: 50_000, currentBid: 135_000, totalBids: 14, watchers: 42,
    startsAt: new Date(Date.now() - 3_600_000),
    endsAt:   new Date(Date.now() + 7_200_000),
    status: AuctionStatus.Active, isFeatured: true,
    condition: 'Near Mint', reserveMet: true,
    seller: { id: 'u1', name: 'Marcus W.', rating: 4.9, totalAuctions: 218 },
  },
  {
    id: '2', title: 'Gibson Les Paul Custom 1974', description: '',
    imageUrls: [], categoryId: 'music', sellerId: 'u2',
    startingPrice: 100_000, currentBid: 280_000, totalBids: 31, watchers: 89,
    startsAt: new Date(Date.now() - 7_200_000),
    endsAt:   new Date(Date.now() - 3_600_000),
    status: AuctionStatus.Won, isFeatured: true,
    condition: 'Good', reserveMet: true,
    seller: { id: 'u2', name: 'Vintage Strings', rating: 4.7, totalAuctions: 132 },
  },
]

export default function MyBidsPage() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2">
        <ListOrdered className="size-5 text-[var(--color-text-brand)]" />
        <h1 className="font-display font-medium text-[length:var(--font-size-2xl)]">My Bids</h1>
      </div>
      <AuctionGrid auctions={MY_BIDS} />
    </div>
  )
}
