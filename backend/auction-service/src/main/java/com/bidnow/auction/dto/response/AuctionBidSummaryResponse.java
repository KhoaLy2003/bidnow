package com.bidnow.auction.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Live state of an active auction, as bidding-service needs it to build a bidder's "my bids" list. */
@Data
@Builder
public class AuctionBidSummaryResponse {
    private UUID auctionId;
    private String title;
    private String primaryImageUrl;
    private BigDecimal currentPrice;
    private UUID currentWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
}
