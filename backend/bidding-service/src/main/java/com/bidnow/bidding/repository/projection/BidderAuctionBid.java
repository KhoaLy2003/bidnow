package com.bidnow.bidding.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row per auction a bidder has bid on: their highest amount there and when they last bid. */
public record BidderAuctionBid(UUID auctionId, BigDecimal highestAmount, LocalDateTime lastBidAt) {
}
