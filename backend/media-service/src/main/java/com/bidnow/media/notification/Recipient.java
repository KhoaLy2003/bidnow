package com.bidnow.media.notification;

import com.bidnow.media.domain.enums.NotificationLanguage;

import java.util.UUID;

/** Where and how to email a user. {@code email} is null when unknown; {@code emailOptIn} gates non-transactional emails. */
public record Recipient(UUID userId, String email, NotificationLanguage language, boolean emailOptIn) {

    public static Recipient unknown(UUID userId) {
        return new Recipient(userId, null, NotificationLanguage.EN, true);
    }
}
