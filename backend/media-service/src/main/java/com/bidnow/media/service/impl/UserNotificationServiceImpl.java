package com.bidnow.media.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.repository.NotificationRepository;
import com.bidnow.media.repository.NotificationSpecifications;
import com.bidnow.media.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/** Inbox reads and edits, always scoped to the caller's own non-deleted notifications. */
@Service
@RequiredArgsConstructor
public class UserNotificationServiceImpl implements UserNotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, NotificationQuery query) {
        return PageResponse.of(repository
                .findAll(NotificationSpecifications.inbox(userId, query, clock.getZone()), query.toPageable())
                .map(NotificationResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId);
    }

    @Override
    @Transactional
    public NotificationResponse get(UUID userId, UUID id) {
        return markRead(userId, id);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification notification = find(userId, id);
        if (notification.getReadAt() == null) {
            notification.setReadAt(now());
        }
        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public NotificationResponse markUnread(UUID userId, UUID id) {
        Notification notification = find(userId, id);
        notification.setReadAt(null);
        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, now());
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        find(userId, id).setDeletedAt(now());
    }

    @Override
    @Transactional
    public int deleteAll(UUID userId) {
        return repository.softDeleteAll(userId, now());
    }

    @Override
    @Transactional
    public int deleteRead(UUID userId) {
        return repository.softDeleteRead(userId, now());
    }

    private Notification find(UUID userId, UUID id) {
        return repository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found", ErrorCodes.NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
