package com.bidnow.media.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Payload shapes of {@link AuctionRealtimeMessage} — the browser-facing contract (spec §6). */
public final class RealtimePayloads {

    private RealtimePayloads() {
    }

    public record BidPlaced(UUID bidId, UUID bidderId, String bidderName, BigDecimal amount, OffsetDateTime placedAt,
                            Integer totalBids, OffsetDateTime endTime, boolean antiSnipingTriggered) {
    }

    public record AuctionExtended(Instant previousEndTime, Instant newEndTime, Integer extensionCount) {
    }

    public record AuctionEnded(UUID winnerId, BigDecimal finalPrice, Instant endedAt) {
    }

    public record AuctionCancelled(String reason) {
    }

    public record Outbid(String auctionTitle, BigDecimal currentPrice, String newLeaderName) {
    }
}
