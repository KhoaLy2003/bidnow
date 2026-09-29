package com.bidnow.auction.service;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.exception.BidTooLowException;
import com.bidnow.auction.exception.ConflictException;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Internal bid operations called by bidding-service. auction-service is the source of truth for
 * price, winner and end time. {@link #applyBid} re-validates and mutates under a row lock, so it
 * serializes with closure and cancellation, which take the same lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBidService {

    private final AuctionItemRepository auctionItemRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BidContextResponse getBidContext(UUID auctionId) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElseThrow(() -> notFound(auctionId));
        return BidContextResponse.builder()
                .auctionId(auction.getId())
                .title(auction.getTitle())
                .sellerId(auction.getSellerId())
                .status(auction.getStatus())
                .currentPrice(auction.getCurrentPrice())
                .bidIncrement(auction.getBidIncrement())
                .depositAmount(auction.getDepositAmount())
                .currentWinnerId(auction.getCurrentWinnerId())
                .totalBids(auction.getTotalBids())
                .endTime(auction.getEndTime())
                .build();
    }

    /**
     * Applies a bid atomically. Idempotent on {@code bidId}: replaying the last applied bid returns
     * the current state without re-validating or changing anything.
     */
    // Timeout must stay below bidding-service's 2 s Feign read timeout; it bounds the row-lock wait.
    @Transactional(timeout = 1)
    public ApplyBidResponse applyBid(UUID auctionId, ApplyBidRequest request) {
        AuctionItem auction = auctionItemRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> notFound(auctionId));

        if (request.getBidId().equals(auction.getLastBidId())) {
            log.info("Replay of bid {} on auction {} — returning current state", request.getBidId(), auctionId);
            return toResponse(auction, null);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (auction.getStatus() != AuctionStatus.ACTIVE || !now.isBefore(auction.getEndTime())) {
            throw new ConflictException("Auction is not open for bidding", AuctionErrorCodes.AUCTION_NOT_OPEN);
        }
        if (auction.getSellerId().equals(request.getBidderId())) {
            throw new ForbiddenException("Sellers cannot bid on their own auction", AuctionErrorCodes.BID_OWN_AUCTION);
        }
        BigDecimal minimumBid = minimumBid(auction);
        if (request.getAmount().compareTo(minimumBid) < 0) {
            throw new BidTooLowException(minimumBid);
        }

        UUID previousWinnerId = auction.getCurrentWinnerId();
        auction.setCurrentPrice(request.getAmount());
        auction.setCurrentWinnerId(request.getBidderId());
        auction.setTotalBids(auction.getTotalBids() + 1);
        auction.setLastBidId(request.getBidId());
        auctionItemRepository.save(auction);

        log.info("Applied bid {} on auction {}: price={}, bidder={}, totalBids={}",
                request.getBidId(), auctionId, request.getAmount(), request.getBidderId(), auction.getTotalBids());
        return toResponse(auction, previousWinnerId);
    }

    /** The first bid may equal the starting price (current_price starts there); later bids must add the increment. */
    static BigDecimal minimumBid(AuctionItem auction) {
        return auction.getTotalBids() == 0
                ? auction.getCurrentPrice()
                : auction.getCurrentPrice().add(auction.getBidIncrement());
    }

    private static ApplyBidResponse toResponse(AuctionItem auction, UUID previousWinnerId) {
        return ApplyBidResponse.builder()
                .auctionId(auction.getId())
                .currentPrice(auction.getCurrentPrice())
                .currentWinnerId(auction.getCurrentWinnerId())
                .previousWinnerId(previousWinnerId)
                .totalBids(auction.getTotalBids())
                .endTime(auction.getEndTime())
                .extended(false)
                .extensionCount(auction.getExtensionCount())
                .build();
    }

    private static NotFoundException notFound(UUID auctionId) {
        return new NotFoundException("Auction not found: " + auctionId, AuctionErrorCodes.AUCTION_NOT_FOUND);
    }
}
