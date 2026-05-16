package com.bidnow.auction.dto.request;

import com.bidnow.auction.domain.enums.AuctionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request to create a new auction listing")
public class CreateAuctionRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Auction title", example = "Vintage Watch Collection", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "Description is required")
    @Schema(description = "Detailed description of the item", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @NotNull(message = "Category is required")
    @Schema(description = "Category UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID categoryId;

    @NotNull(message = "Starting price is required")
    @DecimalMin(value = "0.00", message = "Starting price must be non-negative")
    @Schema(description = "Initial bidding price", example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal startingPrice;

    @NotNull(message = "Bid increment is required")
    @DecimalMin(value = "0.01", message = "Bid increment must be greater than zero")
    @Schema(description = "Minimum bid increase amount", example = "10.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal bidIncrement;

    @DecimalMin(value = "0.01", message = "Buy now price must be positive")
    @Schema(description = "Optional instant purchase price", example = "500.00")
    private BigDecimal buyNowPrice;

    @NotNull(message = "Deposit amount is required")
    @DecimalMin(value = "0.00", message = "Deposit amount must be non-negative")
    @Schema(description = "Required deposit to participate", example = "50.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal depositAmount;

    @NotNull(message = "Start time is required")
    @Schema(description = "Auction start datetime (UTC)", example = "2026-06-01T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private OffsetDateTime startTime;

    @NotNull(message = "End time is required")
    @Schema(description = "Auction end datetime (UTC)", example = "2026-06-08T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private OffsetDateTime endTime;

    @NotEmpty(message = "At least one image is required")
    @Size(min = 1, max = 10, message = "Must provide between 1 and 10 images")
    @Schema(description = "List of image URLs (1–10)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> imageUrls;

    @Schema(description = "Initial status: DRAFT (default) or ACTIVE", example = "DRAFT")
    private AuctionStatus status;
}
