import { AuctionStatus } from '@/lib/design-tokens'
import type { AuctionDetail } from '@/types/ui/auction.ui'

/** A live auction with a buy-now price of $200 and a current bid of $100. Test-only. */
export function baseAuction(overrides: Partial<AuctionDetail> = {}): AuctionDetail {
  return {
    id: 'a-1',
    title: 'Vintage Watch',
    description: 'A watch',
    categoryId: 'c-1',
    categoryName: 'Watches',
    startingPrice: 50,
    bidIncrement: 5,
    buyNowPrice: 200,
    depositAmount: 20,
    currentBid: 100,
    currentWinnerId: 'other-user',
    totalBids: 3,
    status: AuctionStatus.Active,
    startsAt: new Date('2026-10-01T10:00:00Z'),
    endsAt: new Date('2026-10-01T13:00:00Z'),
    originalEndAt: new Date('2026-10-01T13:00:00Z'),
    extensionCount: 0,
    images: [],
    seller: { id: 'seller-1', name: 'Sam' },
    createdAt: new Date('2026-10-01T09:00:00Z'),
    ...overrides,
  }
}
