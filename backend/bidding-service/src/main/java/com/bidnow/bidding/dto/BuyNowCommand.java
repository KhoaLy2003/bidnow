package com.bidnow.bidding.dto;

import java.util.UUID;

/** Body of auction-service's internal POST /api/v1/internal/auctions/{id}/buy-now. */
public record BuyNowCommand(UUID buyerId) {
}
