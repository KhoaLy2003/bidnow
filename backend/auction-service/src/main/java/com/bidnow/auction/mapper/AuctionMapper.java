package com.bidnow.auction.mapper;

import com.bidnow.auction.domain.entity.AuctionCategory;
import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.dto.request.UpdateAuctionRequest;
import com.bidnow.auction.dto.response.AuctionBrowseItem;
import com.bidnow.auction.dto.response.AuctionCategoryResponse;
import com.bidnow.auction.dto.response.AuctionDetailResponse;
import com.bidnow.auction.dto.response.AuctionImageResponse;
import com.bidnow.auction.dto.response.SellerAuctionResponse;
import com.bidnow.auction.dto.response.AuctionSummaryResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class AuctionMapper {

    public SellerAuctionResponse toResponse(AuctionItem item, List<AuctionImage> images) {
        if (item == null) return null;
        return SellerAuctionResponse.builder()
                .id(item.getId())
                .sellerId(item.getSellerId())
                .title(item.getTitle())
                .description(item.getDescription())
                .category(toCategory(item.getCategory()))
                .startingPrice(item.getStartingPrice())
                .bidIncrement(item.getBidIncrement())
                .buyNowPrice(item.getBuyNowPrice())
                .depositAmount(item.getDepositAmount())
                .currentPrice(item.getCurrentPrice())
                .currentWinnerId(item.getCurrentWinnerId())
                .totalBids(item.getTotalBids())
                .status(item.getStatus())
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .originalEndTime(item.getOriginalEndTime())
                .extensionCount(item.getExtensionCount())
                .completedAt(item.getCompletedAt())
                .winnerId(item.getWinnerId())
                .images(images.stream().map(this::toImageResponse).toList())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public AuctionSummaryResponse toSummaryResponse(AuctionItem item, AuctionImage primaryImage) {
        if (item == null) return null;
        AuctionCategory category = item.getCategory();
        return AuctionSummaryResponse.builder()
                .id(item.getId())
                .sellerId(item.getSellerId())
                .title(item.getTitle())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .startingPrice(item.getStartingPrice())
                .currentPrice(item.getCurrentPrice())
                .buyNowPrice(item.getBuyNowPrice())
                .status(item.getStatus())
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .totalBids(item.getTotalBids())
                .primaryImageUrl(primaryImage != null ? primaryImage.getImageUrl() : null)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public AuctionImageResponse toImageResponse(AuctionImage image) {
        if (image == null) return null;
        return AuctionImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .thumbnailUrl(image.getThumbnailUrl())
                .displayOrder(image.getDisplayOrder())
                .isPrimary(image.getIsPrimary())
                .uploadedAt(image.getUploadedAt())
                .build();
    }

    public AuctionCategoryResponse toCategory(AuctionCategory category) {
        if (category == null) return null;
        return AuctionCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .build();
    }

    public AuctionDetailResponse toDetailResponse(AuctionItem item, List<AuctionImage> images, UserSummaryResponse seller) {
        if (item == null) return null;
        return AuctionDetailResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .category(toCategory(item.getCategory()))
                .startingPrice(item.getStartingPrice())
                .bidIncrement(item.getBidIncrement())
                .buyNowPrice(item.getBuyNowPrice())
                .depositAmount(item.getDepositAmount())
                .currentPrice(item.getCurrentPrice())
                .currentWinnerId(item.getCurrentWinnerId())
                .totalBids(item.getTotalBids())
                .status(item.getStatus())
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .originalEndTime(item.getOriginalEndTime())
                .extensionCount(item.getExtensionCount())
                .completedAt(item.getCompletedAt())
                .winnerId(item.getWinnerId())
                .images(images.stream().map(this::toImageResponse).toList())
                .seller(seller)
                .createdAt(item.getCreatedAt())
                .build();
    }

    public AuctionBrowseItem toBrowseItem(AuctionItem item, AuctionImage primaryImage) {
        if (item == null) return null;
        AuctionCategory category = item.getCategory();
        return AuctionBrowseItem.builder()
                .id(item.getId())
                .title(item.getTitle())
                .primaryImageUrl(primaryImage != null ? primaryImage.getImageUrl() : null)
                .currentPrice(item.getCurrentPrice())
                .totalBids(item.getTotalBids())
                .endTime(item.getEndTime())
                .status(item.getStatus())
                .buyNowPrice(item.getBuyNowPrice())
                .categoryName(category != null ? category.getName() : null)
                .build();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "currentPrice", ignore = true)
    @Mapping(target = "currentWinnerId", ignore = true)
    @Mapping(target = "totalBids", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "originalEndTime", ignore = true)
    @Mapping(target = "extensionCount", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "winnerId", ignore = true)
    @Mapping(target = "winnerPaidAt", ignore = true)
    @Mapping(target = "paymentDeadline", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    public abstract void updateFromRequest(UpdateAuctionRequest request, @MappingTarget AuctionItem item);
}
