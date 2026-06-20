'use client'

import { AuctionStatus }    from '@/lib/design-tokens'
import { useAuthStore }     from '@/store/authStore'
import { BidPanelUpcoming } from './BidPanelUpcoming'
import { BidPanelLive }     from './BidPanelLive'
import { BidPanelEnded }    from './BidPanelEnded'
import { SellerOwnedPanel } from './SellerOwnedPanel'
import type { AuctionDetail } from '@/types/ui/auction.ui'

const LIVE_STATUSES = new Set<AuctionStatus>([
  AuctionStatus.Active,
  AuctionStatus.EndingSoon,
  AuctionStatus.Critical,
  AuctionStatus.Outbid,
])

const ENDED_STATUSES = new Set<AuctionStatus>([
  AuctionStatus.Closed,
  AuctionStatus.Won,
  AuctionStatus.Lost,
])

interface BidPanelProps {
  auction: AuctionDetail
}

export function BidPanel({ auction }: BidPanelProps) {
  const user = useAuthStore(s => s.user)
  const isOwner = !!user && !!auction.seller && user.id === auction.seller.id
  const isCurrentUserWinning = user?.id === auction.currentWinnerId

  if (isOwner) {
    return <SellerOwnedPanel auctionId={auction.id} />
  }
  if (auction.status === AuctionStatus.Scheduled) {
    return <BidPanelUpcoming auction={auction} />
  }
  if (LIVE_STATUSES.has(auction.status)) {
    return <BidPanelLive auction={auction} isCurrentUserWinning={isCurrentUserWinning} />
  }
  if (ENDED_STATUSES.has(auction.status)) {
    return <BidPanelEnded auction={auction} />
  }
  return <BidPanelLive auction={auction} isCurrentUserWinning={isCurrentUserWinning} />
}
