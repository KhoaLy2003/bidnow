package com.bidnow.media.service.impl;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.domain.enums.NotificationLanguage;
import com.bidnow.media.repository.NotificationTemplateRepository;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Loggable
public class NotificationServiceImpl implements NotificationService {

    private final EmailService emailService;
    private final NotificationTemplateRepository templateRepository;
    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    // -------------------------------------------------------------------------
    // OTP Verification — triggered by USER_VERIFICATION_REQUESTED event
    // -------------------------------------------------------------------------

    @Override
    public void handleUserVerificationRequested(UserVerificationRequestedEvent event) {
        log.info("Handling UserVerificationRequestedEvent for user: {}", event.getUserId());

        NotificationLanguage lang = resolveLanguage();
        String templateName = "OTP_VERIFICATION_" + lang.name();

        Optional<NotificationTemplate> templateOpt =
                templateRepository.findByNameAndLanguageAndActiveTrue(templateName, lang);

        if (templateOpt.isEmpty()) {
            log.error("OTP email template '{}' not found or inactive — skipping email send", templateName);
            return;
        }

        Map<String, Object> variables = Map.of(
                "otp", event.getOtp()
        );

        emailService.sendTemplateEmail(event.getEmail(), templateOpt.get(), variables);
        log.info("OTP verification email sent to: {}", event.getEmail());
    }

    // -------------------------------------------------------------------------
    // Welcome Email — triggered by USER_REGISTERED event (after OTP verified)
    // -------------------------------------------------------------------------

    @Override
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Handling UserRegisteredEvent for user: {}", event.getUserId());

        NotificationLanguage lang = resolveLanguage();
        String templateName = "WELCOME_EMAIL_" + lang.name();

        Optional<NotificationTemplate> templateOpt =
                templateRepository.findByNameAndLanguageAndActiveTrue(templateName, lang);

        if (templateOpt.isEmpty()) {
            log.error("Welcome email template '{}' not found or inactive — skipping email send", templateName);
            return;
        }

        // Derive a display name from the email address (before the @) as a fallback
        // until the User Service profile is queryable from here
        String userName = deriveUserName(event.getEmail());

        Map<String, Object> variables = Map.of(
                "userName", userName,
                "actionUrl", frontendBaseUrl + "/auctions"
        );

        emailService.sendTemplateEmail(event.getEmail(), templateOpt.get(), variables);
        log.info("Welcome email sent to: {}", event.getEmail());
    }

    // -------------------------------------------------------------------------
    // Remaining handlers (stubs — implemented in later issues)
    // -------------------------------------------------------------------------

    @Override
    public void handleAuctionCreated(AuctionCreatedEvent event) {
        log.info("Handling AuctionCreatedEvent for auction: {}", event.getAuctionId());
        // TODO: Implement in issue #29 (Seller Auction Management)
    }

    @Override
    public void handleBidPlaced(BidPlacedEvent event) {
        log.info("Handling BidPlacedEvent for auction: {}", event.getAuctionId());
        // TODO: Implement real-time broadcast and smart batching in issue #19
    }

    @Override
    public void handleAuctionEnded(AuctionEndedEvent event) {
        log.info("Handling AuctionEndedEvent for auction: {}", event.getAuctionId());
        // TODO: Implement winner/loser emails in issue #19
    }

    @Override
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Handling PaymentEvent for auction: {}, type: {}", event.getAuctionId(), event.getPaymentType());
        // TODO: Implement payment emails in issue #19
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private NotificationLanguage resolveLanguage() {
        // TODO: read language from UserPreference once the field is modelled
        return NotificationLanguage.EN;
    }

    /**
     * Derives a friendly display name from an email address.
     * e.g. "john.doe@example.com" → "john.doe"
     */
    private String deriveUserName(String email) {
        if (email == null || !email.contains("@")) {
            return "there";
        }
        return email.substring(0, email.indexOf('@'));
    }
}
