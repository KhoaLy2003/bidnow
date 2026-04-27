package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidPlacedEvent {
    private UUID auctionId;
    private String auctionTitle;
    private UUID bidderId;
    private String bidderName;
    private BigDecimal bidAmount;
    private LocalDateTime bidTime;
    private UUID previousHighestBidderId; // Used for outbid alert
    private boolean isAntiSnipingTriggered; // True if auction extended
}
