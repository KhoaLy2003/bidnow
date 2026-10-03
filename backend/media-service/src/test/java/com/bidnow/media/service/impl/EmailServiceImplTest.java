package com.bidnow.media.service.impl;

import com.bidnow.media.domain.entity.EmailLog;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.EmailStatus;
import com.bidnow.media.feign.IdentityServiceClient;
import com.bidnow.media.repository.EmailLogRepository;
import com.bidnow.media.repository.NotificationTemplateRepository;
import com.bidnow.media.service.TemplateService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // shared stubs are unused by the annotation-only test
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateService templateService;
    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private IdentityServiceClient identityServiceClient;

    @InjectMocks
    private EmailServiceImpl emailService;

    private final NotificationTemplate template =
            NotificationTemplate.builder().name("WELCOME_EMAIL_EN").bodyText("Hi {userName}").build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@bidnow.test");
        when(templateService.processSubject(any(), any())).thenReturn("Welcome");
        when(templateService.processHtmlBody(any(), any())).thenReturn(null);
        when(templateService.processTextBody(any(), any())).thenReturn("Hi Alice");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        when(emailLogRepository.save(any(EmailLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendTemplateEmail_withNotificationId_linksTheLogAndMarksItSent() {
        UUID notificationId = UUID.randomUUID();

        EmailLog log = emailService.sendTemplateEmail(notificationId, "alice@example.com", template,
                Map.of("userName", "Alice"));

        assertThat(log.getNotificationId()).isEqualTo(notificationId);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(log.getTemplateName()).isEqualTo("WELCOME_EMAIL_EN");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendTemplateEmail_smtpFailure_logsFailedWithoutThrowing() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        EmailLog log = emailService.sendTemplateEmail(UUID.randomUUID(), "alice@example.com", template, Map.of());

        assertThat(log.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(log.getFailureReason()).contains("SMTP down");
    }

    @Test
    void sendTemplateEmail_withoutNotificationId_keepsLegacyBehaviour() {
        EmailLog log = emailService.sendTemplateEmail("alice@example.com", template, Map.of());

        assertThat(log.getNotificationId()).isNull();
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void notificationOverload_runsInItsOwnTransaction() throws Exception {
        Method method = EmailServiceImpl.class.getMethod("sendTemplateEmail",
                UUID.class, String.class, NotificationTemplate.class, Map.class);

        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
