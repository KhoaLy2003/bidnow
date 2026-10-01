package com.bidnow.media.notification.batch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A closed batching window: {@code count} alerts arrived after the immediate one. */
public record DueBatch(BidAlertKind kind, UUID userId, UUID auctionId, Instant windowStart, int count,
                       String auctionTitle, BigDecimal latestAmount) {
}
