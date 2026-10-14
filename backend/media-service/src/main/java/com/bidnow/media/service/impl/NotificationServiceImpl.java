package com.bidnow.media.service.impl;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.TemplateResolver;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Loggable
public class NotificationServiceImpl implements NotificationService {

    private final EmailService emailService;
    private final TemplateResolver templateResolver;
    private final NotificationDispatcher dispatcher;
    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    // -------------------------------------------------------------------------
    // OTP Verification — triggered by USER_VERIFICATION_REQUESTED event
    // -------------------------------------------------------------------------

    @Override
    public void handleUserVerificationRequested(UserVerificationRequestedEvent event) {
        log.info("Handling UserVerificationRequestedEvent for user: {}", event.getUserId());

        // Email only: the account is not active yet, so there is no inbox, and no language preference to read
        templateResolver.resolve("OTP_VERIFICATION", NotificationLanguage.EN).ifPresent(template -> {
            emailService.sendTemplateEmail(event.getEmail(), template, Map.of("otp", event.getOtp()));
            log.info("OTP verification email sent to: {}", event.getEmail());
        });
    }

    // -------------------------------------------------------------------------
    // Welcome — triggered by USER_REGISTERED event (after OTP verified)
    // -------------------------------------------------------------------------

    @Override
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Handling UserRegisteredEvent for user: {}", event.getUserId());

        String userName = displayName(event);
        dispatcher.dispatch(new NotificationIntent(
                event.getUserId(),
                NotificationType.USER_REGISTERED,
                DedupKeys.welcome(),
                null,
                "Welcome to BidNow",
                "Hi " + userName + ", your account is ready. Explore live auctions and place your first bid.",
                "/auctions",
                null,
                new NotificationIntent.EmailSpec("WELCOME_EMAIL",
                        Map.of("userName", userName, "actionUrl", frontendBaseUrl + "/auctions"),
                        false)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** First name when known, else the local part of the email ("john.doe@x" → "john.doe"), else "there". */
    private static String displayName(UserRegisteredEvent event) {
        if (StringUtils.hasText(event.getFirstName())) {
            return event.getFirstName().trim();
        }
        String email = event.getEmail();
        if (email == null || !email.contains("@")) {
            return "there";
        }
        return email.substring(0, email.indexOf('@'));
    }
}
