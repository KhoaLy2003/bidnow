package com.bidnow.auction.dto.response;

import com.bidnow.auction.domain.enums.AuctionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BidContextResponse {
    private UUID auctionId;
    private String title;
    private UUID sellerId;
    private AuctionStatus status;
    private BigDecimal currentPrice;
    private BigDecimal bidIncrement;
    private BigDecimal depositAmount;
    private UUID currentWinnerId;
    private int totalBids;
    private OffsetDateTime endTime;
}
