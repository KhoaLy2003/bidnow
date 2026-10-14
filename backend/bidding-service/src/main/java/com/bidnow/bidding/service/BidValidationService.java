package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fast pre-filter against the cached auction context. Mirrors auction-service's authoritative
 * apply-bid rules and their order; auction-service re-checks everything under a row lock.
 */
@Service
public class BidValidationService {

    public void preValidate(BidContext ctx, UUID bidderId, BigDecimal amount, Instant now) {
        if (!BidContext.STATUS_ACTIVE.equals(ctx.getStatus()) || !now.isBefore(ctx.getEndTime().toInstant())) {
            throw new ConflictException("Auction is not open for bidding", BiddingErrorCodes.AUCTION_NOT_OPEN);
        }
        if (ctx.getSellerId().equals(bidderId)) {
            throw new ForbiddenException("Sellers cannot bid on their own auction", BiddingErrorCodes.BID_OWN_AUCTION);
        }
        BigDecimal minimumBid = minimumBid(ctx);
        if (amount.compareTo(minimumBid) < 0) {
            throw new BidTooLowException(minimumBid);
        }
    }

    /** The first bid may equal the starting price (current price starts there); later bids must add the increment. */
    static BigDecimal minimumBid(BidContext ctx) {
        return ctx.getTotalBids() == 0
                ? ctx.getCurrentPrice()
                : ctx.getCurrentPrice().add(ctx.getBidIncrement());
    }
}
