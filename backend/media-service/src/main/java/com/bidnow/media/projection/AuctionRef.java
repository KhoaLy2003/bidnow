package com.bidnow.media.projection;

import java.time.OffsetDateTime;
import java.util.UUID;

/** media-service's projected view of an auction; any field but the id may be unknown (null). */
public record AuctionRef(UUID auctionId, String title, UUID sellerId, OffsetDateTime endTime) {
}
