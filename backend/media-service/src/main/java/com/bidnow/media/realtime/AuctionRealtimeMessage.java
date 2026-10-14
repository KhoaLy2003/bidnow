package com.bidnow.media.realtime;

import java.util.UUID;

/** STOMP envelope pushed to browsers: {@code {type, auctionId, payload}}. */
public record AuctionRealtimeMessage(String type, UUID auctionId, Object payload) {
    public static final String BID_PLACED = "BID_PLACED";
    public static final String AUCTION_EXTENDED = "AUCTION_EXTENDED";
    public static final String AUCTION_ENDED = "AUCTION_ENDED";
    public static final String AUCTION_CANCELLED = "AUCTION_CANCELLED";
}
