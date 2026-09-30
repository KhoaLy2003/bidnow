package com.bidnow.media.dto.response;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    private static Notification notification(LocalDateTime readAt) {
        Notification n = Notification.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).type(NotificationType.AUCTION_WON)
                .channel(NotificationChannel.IN_APP).title("You won").message("You won Vintage Watch")
                .actionUrl("/auctions/x").auctionId(UUID.randomUUID()).metadata(Map.of("amount", "105.00"))
                .dedupKey("AUCTION_WON:x").readAt(readAt)
                .build();
        n.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        return n;
    }

    @Test
    void from_mapsFields() {
        Notification n = notification(null);

        NotificationResponse response = NotificationResponse.from(n);

        assertThat(response.getId()).isEqualTo(n.getId());
        assertThat(response.getType()).isEqualTo("AUCTION_WON");
        assertThat(response.getTitle()).isEqualTo("You won");
        assertThat(response.getMessage()).isEqualTo("You won Vintage Watch");
        assertThat(response.getActionUrl()).isEqualTo("/auctions/x");
        assertThat(response.getAuctionId()).isEqualTo(n.getAuctionId());
        assertThat(response.getMetadata()).containsEntry("amount", "105.00");
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(response.isRead()).isFalse();
    }

    @Test
    void from_readAtSet_isRead() {
        assertThat(NotificationResponse.from(notification(LocalDateTime.of(2026, 10, 1, 11, 0))).isRead()).isTrue();
    }
}
