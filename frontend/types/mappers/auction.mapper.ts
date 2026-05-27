import type { AuctionSummaryResponse } from '@/types/api/auction.api'
import { SellerAuction, SellerAuctionStatus } from '@/types/ui/seller.ui'

export function mapAuctionSummaryToSellerAuction(dto: AuctionSummaryResponse): SellerAuction {
  let status = SellerAuctionStatus.Draft;
  const dtoStatus = dto.status?.toUpperCase() || '';
  
  if (dtoStatus === 'ACTIVE') status = SellerAuctionStatus.Active;
  else if (dtoStatus === 'SCHEDULED') status = SellerAuctionStatus.Scheduled;
  else if (dtoStatus === 'DRAFT') status = SellerAuctionStatus.Draft;
  else if (dtoStatus === 'COMPLETED') status = SellerAuctionStatus.Completed;
  else if (dtoStatus === 'FAILED') status = SellerAuctionStatus.Failed;
  else if (dtoStatus === 'CANCELLED') status = SellerAuctionStatus.Cancelled;

  return {
    id: dto.id,
    title: dto.title,
    sellerId: dto.sellerId,
    categoryId: dto.categoryId,
    categoryName: dto.categoryName,
    startingPrice: dto.startingPrice,
    currentBid: dto.currentPrice,
    primaryImageUrl: dto.primaryImageUrl,
    totalBids: dto.totalBids,
    startsAt: new Date(dto.startTime),
    endsAt: new Date(dto.endTime),
    createdAt: new Date(dto.createdAt),
    status
  };
}
