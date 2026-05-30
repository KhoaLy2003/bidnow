import type { AuctionSummaryResponse, AuctionDetailResponse } from '@/types/api/auction.api'
import { SellerAuction, SellerAuctionStatus } from '@/types/ui/seller.ui'
import type { AuctionDetail, AuctionDetailSeller } from '@/types/ui/auction.ui'
import { AuctionStatus } from '@/lib/design-tokens'

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

function parseDetailStatus(status: string): AuctionStatus {
  switch (status?.toUpperCase()) {
    case 'ACTIVE':    return AuctionStatus.Active
    case 'SCHEDULED': return AuctionStatus.Scheduled
    default:          return AuctionStatus.Closed
  }
}

export function mapAuctionDetailResponse(dto: AuctionDetailResponse): AuctionDetail {
  const seller: AuctionDetailSeller | null = dto.seller
    ? { id: dto.seller.id, name: dto.seller.name, avatarUrl: dto.seller.avatarUrl }
    : null

  return {
    id:              dto.id,
    title:           dto.title,
    description:     dto.description,
    categoryId:      dto.category.id,
    categoryName:    dto.category.name,
    startingPrice:   dto.startingPrice,
    bidIncrement:    dto.bidIncrement,
    buyNowPrice:     dto.buyNowPrice,
    depositAmount:   dto.depositAmount,
    currentBid:      dto.currentPrice,
    currentWinnerId: dto.currentWinnerId,
    totalBids:       dto.totalBids,
    status:          parseDetailStatus(dto.status),
    startsAt:        new Date(dto.startTime),
    endsAt:          new Date(dto.endTime),
    originalEndAt:   new Date(dto.originalEndTime),
    extensionCount:  dto.extensionCount,
    completedAt:     dto.completedAt ? new Date(dto.completedAt) : undefined,
    winnerId:        dto.winnerId,
    images:          dto.images,
    seller,
    createdAt:       new Date(dto.createdAt),
  }
}
