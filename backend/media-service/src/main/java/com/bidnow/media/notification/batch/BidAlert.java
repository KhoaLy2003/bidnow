package com.bidnow.media.notification.batch;

import java.math.BigDecimal;
import java.util.UUID;

/** One bid that should alert one user; {@code amount} is the new current price. */
public record BidAlert(BidAlertKind kind, UUID userId, UUID auctionId, UUID bidId, String auctionTitle,
                       BigDecimal amount) {
}
