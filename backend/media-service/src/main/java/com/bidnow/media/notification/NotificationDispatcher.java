package com.bidnow.media.notification;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationStatus;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.kafka.UserNotificationPushPublisher;
import com.bidnow.media.realtime.UserNotificationMessage;
import com.bidnow.media.repository.NotificationInboxRepository;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single entry point for delivering notifications. Inside the transaction: idempotent insert (one row per
 * user and dedup key). After commit: unread count, email and live push. Duplicates produce nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationInboxRepository inboxRepository;
    private final RecipientDirectory recipientDirectory;
    private final TemplateResolver templateResolver;
    private final EmailService emailService;
    private final UserNotificationPushPublisher pushPublisher;
    private final UnreadCounter unreadCounter;
    private final Clock clock;

    @Transactional
    public void dispatch(NotificationIntent intent) {
        dispatchAll(List.of(intent));
    }

    @Transactional
    public void dispatchAll(List<NotificationIntent> intents) {
        List<Delivery> deliveries = new ArrayList<>();
        for (NotificationIntent intent : intents) {
            Notification row = toRow(intent);
            if (!inboxRepository.insertIfAbsent(row)) {
                log.debug("Duplicate notification {} for user {} ignored", intent.dedupKey(), intent.userId());
                continue;
            }
            deliveries.add(new Delivery(intent, NotificationResponse.from(row)));
        }
        if (!deliveries.isEmpty()) {
            AfterCommit.run(() -> deliver(deliveries));
        }
    }

    private void deliver(List<Delivery> deliveries) {
        Set<UUID> emailUserIds = deliveries.stream()
                .filter(d -> d.intent().email() != null)
                .map(d -> d.intent().userId())
                .collect(Collectors.toSet());
        Map<UUID, Recipient> recipients = Map.of();
        if (!emailUserIds.isEmpty()) {
            try {
                recipients = recipientDirectory.resolve(emailUserIds);
            } catch (RuntimeException ex) {
                log.warn("Recipient lookup failed - emails skipped, pushes continue: {}", ex.getMessage());
            }
        }

        for (Delivery delivery : deliveries) {
            UUID userId = delivery.intent().userId();
            try {
                try {
                    long unreadCount = unreadCounter.count(userId);
                    pushPublisher.publish(userId, UserNotificationMessage.notification(delivery.notification(), unreadCount));
                } catch (RuntimeException ex) {
                    log.warn("Push for notification {} failed: {}", delivery.notification().getId(), ex.getMessage());
                }
                if (delivery.intent().email() != null) {
                    sendEmail(delivery, recipients.getOrDefault(userId, Recipient.unknown(userId)));
                }
            } catch (RuntimeException ex) {
                log.error("Delivery of notification {} failed: {}", delivery.notification().getId(), ex.getMessage(), ex);
            }
        }
    }

    private void sendEmail(Delivery delivery, Recipient recipient) {
        NotificationIntent.EmailSpec spec = delivery.intent().email();
        if (recipient.email() == null) {
            log.warn("No email address for user {} - {} email skipped", recipient.userId(), spec.templateBaseName());
            return;
        }
        if (!spec.transactional() && !recipient.emailOptIn()) {
            log.debug("User {} opted out of emails - {} skipped", recipient.userId(), spec.templateBaseName());
            return;
        }
        templateResolver.resolve(spec.templateBaseName(), recipient.language()).ifPresent(template -> {
            try {
                emailService.sendTemplateEmail(delivery.notification().getId(), recipient.email(), template,
                        withUserName(spec.variables(), recipient));
            } catch (RuntimeException ex) {
                log.error("Email {} for user {} failed: {}", template.getName(), recipient.userId(), ex.getMessage());
            }
        });
    }

    /** Templates greet {userName}; handlers rarely know it, so it is filled from the recipient's display name. */
    private static Map<String, Object> withUserName(Map<String, Object> variables, Recipient recipient) {
        Map<String, Object> merged = new HashMap<>(variables == null ? Map.of() : variables);
        merged.putIfAbsent("userName", StringUtils.hasText(recipient.displayName()) ? recipient.displayName() : "there");
        return merged;
    }

    private Notification toRow(NotificationIntent intent) {
        Notification row = Notification.builder()
                .id(UUID.randomUUID())
                .userId(intent.userId())
                .type(intent.type())
                .channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.SENT)
                .title(intent.title())
                .message(intent.message())
                .actionUrl(intent.actionUrl())
                .auctionId(intent.auctionId())
                .metadata(intent.metadata())
                .dedupKey(intent.dedupKey())
                .build();
        row.setCreatedAt(LocalDateTime.now(clock));
        return row;
    }

    private record Delivery(NotificationIntent intent, NotificationResponse notification) {
    }
}
