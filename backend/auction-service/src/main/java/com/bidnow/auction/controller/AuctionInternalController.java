package com.bidnow.auction.controller;

import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.service.AuctionBidService;
import com.bidnow.common.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/auctions")
@RequiredArgsConstructor
@Tag(name = "Internal Auction Interface", description = "Service-to-service bid context and atomic bid application")
public class AuctionInternalController {

    private final AuctionBidService auctionBidService;

    @Operation(summary = "Get bid context (Internal)",
            description = "Price, increment, deposit, status and end time used by bidding-service to pre-validate bids.")
    @GetMapping("/{id}/bid-context")
    public ResponseEntity<BaseResponse<BidContextResponse>> getBidContext(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(auctionBidService.getBidContext(id)));
    }

    @Operation(summary = "Apply bid (Internal)",
            description = "Atomically re-validates and applies a bid under a row lock. Idempotent on bidId.")
    @PostMapping("/{id}/bids")
    public ResponseEntity<BaseResponse<ApplyBidResponse>> applyBid(@PathVariable UUID id,
                                                                   @Valid @RequestBody ApplyBidRequest request) {
        return ResponseEntity.ok(BaseResponse.success(auctionBidService.applyBid(id, request)));
    }
}
