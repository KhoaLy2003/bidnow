package com.bidnow.auction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Active auction count per category")
public class CategoryCountResponse {

    @Schema(description = "Category UUID")
    private UUID categoryId;

    @Schema(description = "Category name")
    private String categoryName;

    @Schema(description = "Category URL slug")
    private String slug;

    @Schema(description = "Number of active auctions in this category")
    private Long count;
}
