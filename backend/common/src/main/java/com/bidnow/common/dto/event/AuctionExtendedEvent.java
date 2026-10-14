package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Anti-sniping extension of an auction's end time, published by auction-service after commit. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionExtendedEvent {
    private UUID auctionId;
    private String auctionTitle;
    private Instant previousEndTime;
    private Instant newEndTime;
    private Integer extensionCount;
    private UUID triggeredByBidId;
    private UUID triggeredByUserId;
}
