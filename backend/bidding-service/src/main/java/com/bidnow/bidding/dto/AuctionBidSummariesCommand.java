package com.bidnow.bidding.dto;

import java.util.List;
import java.util.UUID;

/** Body of auction-service's {@code POST /api/v1/internal/auctions/bid-summaries} (≤100 ids). */
public record AuctionBidSummariesCommand(List<UUID> auctionIds) {
}
