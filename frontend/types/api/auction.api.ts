export interface AuctionCategoryResponse {
  id: string;
  name: string;
  slug: string;
}

export interface AuctionImageResponse {
  id: string;
  imageUrl: string;
  thumbnailUrl: string;
  displayOrder: number;
  isPrimary: boolean;
  uploadedAt: string;
}

export interface AuctionResponse {
  id: string;
  sellerId: string;
  title: string;
  description: string;
  category: AuctionCategoryResponse;
  startingPrice: number;
  bidIncrement: number;
  buyNowPrice?: number;
  depositAmount: number;
  currentPrice: number;
  currentWinnerId?: string;
  totalBids: number;
  status: string;
  startTime: string;
  endTime: string;
  originalEndTime: string;
  extensionCount: number;
  completedAt?: string;
  winnerId?: string;
  images: AuctionImageResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateAuctionRequest {
  title: string;
  description: string;
  categoryId: string;
  startingPrice: number;
  bidIncrement: number;
  buyNowPrice?: number;
  depositAmount: number;
  startTime: string;
  endTime: string;
  imageUrls: string[];
  status?: string;
}

export interface UpdateAuctionRequest {
  title: string;
  description: string;
  categoryId: string;
  startingPrice: number;
  bidIncrement: number;
  buyNowPrice?: number;
  depositAmount: number;
  startTime: string;
  endTime: string;
  imageUrls: string[];
}

export interface AuctionSummaryResponse {
  id: string;
  sellerId: string;
  title: string;
  categoryId: string;
  categoryName: string;
  startingPrice: number;
  currentPrice: number;
  status: string;
  startTime: string;
  endTime: string;
  totalBids: number;
  primaryImageUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserSummaryResponse {
  id: string;
  name: string;
  avatarUrl?: string;
}

export interface AuctionDetailResponse {
  id: string;
  title: string;
  description: string;
  category: AuctionCategoryResponse;
  startingPrice: number;
  bidIncrement: number;
  buyNowPrice: number | null;
  depositAmount: number;
  currentPrice: number;
  currentWinnerId?: string;
  totalBids: number;
  status: string;
  startTime: string;
  endTime: string;
  originalEndTime: string;
  extensionCount: number;
  completedAt?: string;
  winnerId?: string;
  images: AuctionImageResponse[];
  seller: UserSummaryResponse | null;
  createdAt: string;
}

export interface AuctionBrowseItemResponse {
  id:              string;
  title:           string;
  primaryImageUrl: string | null;
  currentPrice:    number;
  totalBids:       number;
  endTime:         string;
  status:          string;
  buyNowPrice:     number | null;
  categoryName:    string;
}

export interface CategoryCountResponse {
  categoryId:    string;
  categoryName: string;
  slug:         string;
  count:        number;
}

export interface BrowseAuctionParams {
  keyword?:         string;
  categorySlug?:    string;
  minPrice?:        number;
  maxPrice?:        number;
  endingSoon?:      boolean;
  buyNowAvailable?: boolean;
  sortBy?:          string;
  page?:            number;
  size?:            number;
}

export type AdminAuctionStatus =
  | "DRAFT"
  | "SCHEDULED"
  | "ACTIVE"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "REJECTED";

export interface AdminAuctionSummaryResponse {
  id: string;
  title: string;
  status: AdminAuctionStatus;
  sellerId: string;
  sellerName?: string | null;
  category: AuctionCategoryResponse;
  currentPrice: number;
  startTime: string;
  endTime: string;
  totalBids: number;
  createdAt: string;
}

export interface AuctionStatusHistoryResponse {
  fromStatus: string | null;
  toStatus: string;
  reason?: string | null;
  triggeredBy?: string | null;
  createdAt: string;
}

export interface AdminAuctionDetailResponse {
  id: string;
  title: string;
  description: string;
  category: AuctionCategoryResponse;
  sellerId: string;
  sellerName?: string | null;
  startingPrice: number;
  bidIncrement: number;
  buyNowPrice?: number | null;
  depositAmount: number;
  currentPrice: number;
  currentWinnerId?: string | null;
  totalBids: number;
  status: AdminAuctionStatus;
  startTime: string;
  endTime: string;
  originalEndTime: string;
  extensionCount: number;
  completedAt?: string | null;
  winnerId?: string | null;
  cancellationReason?: string | null;
  cancelledBy?: string | null;
  cancelledAt?: string | null;
  rejectionReason?: string | null;
  rejectedBy?: string | null;
  rejectedAt?: string | null;
  images: AuctionImageResponse[];
  statusHistory: AuctionStatusHistoryResponse[];
  createdAt: string;
}

export interface AdminAuctionFilters {
  status?: AdminAuctionStatus | "ALL";
  categoryId?: string;
  sellerId?: string;
  q?: string;
}

export interface AdminAuctionReasonRequest {
  reason?: string;
}
