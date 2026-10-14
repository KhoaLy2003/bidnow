package com.bidnow.media.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFormatsTest {

    @Test
    void money_usesDollarsWithGroupingAndTwoDecimals() {
        assertThat(NotificationFormats.money(new BigDecimal("1234.5"))).isEqualTo("$1,234.50");
        assertThat(NotificationFormats.money(new BigDecimal("105"))).isEqualTo("$105.00");
        assertThat(NotificationFormats.money(null)).isEmpty();
    }

    @Test
    void deadline_isUtcToTheMinute() {
        assertThat(NotificationFormats.deadline(Instant.parse("2026-10-03T10:00:59Z"))).isEqualTo("2026-10-03 10:00 UTC");
        assertThat(NotificationFormats.deadline(null)).isEmpty();
    }

    @Test
    void links_buildRelativeAndAbsoluteUrls() {
        NotificationLinks links = new NotificationLinks("http://localhost:3000/");
        java.util.UUID id = java.util.UUID.fromString("a0000000-0000-0000-0000-000000000001");

        assertThat(NotificationLinks.auctionPath(id)).isEqualTo("/auctions/" + id);
        assertThat(links.absolute(NotificationLinks.WALLET_PATH)).isEqualTo("http://localhost:3000/wallet");
    }
}
