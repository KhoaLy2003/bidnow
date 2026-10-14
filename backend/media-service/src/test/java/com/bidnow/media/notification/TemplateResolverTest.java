package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateResolverTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private TemplateResolver resolver;

    private final NotificationTemplate vi = NotificationTemplate.builder().name("WELCOME_EMAIL_VI").build();
    private final NotificationTemplate en = NotificationTemplate.builder().name("WELCOME_EMAIL_EN").build();

    @Test
    void resolve_requestedLanguagePresent_returnsIt() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue("WELCOME_EMAIL_VI", NotificationLanguage.VI))
                .thenReturn(Optional.of(vi));

        assertThat(resolver.resolve("WELCOME_EMAIL", NotificationLanguage.VI)).contains(vi);
    }

    @Test
    void resolve_vietnameseMissing_fallsBackToEnglish() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue("WELCOME_EMAIL_VI", NotificationLanguage.VI))
                .thenReturn(Optional.empty());
        when(templateRepository.findByNameAndLanguageAndActiveTrue("WELCOME_EMAIL_EN", NotificationLanguage.EN))
                .thenReturn(Optional.of(en));

        assertThat(resolver.resolve("WELCOME_EMAIL", NotificationLanguage.VI)).contains(en);
    }

    @Test
    void resolve_noActiveTemplate_isEmpty() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue(anyString(), eq(NotificationLanguage.VI)))
                .thenReturn(Optional.empty());
        when(templateRepository.findByNameAndLanguageAndActiveTrue(anyString(), eq(NotificationLanguage.EN)))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve("MISSING", NotificationLanguage.VI)).isEmpty();
    }

    @Test
    void resolve_englishMissing_doesNotLookUpTwice() {
        when(templateRepository.findByNameAndLanguageAndActiveTrue("MISSING_EN", NotificationLanguage.EN))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve("MISSING", NotificationLanguage.EN)).isEmpty();
        verify(templateRepository, never()).findByNameAndLanguageAndActiveTrue(anyString(), eq(NotificationLanguage.VI));
    }
}
