package com.bidnow.auction.controller;

import com.bidnow.auction.dto.response.AuctionCategoryResponse;
import com.bidnow.auction.service.AuctionCategoryService;
import com.bidnow.common.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Categories of auctions")
public class AuctionCategoryController {
    private final AuctionCategoryService auctionCategoryService;

    /**
     * =============================================================
     * Get all active auction categories.
     *
     * @return ResponseEntity containing a BaseResponse with a list of AuctionCategoryResponse.
     *         HTTP 200 on success.
     * =============================================================
     */
    @Operation(summary = "Get all active auction categories")
    @ApiResponse(responseCode = "200", description = "Category list returned")
    @GetMapping()
    public ResponseEntity<BaseResponse<List<AuctionCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(BaseResponse.success(auctionCategoryService.getCategories()));
    }
}
