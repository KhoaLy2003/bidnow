package com.bidnow.identity.kafka;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityKafkaProducer {

    private static final String USER_REGISTERED_TOPIC = "user-registered-topic";
    private static final String USER_VERIFICATION_REQUESTED_TOPIC = "user-verification-requested-topic";
    private static final String AUDIT_EVENTS_TOPIC = "audit-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAuditLogEvent(com.bidnow.common.dto.event.AuditLogEvent event) {
        kafkaTemplate.send(AUDIT_EVENTS_TOPIC, event.getCorrelationId().toString(), event);
        log.info("Published AuditLogEvent: {} for entity {}", event.getAction(), event.getEntityId());
    }

    public void publishUserRegisteredEvent(UserRegisteredEvent event) {
        kafkaTemplate.send(USER_REGISTERED_TOPIC, event.getUserId().toString(), event);
        log.info("Published UserRegisteredEvent for user: {}", event.getEmail());
    }

    public void publishUserVerificationRequestedEvent(UserVerificationRequestedEvent event) {
        kafkaTemplate.send(USER_VERIFICATION_REQUESTED_TOPIC, event.getUserId().toString(), event);
        log.info("Published UserVerificationRequestedEvent for user: {}", event.getEmail());
    }
}
