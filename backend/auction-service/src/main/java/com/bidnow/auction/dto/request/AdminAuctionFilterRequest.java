package com.bidnow.auction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter parameters for the admin auction browse endpoint")
public class AdminAuctionFilterRequest {

    @Schema(description = "Comma-separated AuctionStatus values, e.g. SCHEDULED,ACTIVE")
    private String status;

    @Schema(description = "Filter by category UUID")
    private UUID categoryId;

    @Schema(description = "Filter by seller UUID")
    private UUID sellerId;

    @Schema(description = "Case-insensitive partial match on auction title")
    private String q;

    @Min(0)
    @Builder.Default
    @Schema(description = "Zero-based page number", defaultValue = "0")
    private int page = 0;

    @Min(1)
    @Max(100)
    @Builder.Default
    @Schema(description = "Page size (max 100)", defaultValue = "20")
    private int size = 20;

    @Builder.Default
    @Schema(description = "Sort field", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Builder.Default
    @Schema(description = "Sort direction (asc/desc)", defaultValue = "desc")
    private String sortDir = "desc";
}
