package com.bidnow.bidding.service;

import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.BuyNowCommand;
import com.bidnow.bidding.dto.BuyNowResult;
import com.bidnow.bidding.dto.request.BuyNowRequest;
import com.bidnow.bidding.dto.response.BuyNowResponse;
import com.bidnow.bidding.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Buy It Now: pre-validate against the cached context → lock the auction deposit (same gate as a first
 * bid) → ask auction-service to complete the auction under its row lock. No DB write happens here: a
 * buy-now is not stored as a bid. If the auction rejects after the deposit lock (lost race), the lock
 * stays as a registration; wallet-service refunds it when its auction-ended / auction-cancelled sweep runs,
 * provided the lock landed before that sweep (a lock committed after the sweep is not released
 * automatically - the same race class as the bid path; see the spec's Known Limitations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuyNowService {

    private final AuctionContextCacheService contextCache;
    private final BidValidationService bidValidationService;
    private final DepositGate depositGate;
    private final AuctionBidGateway auctionBidGateway;
    private final Clock clock;

    public BuyNowResponse buyNow(UUID buyerId, BuyNowRequest request) {
        UUID auctionId = request.getAuctionId();
        BidContext ctx = preValidateWithRefresh(auctionId, buyerId);
        depositGate.ensureLocked(buyerId, ctx);

        BuyNowResult result;
        try {
            result = auctionBidGateway.buyNow(auctionId, new BuyNowCommand(buyerId));
        } catch (ConflictException ex) {
            log.warn("Buy It Now for auction {} by buyer {} rejected by auction-service ({}) after the deposit gate - the deposit lock stays as a registration; it may need manual release if the auction has already settled",
                    auctionId, buyerId, ex.getErrorCode());
            throw ex;
        } finally {
            // success ends the auction and any failure means our cached view was wrong: drop it either way
            contextCache.evict(auctionId);
        }
        log.info("Buy It Now completed: auction {} bought by {} at {}", auctionId, buyerId, result.getPrice());
        return BuyNowResponse.builder()
                .auctionId(auctionId)
                .price(result.getPrice())
                .endedAt(result.getEndedAt())
                .build();
    }

    /**
     * A cached context can be stale (older than this field, or the auction has since moved), so a 409
     * (not open / not available, including a null buy-now price) refreshes the context once and
     * re-validates. Other rejections are never refreshed.
     */
    private BidContext preValidateWithRefresh(UUID auctionId, UUID buyerId) {
        BidContext ctx = contextCache.get(auctionId);
        try {
            bidValidationService.preValidateBuyNow(ctx, buyerId, clock.instant());
            return ctx;
        } catch (ConflictException ex) {
            BidContext fresh = contextCache.refresh(auctionId);
            bidValidationService.preValidateBuyNow(fresh, buyerId, clock.instant());
            return fresh;
        }
    }
}
