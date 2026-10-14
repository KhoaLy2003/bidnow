package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Picks the active {@code {baseName}_{LANG}} template; a missing or inactive VI template falls back to EN. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateResolver {

    private final NotificationTemplateRepository templateRepository;

    public Optional<NotificationTemplate> resolve(String baseName, NotificationLanguage language) {
        Optional<NotificationTemplate> template = find(baseName, language);
        if (template.isEmpty() && language != NotificationLanguage.EN) {
            template = find(baseName, NotificationLanguage.EN);
            template.ifPresent(t -> log.warn("Template {}_{} missing or inactive - using EN", baseName, language));
        }
        if (template.isEmpty()) {
            log.error("No active template {} for {} (or EN) - email skipped", baseName, language);
        }
        return template;
    }

    private Optional<NotificationTemplate> find(String baseName, NotificationLanguage language) {
        return templateRepository.findByNameAndLanguageAndActiveTrue(baseName + "_" + language.name(), language);
    }
}
