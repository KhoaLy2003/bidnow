package com.bidnow.auction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update an existing auction (all fields optional — null values are ignored)")
public class UpdateAuctionRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Auction title")
    private String title;

    @Schema(description = "Detailed description of the item")
    private String description;

    @Schema(description = "Category UUID")
    private UUID categoryId;

    @DecimalMin(value = "0.01", message = "Starting price must be greater than zero")
    @Schema(description = "Initial bidding price", example = "100.00")
    private BigDecimal startingPrice;

    @DecimalMin(value = "0.01", message = "Bid increment must be greater than zero")
    @Schema(description = "Minimum bid increase amount", example = "10.00")
    private BigDecimal bidIncrement;

    @DecimalMin(value = "0.01", message = "Buy now price must be positive")
    @Schema(description = "Optional instant purchase price (null to remove)")
    private BigDecimal buyNowPrice;

    @DecimalMin(value = "0.00", message = "Deposit amount must be non-negative")
    @Schema(description = "Required deposit to participate", example = "50.00")
    private BigDecimal depositAmount;

    @Schema(description = "Auction start datetime (UTC)")
    private OffsetDateTime startTime;

    @Schema(description = "Auction end datetime (UTC)")
    private OffsetDateTime endTime;

    @Size(min = 1, max = 10, message = "Must provide between 1 and 10 images")
    @Schema(description = "Replacement image URL list (1–10); omit to keep existing images")
    private List<String> imageUrls;
}
