package com.bidnow.auction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Auction category summary")
public class AuctionCategoryResponse {

    @Schema(description = "Category UUID")
    private UUID id;

    @Schema(description = "Category name", example = "Electronics")
    private String name;

    @Schema(description = "URL-friendly slug", example = "electronics")
    private String slug;
}
