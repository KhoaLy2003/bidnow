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
@Schema(description = "Auction list item (summary view)")
public class AuctionSummaryResponse {

    @Schema(description = "Auction UUID")
    private UUID id;

    @Schema(description = "Seller user ID")
    private UUID sellerId;

    @Schema(description = "Auction title")
    private String title;

    @Schema(description = "Category UUID")
    private UUID categoryId;

    @Schema(description = "Category name")
    private String categoryName;

    @Schema(description = "Initial bidding price")
    private BigDecimal startingPrice;

    @Schema(description = "Current highest bid amount")
    private BigDecimal currentPrice;

    @Schema(description = "Instant purchase price (null if not available)", nullable = true)
    private BigDecimal buyNowPrice;

    @Schema(description = "Auction status")
    private AuctionStatus status;

    @Schema(description = "Auction start datetime")
    private OffsetDateTime startTime;

    @Schema(description = "Auction end datetime")
    private OffsetDateTime endTime;

    @Schema(description = "Total number of bids placed")
    private Integer totalBids;

    @Schema(description = "Primary image URL")
    private String primaryImageUrl;

    @Schema(description = "Record creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
