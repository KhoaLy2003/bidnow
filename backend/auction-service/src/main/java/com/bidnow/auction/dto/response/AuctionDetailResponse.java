package com.bidnow.auction.dto.response;

import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.common.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full auction details for the public detail page")
public class AuctionDetailResponse {

    @Schema(description = "Auction UUID")
    private UUID id;

    @Schema(description = "Auction title")
    private String title;

    @Schema(description = "Item description")
    private String description;

    @Schema(description = "Category details")
    private AuctionCategoryResponse category;

    @Schema(description = "Initial bidding price")
    private BigDecimal startingPrice;

    @Schema(description = "Minimum bid increase amount")
    private BigDecimal bidIncrement;

    @Schema(description = "Optional instant purchase price")
    private BigDecimal buyNowPrice;

    @Schema(description = "Required deposit to participate")
    private BigDecimal depositAmount;

    @Schema(description = "Current highest bid amount")
    private BigDecimal currentPrice;

    @Schema(description = "User ID of current highest bidder")
    private UUID currentWinnerId;

    @Schema(description = "Total number of bids placed")
    private Integer totalBids;

    @Schema(description = "Auction status")
    private AuctionStatus status;

    @Schema(description = "Auction start datetime")
    private OffsetDateTime startTime;

    @Schema(description = "Auction end datetime (may be extended)")
    private OffsetDateTime endTime;

    @Schema(description = "Original planned end time")
    private OffsetDateTime originalEndTime;

    @Schema(description = "Number of anti-sniping extensions")
    private Integer extensionCount;

    @Schema(description = "Actual completion timestamp")
    private OffsetDateTime completedAt;

    @Schema(description = "Final winner user ID")
    private UUID winnerId;

    @Schema(description = "Auction images")
    private List<AuctionImageResponse> images;

    @Schema(description = "Seller summary (null if user-service is unavailable)")
    private UserSummaryResponse seller;

    @Schema(description = "Record creation timestamp")
    private LocalDateTime createdAt;
}
