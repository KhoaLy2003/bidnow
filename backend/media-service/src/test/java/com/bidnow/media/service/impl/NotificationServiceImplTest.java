package com.bidnow.media.service.impl;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.TemplateResolver;
import com.bidnow.media.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private EmailService emailService;
    @Mock
    private TemplateResolver templateResolver;
    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
    }

    private NotificationIntent capturedIntent() {
        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        return intent.getValue();
    }

    @Test
    void userRegistered_dispatchesWelcomeInAppAndEmail() {
        service.handleUserRegistered(UserRegisteredEvent.builder()
                .userId(USER).email("alice@example.com").firstName("Alice").build());

        NotificationIntent intent = capturedIntent();
        assertThat(intent.userId()).isEqualTo(USER);
        assertThat(intent.type()).isEqualTo(NotificationType.USER_REGISTERED);
        assertThat(intent.dedupKey()).isEqualTo("WELCOME");
        assertThat(intent.title()).isEqualTo("Welcome to BidNow");
        assertThat(intent.actionUrl()).isEqualTo("/auctions");
        assertThat(intent.email().templateBaseName()).isEqualTo("WELCOME_EMAIL");
        assertThat(intent.email().transactional()).isFalse();
        assertThat(intent.email().variables())
                .containsEntry("userName", "Alice")
                .containsEntry("actionUrl", "http://localhost:3000/auctions");
    }

    @Test
    void userRegistered_withoutFirstName_derivesNameFromEmail() {
        service.handleUserRegistered(UserRegisteredEvent.builder().userId(USER).email("john.doe@example.com").build());

        assertThat(capturedIntent().email().variables()).containsEntry("userName", "john.doe");
    }

    @Test
    void verificationRequested_sendsOtpEmailInEnglishWithoutInAppRow() {
        NotificationTemplate otp = NotificationTemplate.builder().name("OTP_VERIFICATION_EN").build();
        when(templateResolver.resolve("OTP_VERIFICATION", NotificationLanguage.EN)).thenReturn(Optional.of(otp));

        service.handleUserVerificationRequested(UserVerificationRequestedEvent.builder()
                .userId(USER).email("alice@example.com").otp("123456").build());

        verify(emailService).sendTemplateEmail("alice@example.com", otp, Map.of("otp", "123456"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void verificationRequested_missingTemplate_sendsNothing() {
        when(templateResolver.resolve("OTP_VERIFICATION", NotificationLanguage.EN)).thenReturn(Optional.empty());

        service.handleUserVerificationRequested(UserVerificationRequestedEvent.builder()
                .userId(USER).email("alice@example.com").otp("123456").build());

        verifyNoInteractions(emailService, dispatcher);
    }
}
