package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency keys for {@code media_notifications (user_id, dedup_key)}. One key = one notification per user,
 * so a redelivered event is a no-op. Formats are part of the stored data - never change an existing one.
 */
public final class DedupKeys {

    private static final long EXTENSION_BUCKET_SECONDS = 300;

    private DedupKeys() {
    }

    public static String welcome() {
        return "WELCOME";
    }

    public static String auctionCreated(UUID auctionId) {
        return "AUCTION_CREATED:" + auctionId;
    }

    public static String won(UUID auctionId) {
        return "AUCTION_WON:" + auctionId;
    }

    public static String lost(UUID auctionId) {
        return "AUCTION_LOST:" + auctionId;
    }

    public static String unsold(UUID auctionId) {
        return "AUCTION_UNSOLD:" + auctionId;
    }

    public static String payment(String paymentType, UUID auctionId) {
        return "PAYMENT_" + paymentType + ":" + auctionId;
    }

    public static String refund(UUID auctionId) {
        return "DEPOSIT_REFUNDED:" + auctionId;
    }

    public static String cancelled(UUID auctionId) {
        return "AUCTION_CANCELLED:" + auctionId;
    }

    /**
     * One notification per extension; extensions whose new end times fall in the same 5-minute bucket collapse into
     * one (only possible when the configured extension is shorter than 5 minutes).
     */
    public static String extended(UUID auctionId, Instant at) {
        return "AUCTION_EXTENDED:" + auctionId + ":" + Math.floorDiv(at.getEpochSecond(), EXTENSION_BUCKET_SECONDS);
    }

    public static String firstBid(UUID auctionId) {
        return "FIRST_BID:" + auctionId;
    }

    public static String batch(NotificationType type, UUID auctionId, Instant windowStart) {
        return type.name() + ":" + auctionId + ":" + windowStart.toEpochMilli();
    }

    public static String endingSoon(UUID auctionId, int thresholdMinutes) {
        return "ENDING_SOON:" + auctionId + ":" + thresholdMinutes;
    }
}
