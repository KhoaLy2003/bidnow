package com.bidnow.bidding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** auction-service's authoritative state after an applied bid (mirrors its ApplyBidResponse). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplyBidResult {
    private UUID auctionId;
    private BigDecimal currentPrice;
    private UUID currentWinnerId;
    private UUID previousWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
    private boolean extended;
    private int extensionCount;
}
