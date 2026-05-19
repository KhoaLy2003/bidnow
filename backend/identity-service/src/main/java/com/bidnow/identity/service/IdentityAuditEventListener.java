package com.bidnow.identity.service;

import com.bidnow.common.dto.event.AuditApplicationEvent;
import com.bidnow.identity.dto.event.IdentityAuditApplicationEvent;
import com.bidnow.identity.kafka.IdentityKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityAuditEventListener {

    private final IdentityKafkaProducer kafkaProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuditLogEvent(IdentityAuditApplicationEvent event) {
        log.info("Publishing audit log event after commit: {} for entity {}",
                event.getAuditLogEvent().getAction(), event.getAuditLogEvent().getEntityId());
        kafkaProducer.publishAuditLogEvent(event.getAuditLogEvent());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGenericAuditEvent(AuditApplicationEvent event) {
        log.info("Publishing generic audit event after commit: {} for entity {}",
                event.getAuditLogEvent().getAction(), event.getAuditLogEvent().getEntityId());
        kafkaProducer.publishAuditLogEvent(event.getAuditLogEvent());
    }
}
