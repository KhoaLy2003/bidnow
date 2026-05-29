package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionCancelledEvent {
    private UUID auctionId;
    private UUID sellerId;
    private String previousStatus;
    private String reason;
    private Instant cancelledAt;
}
