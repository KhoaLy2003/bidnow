package com.bidnow.auction.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ApplyBidResponse {
    private UUID auctionId;
    private BigDecimal currentPrice;
    private UUID currentWinnerId;
    private UUID previousWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
    private boolean extended;
    private int extensionCount;
}
