package com.bidnow.bidding.service;

import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.kafka.BidEventPublisher;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Places a bid (spec §3): pre-validate → lock deposit → [insert bid → apply in auction-service] →
 * after commit: refresh cache, publish event. The deposit lock and all post-commit work stay outside
 * the DB transaction; only the insert and the (fast, bounded) apply-bid call run inside it, so a
 * rejected or failed apply-bid rolls the insert back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final AuctionContextCacheService contextCache;
    private final BidValidationService bidValidationService;
    private final DepositGate depositGate;
    private final BidRepository bidRepository;
    private final AuctionBidGateway auctionBidGateway;
    private final UserSummaryCacheService userSummaries;
    private final BidEventPublisher bidEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PlaceBidResponse placeBid(UUID bidderId, PlaceBidRequest request) {
        UUID auctionId = request.getAuctionId();
        BigDecimal amount = request.getAmount();

        BidContext ctx = preValidateWithRefresh(auctionId, bidderId, amount);
        depositGate.ensureLocked(bidderId, ctx);

        UUID bidId = UUID.randomUUID();
        ApplyBidResult result = persistAndApply(bidId, auctionId, bidderId, amount);

        contextCache.put(ctx.toBuilder()
                .currentPrice(result.getCurrentPrice())
                .currentWinnerId(result.getCurrentWinnerId())
                .totalBids(result.getTotalBids())
                .endTime(result.getEndTime())
                .build());
        bidEventPublisher.publishBidPlaced(BidPlacedEvent.builder()
                .bidId(bidId)
                .auctionId(auctionId)
                .auctionTitle(ctx.getTitle())
                .bidderId(bidderId)
                .bidderName(userSummaries.displayName(bidderId))
                .bidAmount(amount)
                .bidTime(LocalDateTime.now(clock))
                .previousHighestBidderId(result.getPreviousWinnerId())
                .isAntiSnipingTriggered(result.isExtended())
                .totalBids(result.getTotalBids())
                .endTime(result.getEndTime())
                .build());

        return PlaceBidResponse.builder()
                .bidId(bidId)
                .auctionId(auctionId)
                .amount(amount)
                .placedAt(OffsetDateTime.now(clock))
                .currentPrice(result.getCurrentPrice())
                .totalBids(result.getTotalBids())
                .endTime(result.getEndTime())
                .extended(result.isExtended())
                .build();
    }

    /**
     * A cached context can be stale (the auction has started, or its end time was extended by
     * anti-sniping), so a 409 must reflect auction-service's current state: on a conflict the
     * context is refreshed once and re-validated. Other rejections are never refreshed - a stale
     * price only lowers the minimum bid and the seller never changes.
     */
    private BidContext preValidateWithRefresh(UUID auctionId, UUID bidderId, BigDecimal amount) {
        BidContext ctx = contextCache.get(auctionId);
        try {
            bidValidationService.preValidate(ctx, bidderId, amount, clock.instant());
            return ctx;
        } catch (ConflictException ex) {
            BidContext fresh = contextCache.refresh(auctionId);
            bidValidationService.preValidate(fresh, bidderId, amount, clock.instant());
            return fresh;
        }
    }

    private ApplyBidResult persistAndApply(UUID bidId, UUID auctionId, UUID bidderId, BigDecimal amount) {
        AtomicBoolean applied = new AtomicBoolean(false);
        try {
            return transactionTemplate.execute(status -> {
                Bid bid = bidRepository.saveAndFlush(Bid.builder()
                        .id(bidId)
                        .auctionId(auctionId)
                        .bidderId(bidderId)
                        .amount(amount)
                        .build());
                ApplyBidResult result = auctionBidGateway.apply(auctionId, new ApplyBidCommand(bidId, bidderId, amount));
                applied.set(true);
                bid.setAntiSnipingTriggered(result.isExtended());
                return result;
            });
        } catch (ConflictException | BidTooLowException ex) {
            // auction-service's authoritative state disagreed with our cached context
            if (ex instanceof ConflictException conflict
                    && BiddingErrorCodes.AUCTION_NOT_OPEN.equals(conflict.getErrorCode())) {
                log.warn("Bid {} rejected by auction-service as AUCTION_NOT_OPEN for user {} on auction {} after deposit gate"
                        + " - deposit may need release if the auction has already settled", bidId, bidderId, auctionId);
            }
            contextCache.evict(auctionId);
            throw ex;
        } catch (RuntimeException ex) {
            if (applied.get()) {
                log.error("CRITICAL: bid {} on auction {} was applied by auction-service but the local commit failed "
                        + "- auction last_bid_id = {} has no bids row; reconcile manually", bidId, auctionId, bidId, ex);
            }
            throw ex;
        }
    }
}
