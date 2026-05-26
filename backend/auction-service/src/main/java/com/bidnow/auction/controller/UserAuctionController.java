package com.bidnow.auction.controller;

import com.bidnow.auction.dto.response.AuctionSummaryResponse;
import com.bidnow.auction.service.AuctionService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/auctions")
@RequiredArgsConstructor
@Tag(name = "Auctions", description = "Authenticated seller's auction listings)")
public class UserAuctionController {
    private final AuctionService auctionService;

    /**
     * =============================================================
     * List auctions owned by the authenticated seller.
     *
     * @param sellerId UUID of the authenticated seller
     * @param type filter type (default: "active")
     * @param categoryId optional category UUID to filter auctions
     * @param page zero-based page index
     * @param size page size
     * @return ResponseEntity containing a BaseResponse with a PageResponse of AuctionSummaryResponse.
     *         HTTP 200 on success.
     * =============================================================
     */
    @Operation(summary = "List auctions owned by the authenticated seller")
    @ApiResponse(responseCode = "200", description = "Auction list returned")
    @GetMapping("/")
    public ResponseEntity<BaseResponse<PageResponse<AuctionSummaryResponse>>> getMyAuctions(
            @AuthenticatedUserId UUID sellerId,
            @RequestParam(defaultValue = "active") String type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String sortBy = "history".equalsIgnoreCase(type) ? "updatedAt" : "endTime";
        String sortDir = "history".equalsIgnoreCase(type) ? "desc" : "asc";
        Pageable pageable = PaginationUtils.getPageable(page, size, sortBy, sortDir);
        PageResponse<AuctionSummaryResponse> response = auctionService.getMyAuctions(sellerId, type, categoryId, pageable);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
