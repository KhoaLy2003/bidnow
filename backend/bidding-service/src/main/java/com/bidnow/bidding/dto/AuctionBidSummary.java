package com.bidnow.bidding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Live state of an active auction; mirrors auction-service's {@code AuctionBidSummaryResponse}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuctionBidSummary {
    private UUID auctionId;
    private String title;
    private String primaryImageUrl;
    private BigDecimal currentPrice;
    private UUID currentWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
}
