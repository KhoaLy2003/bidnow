package com.bidnow.media.domain.enums;

public enum NotificationLanguage {
    EN,
    VI;

    /** Maps a user-service language code ("en", "vi") to a template language; unknown codes fall back to EN. */
    public static NotificationLanguage fromCode(String code) {
        return code != null && code.trim().equalsIgnoreCase("vi") ? VI : EN;
    }
}
