package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** An ACTIVE auction reached a platform-default "ending soon" threshold; published by auction-service after commit. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionEndingSoonEvent {
    private UUID auctionId;
    private String auctionTitle;
    private UUID sellerId;
    private Instant endTime;
    private Integer thresholdMinutes;
}
