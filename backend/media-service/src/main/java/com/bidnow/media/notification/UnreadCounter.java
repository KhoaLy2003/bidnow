package com.bidnow.media.notification;

import com.bidnow.media.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Counts a user's unread notifications in its own short read-only transaction. Called from after-commit hooks so
 * the pushed badge count includes every committed change (the outer transaction is already committed there).
 */
@Component
@RequiredArgsConstructor
public class UnreadCounter {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long count(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId);
    }
}
