package com.bidnow.media.realtime;

import com.bidnow.media.dto.response.NotificationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotificationPushJsonTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private final NotificationResponse notification = NotificationResponse.builder()
            .id(UUID.randomUUID()).type("AUCTION_WON").title("You won").message("You won Vintage Watch")
            .actionUrl("/auctions/x").auctionId(UUID.randomUUID()).metadata(Map.of("amount", "105.00"))
            .read(false).createdAt(OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC))
            .build();

    @Test
    void kafkaPayload_roundTrips() throws Exception {
        UserNotificationPush push = new UserNotificationPush(UUID.randomUUID(),
                UserNotificationMessage.notification(notification, 3));

        UserNotificationPush read = mapper.readValue(mapper.writeValueAsString(push), UserNotificationPush.class);

        assertThat(read).isEqualTo(push);
    }

    @Test
    void stompMessage_hasContractShape() throws Exception {
        JsonNode root = mapper.readTree(mapper.writeValueAsString(UserNotificationMessage.notification(notification, 3)));

        assertThat(root.get("type").asText()).isEqualTo("NOTIFICATION");
        assertThat(root.get("unreadCount").asLong()).isEqualTo(3);
        assertThat(root.get("notification").get("read").asBoolean()).isFalse();
        assertThat(root.get("notification").get("createdAt").asText()).isEqualTo("2026-10-01T10:00:00Z");
    }
}
