package com.bidnow.media.kafka;

import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.realtime.UserNotificationPush;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trips the push payload through the same Spring Kafka serde media-service configures in application.yml. */
class UserNotificationPushKafkaSerdeTest {

    @Test
    void payloadSurvivesJsonSerializerAndJsonDeserializer() {
        UUID userId = UUID.randomUUID();
        NotificationResponse notification = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .type("WELCOME")
                .title("Welcome")
                .message("Welcome to BidNow")
                .actionUrl("/auctions")
                .auctionId(UUID.randomUUID())
                .metadata(Map.of("k", "v", "other", "x"))
                .read(false)
                .createdAt(LocalDateTime.of(2026, 9, 30, 12, 34, 56, 123_000_000))
                .build();
        UserNotificationPush original = new UserNotificationPush(userId, UserNotificationMessage.notification(notification, 3));

        RecordHeaders headers = new RecordHeaders();
        byte[] bytes;
        try (JsonSerializer<UserNotificationPush> serializer = new JsonSerializer<>()) {
            bytes = serializer.serialize(UserNotificationPushPublisher.TOPIC, headers, original);
        }

        try (JsonDeserializer<UserNotificationPush> deserializer = new JsonDeserializer<>()) {
            deserializer.configure(Map.of(JsonDeserializer.TRUSTED_PACKAGES, "*"), false);
            UserNotificationPush result = deserializer.deserialize(UserNotificationPushPublisher.TOPIC, headers, bytes);
            assertThat(result).isEqualTo(original);
        }
    }
}
