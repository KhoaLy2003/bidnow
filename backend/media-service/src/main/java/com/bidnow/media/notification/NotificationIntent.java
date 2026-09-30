package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What a handler wants delivered to one user: an in-app notification (always) plus an optional email.
 * {@code actionUrl} is a frontend-relative path (e.g. /auctions/{id}); {@code email} may be null.
 */
public record NotificationIntent(
        UUID userId,
        NotificationType type,
        String dedupKey,
        UUID auctionId,
        String title,
        String message,
        String actionUrl,
        Map<String, Object> metadata,
        EmailSpec email) {

    public NotificationIntent {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(dedupKey, "dedupKey");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
    }

    /**
     * @param templateBaseName template name without the language suffix, e.g. WELCOME_EMAIL
     * @param transactional    true = sent even if the user opted out of emails (payments, wins)
     */
    public record EmailSpec(String templateBaseName, Map<String, Object> variables, boolean transactional) {
    }
}
