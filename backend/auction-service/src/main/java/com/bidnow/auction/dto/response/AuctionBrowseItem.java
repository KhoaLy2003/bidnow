package com.bidnow.auction.dto.response;

import com.bidnow.auction.domain.enums.AuctionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Minimal auction item for public browse listing")
public class AuctionBrowseItem {

    @Schema(description = "Auction UUID")
    private UUID id;

    @Schema(description = "Auction title")
    private String title;

    @Schema(description = "Primary image URL")
    private String primaryImageUrl;

    @Schema(description = "Current highest bid amount")
    private BigDecimal currentPrice;

    @Schema(description = "Total number of bids placed")
    private Integer totalBids;

    @Schema(description = "Auction end datetime")
    private OffsetDateTime endTime;

    @Schema(description = "Auction status")
    private AuctionStatus status;

    @Schema(description = "Instant purchase price (null if not available)")
    private BigDecimal buyNowPrice;

    @Schema(description = "Category name")
    private String categoryName;
}
