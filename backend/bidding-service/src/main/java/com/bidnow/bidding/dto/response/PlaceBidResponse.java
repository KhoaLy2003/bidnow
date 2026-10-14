package com.bidnow.bidding.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PlaceBidResponse {
    private UUID bidId;
    private UUID auctionId;
    private BigDecimal amount;
    private OffsetDateTime placedAt;
    private BigDecimal currentPrice;
    private int totalBids;
    private OffsetDateTime endTime;
    private boolean extended;
}
