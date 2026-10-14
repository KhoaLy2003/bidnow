package com.bidnow.media.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Delivers a notification to the user's STOMP sessions on this instance. Best-effort: the notification is
 * already stored, so a failed push only means the client sees it on its next REST fetch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationPusher {

    static final String USER_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void push(UserNotificationPush push) {
        try {
            messagingTemplate.convertAndSendToUser(push.userId().toString(), USER_QUEUE, push.message());
        } catch (RuntimeException ex) {
            log.warn("Notification push failed for user {}: {}", push.userId(), ex.getMessage());
        }
    }
}
