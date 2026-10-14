package com.bidnow.media.realtime;

import java.util.UUID;

/** Kafka payload on user-notification-push-topic: who to push to, and what. */
public record UserNotificationPush(UUID userId, UserNotificationMessage message) {
}
