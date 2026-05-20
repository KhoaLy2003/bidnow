package com.bidnow.identity.dto.event;

import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.common.enums.AuditAction;
import com.bidnow.common.util.AuditContextUtils;
import com.bidnow.common.util.DiffUtils;
import com.bidnow.identity.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Spring ApplicationEvent for capturing user changes within a transaction.
 */
@Getter
public class IdentityAuditApplicationEvent extends ApplicationEvent {
    private final AuditLogEvent auditLogEvent;

    public IdentityAuditApplicationEvent(Object source, User oldUser, User newUser, AuditAction action, String reason) {
        super(source);
        this.auditLogEvent = AuditLogEvent.builder()
                .correlationId(AuditContextUtils.getCorrelationId())
                .entityType("User")
                .entityId(newUser != null ? newUser.getId().toString() : oldUser.getId().toString())
                .action(action)
                .actorId(AuditContextUtils.getActorId())
                .actorType(AuditContextUtils.getActorType())
                .ipAddress(AuditContextUtils.getIpAddress())
                .userAgent(AuditContextUtils.getUserAgent())
                .delta(DiffUtils.calculateDiff(oldUser, newUser))
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
