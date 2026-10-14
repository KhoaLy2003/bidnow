package com.bidnow.media.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLanguageTest {

    @Test
    void fromCode_mapsVietnameseAndDefaultsToEnglish() {
        assertThat(NotificationLanguage.fromCode("vi")).isEqualTo(NotificationLanguage.VI);
        assertThat(NotificationLanguage.fromCode(" VI ")).isEqualTo(NotificationLanguage.VI);
        assertThat(NotificationLanguage.fromCode("en")).isEqualTo(NotificationLanguage.EN);
        assertThat(NotificationLanguage.fromCode("fr")).isEqualTo(NotificationLanguage.EN);
        assertThat(NotificationLanguage.fromCode(null)).isEqualTo(NotificationLanguage.EN);
    }
}
