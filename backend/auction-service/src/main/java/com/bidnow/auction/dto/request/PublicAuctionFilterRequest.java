package com.bidnow.auction.dto.request;

import com.bidnow.auction.domain.enums.AuctionSortBy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter and sort parameters for the public auction browse endpoint")
public class PublicAuctionFilterRequest {

    @Schema(description = "Filter by category UUID (takes precedence over categorySlug)")
    private UUID categoryId;

    @Schema(description = "Filter by category slug (ignored when categoryId is also provided)")
    private String categorySlug;

    @Schema(description = "Minimum current price (inclusive)", example = "100.00")
    private BigDecimal minPrice;

    @Schema(description = "Maximum current price (inclusive)", example = "500.00")
    private BigDecimal maxPrice;

    @Schema(description = "When true, only return auctions ending within the next 24 hours")
    private Boolean endingSoon;

    @Schema(description = "Case-insensitive keyword match on auction title")
    private String keyword;

    @Schema(description = "When true, only return auctions that have a buy-now price set")
    private Boolean buyNowAvailable;

    @Builder.Default
    @Schema(description = "Sort order", defaultValue = "END_TIME_ASC")
    private AuctionSortBy sortBy = AuctionSortBy.END_TIME_ASC;

    @Min(0)
    @Builder.Default
    @Schema(description = "Zero-based page number", defaultValue = "0")
    private int page = 0;

    @Min(1)
    @Max(50)
    @Builder.Default
    @Schema(description = "Page size (max 50)", defaultValue = "20")
    private int size = 20;
}
