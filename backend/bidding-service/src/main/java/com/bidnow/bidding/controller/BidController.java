package com.bidnow.bidding.controller;

import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.bidding.service.BidValidationService;
import com.bidnow.common.annotation.AuthenticatedUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bids")
@RequiredArgsConstructor
@Tag(name = "Bids", description = "Bid placement")
public class BidController {

    private final AuctionContextCacheService contextCache;
    private final BidValidationService bidValidationService;
    private final Clock clock;

    @Operation(summary = "Place a bid",
            description = "Pre-validates against the cached auction context. Placement (deposit lock + apply-bid) "
                    + "lands in BID-102; until then a valid bid returns 501.")
    @PostMapping
    public ResponseEntity<Void> placeBid(@AuthenticatedUserId UUID bidderId,
                                         @Valid @RequestBody PlaceBidRequest request) {
        preValidateWithRefresh(request.getAuctionId(), bidderId, request.getAmount());
        // BID-102 (Story 3) replaces this with the full placement flow and 201 Created.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * A cached context can be stale (the auction has started, or its end time was extended by
     * anti-sniping), so a 409 must reflect auction-service's current state: on a conflict the
     * context is refreshed once and re-validated. Other rejections are never refreshed - a stale
     * price only lowers the minimum bid and the seller never changes.
     */
    private void preValidateWithRefresh(UUID auctionId, UUID bidderId, BigDecimal amount) {
        BidContext ctx = contextCache.get(auctionId);
        try {
            bidValidationService.preValidate(ctx, bidderId, amount, clock.instant());
        } catch (ConflictException ex) {
            BidContext fresh = contextCache.refresh(auctionId);
            bidValidationService.preValidate(fresh, bidderId, amount, clock.instant());
        }
    }
}
