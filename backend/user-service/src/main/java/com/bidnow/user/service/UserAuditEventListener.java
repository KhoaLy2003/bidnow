/*
 * BidNow Auction System
 */
package com.bidnow.user.service;

import com.bidnow.common.dto.event.AuditApplicationEvent;
import com.bidnow.user.dto.event.UserAuditApplicationEvent;
import com.bidnow.user.kafka.UserKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuditEventListener {

    private final UserKafkaProducer kafkaProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuditLogEvent(UserAuditApplicationEvent event) {
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
