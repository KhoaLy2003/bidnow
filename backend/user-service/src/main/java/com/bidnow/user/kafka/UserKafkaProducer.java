/*
 * BidNow Auction System
 */
package com.bidnow.user.kafka;

import com.bidnow.common.dto.event.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserKafkaProducer {

    private static final String AUDIT_EVENTS_TOPIC = "audit-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAuditLogEvent(AuditLogEvent event) {
        kafkaTemplate.send(AUDIT_EVENTS_TOPIC, event.getCorrelationId().toString(), event);
        log.info("Published AuditLogEvent: {} for entity {}", event.getAction(), event.getEntityId());
    }
}
