package com.bidnow.auction.dto.response;

import com.bidnow.auction.domain.enums.AuctionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Auction list item for the admin moderation browse endpoint")
public class AdminAuctionSummaryResponse {

    @Schema(description = "Auction UUID")
    private UUID id;

    @Schema(description = "Auction title")
    private String title;

    @Schema(description = "Auction status")
    private AuctionStatus status;

    @Schema(description = "Seller user ID")
    private UUID sellerId;

    @Schema(description = "Seller display name (null if user-service is unavailable)", nullable = true)
    private String sellerName;

    @Schema(description = "Category details")
    private AuctionCategoryResponse category;

    @Schema(description = "Current highest bid amount")
    private BigDecimal currentPrice;

    @Schema(description = "Auction start datetime")
    private OffsetDateTime startTime;

    @Schema(description = "Auction end datetime")
    private OffsetDateTime endTime;

    @Schema(description = "Total number of bids placed")
    private Integer totalBids;

    @Schema(description = "Record creation timestamp")
    private LocalDateTime createdAt;
}
