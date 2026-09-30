package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DedupKeysTest {

    private static final UUID A = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void formatsAreFixed() {
        assertThat(DedupKeys.welcome()).isEqualTo("WELCOME");
        assertThat(DedupKeys.auctionCreated(A)).isEqualTo("AUCTION_CREATED:" + A);
        assertThat(DedupKeys.won(A)).isEqualTo("AUCTION_WON:" + A);
        assertThat(DedupKeys.lost(A)).isEqualTo("AUCTION_LOST:" + A);
        assertThat(DedupKeys.payment("REQUIRED", A)).isEqualTo("PAYMENT_REQUIRED:" + A);
        assertThat(DedupKeys.refund(A)).isEqualTo("DEPOSIT_REFUNDED:" + A);
        assertThat(DedupKeys.cancelled(A)).isEqualTo("AUCTION_CANCELLED:" + A);
        assertThat(DedupKeys.firstBid(A)).isEqualTo("FIRST_BID:" + A);
        assertThat(DedupKeys.endingSoon(A, 15)).isEqualTo("ENDING_SOON:" + A + ":15");
        assertThat(DedupKeys.batch(NotificationType.BID_OUTBID, A, Instant.ofEpochMilli(1_700_000_000_123L)))
                .isEqualTo("BID_OUTBID:" + A + ":1700000000123");
    }

    @Test
    void extended_sharesAKeyWithinAFiveMinuteBucket() {
        String first = DedupKeys.extended(A, Instant.parse("2026-10-01T12:00:00Z"));
        String sameBucket = DedupKeys.extended(A, Instant.parse("2026-10-01T12:04:59Z"));
        String nextBucket = DedupKeys.extended(A, Instant.parse("2026-10-01T12:05:00Z"));

        assertThat(first).startsWith("AUCTION_EXTENDED:" + A + ":");
        assertThat(sameBucket).isEqualTo(first);
        assertThat(nextBucket).isNotEqualTo(first);
    }

    @Test
    void keysFitTheColumn() {
        assertThat(DedupKeys.batch(NotificationType.BID_OUTBID, A, Instant.now()).length()).isLessThanOrEqualTo(200);
    }
}
