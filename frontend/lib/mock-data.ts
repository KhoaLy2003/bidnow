import type { BidHistoryItem } from '@/types/ui/auction.ui'

export const MOCK_CURRENT_USER_ID = 'current-user'

export const MOCK_BIDS: BidHistoryItem[] = [
  { id: 'b1', auctionId: '1', bidderId: 'u5', amount: 1350, placedAt: new Date(Date.now() - 60_000),  isAutoBid: false, bidderName: 'Alex K.',    isCurrentUser: false, isWinning: true  },
  { id: 'b2', auctionId: '1', bidderId: 'u6', amount: 1300, placedAt: new Date(Date.now() - 180_000), isAutoBid: true,  bidderName: 'Maya S.',    isCurrentUser: false, isWinning: false },
  { id: 'b3', auctionId: '1', bidderId: 'me', amount: 1250, placedAt: new Date(Date.now() - 300_000), isAutoBid: false, bidderName: 'You',        isCurrentUser: true,  isWinning: false },
]
