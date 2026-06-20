package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionEndedEvent {
    private UUID auctionId;
    private String auctionTitle;
    private UUID sellerId;
    private UUID winnerId;
    private BigDecimal winningBidAmount;
    private List<UUID> loserIds; // For sending auction lost notification
    private Integer totalBids;
    private Instant endedAt;
    private String closureSource; // SCHEDULER | ADMIN | BUY_NOW
}
