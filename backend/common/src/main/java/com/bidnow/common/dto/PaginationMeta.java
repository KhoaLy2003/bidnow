package com.bidnow.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Metadata for paginated results")
public class PaginationMeta {
    @Schema(description = "Current page number (zero-based)", example = "0")
    private int page;

    @Schema(description = "Number of items per page", example = "10")
    private int limit;

    @Schema(description = "Total number of items across all pages", example = "100")
    private long total;

    @Schema(description = "Total number of pages", example = "10")
    private int totalPages;

    @Schema(description = "Whether there is a next page", example = "true")
    private boolean hasNext;

    @Schema(description = "Whether there is a previous page", example = "false")
    private boolean hasPrev;
}
