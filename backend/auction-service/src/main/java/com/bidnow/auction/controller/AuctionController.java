package com.bidnow.auction.controller;

import com.bidnow.auction.dto.request.CreateAuctionRequest;
import com.bidnow.auction.dto.request.UpdateAuctionRequest;
import com.bidnow.auction.dto.response.AuctionCategoryResponse;
import com.bidnow.auction.dto.response.AuctionResponse;
import com.bidnow.auction.dto.response.AuctionSummaryResponse;
import com.bidnow.auction.service.AuctionService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
@Tag(name = "Auctions", description = "Auction listing management")
public class AuctionController {

    private final AuctionService auctionService;

    @Operation(summary = "Get all active auction categories")
    @ApiResponse(responseCode = "200", description = "Category list returned")
    @GetMapping("/categories")
    public ResponseEntity<BaseResponse<List<AuctionCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(BaseResponse.success(auctionService.getCategories()));
    }

    @Operation(summary = "Create a new auction listing")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Auction created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or business rule violation"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @PostMapping
    public ResponseEntity<BaseResponse<AuctionResponse>> createAuction(
            @AuthenticatedUserId UUID sellerId,
            @Valid @RequestBody CreateAuctionRequest request) {
        AuctionResponse response = auctionService.createAuction(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<AuctionResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Auction created successfully")
                        .data(response)
                        .build());
    }

    @Operation(summary = "List auctions owned by the authenticated seller")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auction list returned")
    })
    @GetMapping("/my-auctions")
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

    @Operation(summary = "Update an auction (only allowed before it starts)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auction updated successfully"),
            @ApiResponse(responseCode = "400", description = "Auction cannot be modified"),
            @ApiResponse(responseCode = "403", description = "Not the auction owner"),
            @ApiResponse(responseCode = "404", description = "Auction or category not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<AuctionResponse>> updateAuction(
            @AuthenticatedUserId UUID sellerId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAuctionRequest request) {
        AuctionResponse response = auctionService.updateAuction(sellerId, id, request);
        return ResponseEntity.ok(BaseResponse.success("Auction updated successfully", response));
    }

    @Operation(summary = "Delete (soft) an auction (only allowed before it starts)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Auction deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Auction cannot be deleted"),
            @ApiResponse(responseCode = "403", description = "Not the auction owner"),
            @ApiResponse(responseCode = "404", description = "Auction not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuction(
            @AuthenticatedUserId UUID sellerId,
            @PathVariable UUID id) {
        auctionService.deleteAuction(sellerId, id);
        return ResponseEntity.noContent().build();
    }
}
