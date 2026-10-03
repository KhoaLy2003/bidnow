package com.bidnow.bidding.controller;

import com.bidnow.bidding.dto.request.BidHistoryQuery;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.BidHistoryResponse;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.service.BidHistoryService;
import com.bidnow.bidding.service.BidService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bids")
@RequiredArgsConstructor
@Tag(name = "Bids", description = "Bid placement")
public class BidController {

    private final BidService bidService;
    private final BidHistoryService bidHistoryService;

    @Operation(summary = "Place a bid",
            description = "Validates the bid, locks the bidder's auction deposit on their first bid, and applies the "
                    + "bid atomically in auction-service.")
    @PostMapping
    public ResponseEntity<BaseResponse<PlaceBidResponse>> placeBid(@AuthenticatedUserId UUID bidderId,
                                                                   @Valid @RequestBody PlaceBidRequest request) {
        PlaceBidResponse placed = bidService.placeBid(bidderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<PlaceBidResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Bid placed")
                        .data(placed)
                        .build());
    }

    @Operation(summary = "Bid history of an auction", description = "Public. Newest first, paginated (size ≤ 100).")
    @GetMapping("/auction/{auctionId}")
    public ResponseEntity<BaseResponse<PageResponse<BidHistoryResponse>>> auctionHistory(
            @PathVariable UUID auctionId, @Valid @ModelAttribute BidHistoryQuery query) {
        return ResponseEntity.ok(BaseResponse.success(bidHistoryService.auctionHistory(auctionId, query)));
    }

    @Operation(summary = "My bids on an auction", description = "The caller's own bids, newest first.")
    @GetMapping("/auction/{auctionId}/my-bids")
    public ResponseEntity<BaseResponse<PageResponse<BidHistoryResponse>>> myBids(
            @AuthenticatedUserId UUID bidderId, @PathVariable UUID auctionId,
            @Valid @ModelAttribute BidHistoryQuery query) {
        return ResponseEntity.ok(BaseResponse.success(bidHistoryService.myBids(auctionId, bidderId, query)));
    }
}
