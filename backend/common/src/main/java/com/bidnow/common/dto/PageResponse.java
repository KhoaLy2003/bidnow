/*
 * BidNow Auction System
 */
package com.bidnow.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated response wrapper")
public class PageResponse<T> {
    @Schema(description = "List of data items")
    private List<T> data;

    @Schema(description = "Pagination metadata")
    private PaginationMeta pagination;

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .data(page.getContent())
                .pagination(PaginationMeta.builder()
                        .page(page.getNumber())
                        .limit(page.getSize())
                        .total(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .build())
                .build();
    }
}
