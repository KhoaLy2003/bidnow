package com.bidnow.bidding.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of auction-service's internal POST /api/v1/internal/auctions/{id}/bids. */
public record ApplyBidCommand(UUID bidId, UUID bidderId, BigDecimal amount) {
}
