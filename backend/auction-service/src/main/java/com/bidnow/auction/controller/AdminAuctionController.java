package com.bidnow.auction.controller;

import com.bidnow.auction.dto.request.AdminAuctionFilterRequest;
import com.bidnow.auction.dto.request.AdminAuctionReasonRequest;
import com.bidnow.auction.dto.response.AdminAuctionDetailResponse;
import com.bidnow.auction.dto.response.AdminAuctionSummaryResponse;
import com.bidnow.auction.dto.response.SellerAuctionResponse;
import com.bidnow.auction.service.AdminAuctionService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/auctions")
@RequiredArgsConstructor
@Tag(name = "Admin Auction Moderation", description = "Endpoints for platform administrators to moderate auctions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuctionController {

    private final AdminAuctionService adminAuctionService;

    @Operation(summary = "Browse all auctions (Admin only)")
    @ApiResponse(responseCode = "200", description = "Auction list returned")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminAuctionSummaryResponse>>> listAuctions(
            @Valid @ModelAttribute AdminAuctionFilterRequest filter) {
        PageResponse<AdminAuctionSummaryResponse> response = adminAuctionService.listAuctions(filter);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Get auction detail with full status history (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auction detail returned"),
            @ApiResponse(responseCode = "404", description = "Auction not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<AdminAuctionDetailResponse>> getAuctionDetail(@PathVariable UUID id) {
        AdminAuctionDetailResponse response = adminAuctionService.getAuctionDetail(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Reject a SCHEDULED auction (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auction rejected successfully"),
            @ApiResponse(responseCode = "400", description = "Auction not in SCHEDULED status or blank reason"),
            @ApiResponse(responseCode = "404", description = "Auction not found")
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<BaseResponse<SellerAuctionResponse>> rejectAuction(
            @Parameter(hidden = true) @AuthenticatedUserId UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody AdminAuctionReasonRequest request) {
        SellerAuctionResponse response = adminAuctionService.rejectAuction(adminId, id, request);
        return ResponseEntity.ok(BaseResponse.success("Auction rejected successfully", response));
    }

    @Operation(summary = "Cancel an ACTIVE auction (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auction cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Auction not ACTIVE or blank reason"),
            @ApiResponse(responseCode = "404", description = "Auction not found")
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BaseResponse<SellerAuctionResponse>> cancelAuction(
            @Parameter(hidden = true) @AuthenticatedUserId UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody AdminAuctionReasonRequest request) {
        SellerAuctionResponse response = adminAuctionService.cancelAuction(adminId, id, request);
        return ResponseEntity.ok(BaseResponse.success("Auction cancelled successfully", response));
    }

    @Operation(summary = "Force-close an ACTIVE auction early, crowning the current highest bidder (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auction force-closed successfully"),
            @ApiResponse(responseCode = "400", description = "Auction not ACTIVE or has no bids"),
            @ApiResponse(responseCode = "404", description = "Auction not found")
    })
    @PostMapping("/{id}/force-close")
    public ResponseEntity<BaseResponse<SellerAuctionResponse>> forceCloseAuction(
            @Parameter(hidden = true) @AuthenticatedUserId UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminAuctionReasonRequest request) {
        SellerAuctionResponse response = adminAuctionService.forceCloseAuction(adminId, id, request);
        return ResponseEntity.ok(BaseResponse.success("Auction force-closed successfully", response));
    }
}
