package com.bidnow.auction.controller;

import com.bidnow.auction.dto.request.CreateAuctionRequest;
import com.bidnow.auction.dto.request.UpdateAuctionRequest;
import com.bidnow.auction.dto.response.AuctionResponse;
import com.bidnow.auction.service.AuctionService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
@Tag(name = "Auctions", description = "Public auction browsing and core CRUD operations")
public class AuctionController {

    private final AuctionService auctionService;

    /**
     * =============================================================
     * Create a new auction listing.
     *
     * @param sellerId UUID of the authenticated seller (resolved from security/context)
     * @param request CreateAuctionRequest validated request body containing auction details
     * @return ResponseEntity containing a BaseResponse with the created AuctionResponse.
     *         HTTP 201 on success. Possible responses: 201, 400 (validation/business), 404 (category not found).
     * =============================================================
     */
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

    /**
     * =============================================================
     * Update an auction (only allowed before it starts).
     *
     * @param sellerId UUID of the authenticated seller
     * @param id UUID of the auction to update (path variable)
     * @param request UpdateAuctionRequest validated request body with update fields
     * @return ResponseEntity containing a BaseResponse with the updated AuctionResponse.
     *         HTTP 200 on success. Possible responses: 200, 400 (cannot modify), 403 (not owner), 404 (not found).
     * =============================================================
     */
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

    /**
     * =============================================================
     * Delete (soft) an auction (only allowed before it starts).
     *
     * @param sellerId UUID of the authenticated seller
     * @param id UUID of the auction to delete (path variable)
     * @return ResponseEntity with no content (HTTP 204) on successful deletion.
     *         Possible responses: 204, 400 (cannot delete), 403 (not owner), 404 (not found).
     * ------------------------------------------------------------
     */
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
