package com.bidnow.media.kafka;

import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.realtime.UserNotificationPush;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fans a stored notification out to every media instance (see {@link UserNotificationPushConsumer}), so it
 * reaches the user whichever instance holds their WebSocket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationPushPublisher {

    public static final String TOPIC = "user-notification-push-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(UUID userId, UserNotificationMessage message) {
        kafkaTemplate.send(TOPIC, userId.toString(), new UserNotificationPush(userId, message))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Push for user {} not published - the notification is still in the inbox: {}",
                                userId, ex.getMessage());
                    }
                });
    }
}
