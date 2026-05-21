package com.bidnow.media.kafka;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.media.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Loggable
public class AuditKafkaConsumer {

    private final AuditLogService auditLogService;

    @KafkaListener(topics = "audit-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuditLog(AuditLogEvent event) {
        log.info("Consumed audit log event: {} for entity {}", event.getAction(), event.getEntityId());
        try {
            auditLogService.saveAuditLog(event);
        } catch (Exception e) {
            log.error("Failed to save audit log event", e);
            // In a real scenario, we might send this to a DLQ
        }
    }
}
