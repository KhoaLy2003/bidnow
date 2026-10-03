package com.bidnow.media.service;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;

import java.util.UUID;

/** The caller's own inbox. Unknown, deleted or other users' notification ids are "not found". */
public interface UserNotificationService {

    PageResponse<NotificationResponse> list(UUID userId, NotificationQuery query);

    long unreadCount(UUID userId);

    /** Returns the notification and marks it read. */
    NotificationResponse get(UUID userId, UUID id);

    NotificationResponse markRead(UUID userId, UUID id);

    NotificationResponse markUnread(UUID userId, UUID id);

    /** @return how many notifications were marked read */
    int markAllRead(UUID userId);

    void delete(UUID userId, UUID id);

    /** @return how many notifications were deleted */
    int deleteAll(UUID userId);

    /** @return how many read notifications were deleted */
    int deleteRead(UUID userId);
}
