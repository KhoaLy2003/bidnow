package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuditApplicationEvent;
import com.bidnow.common.dto.event.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards @Audit events to the shared "audit-events" topic (stored by media-service, visible at
 * /api/v1/admin/audit-logs). Runs after commit; fallbackExecution covers an event published outside a transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletAuditEventListener {

    private static final String AUDIT_EVENTS_TOPIC = "audit-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAudit(AuditApplicationEvent event) {
        AuditLogEvent audit = event.getAuditLogEvent();
        String key = audit.getCorrelationId() != null ? audit.getCorrelationId().toString() : audit.getEntityId();
        kafkaTemplate.send(AUDIT_EVENTS_TOPIC, key, audit)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish audit event {} for {} {}",
                                audit.getAction(), audit.getEntityType(), audit.getEntityId(), ex);
                    } else {
                        log.info("Published audit event {} for {} {}",
                                audit.getAction(), audit.getEntityType(), audit.getEntityId());
                    }
                });
    }
}
