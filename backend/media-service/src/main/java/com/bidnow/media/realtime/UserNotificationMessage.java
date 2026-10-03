package com.bidnow.media.realtime;

import com.bidnow.media.dto.response.NotificationResponse;

/** STOMP envelope on {@code /user/queue/notifications}: {@code {type, notification, unreadCount}}. */
public record UserNotificationMessage(String type, NotificationResponse notification, long unreadCount) {

    public static final String NOTIFICATION = "NOTIFICATION";

    public static UserNotificationMessage notification(NotificationResponse notification, long unreadCount) {
        return new UserNotificationMessage(NOTIFICATION, notification, unreadCount);
    }
}
