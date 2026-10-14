package com.bidnow.bidding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Auction state used to pre-validate bids. Deserialized from auction-service's internal
 * {@code GET /api/v1/internal/auctions/{id}/bid-context} and cached as JSON in Redis — field names
 * must stay identical to auction-service's {@code BidContextResponse}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BidContext {
    public static final String STATUS_ACTIVE = "ACTIVE";

    private UUID auctionId;
    private String title;
    private UUID sellerId;
    private String status;
    private BigDecimal currentPrice;
    private BigDecimal bidIncrement;
    private BigDecimal depositAmount;
    private UUID currentWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
}
