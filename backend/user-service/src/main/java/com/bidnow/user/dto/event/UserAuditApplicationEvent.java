/*
 * BidNow Auction System
 */
package com.bidnow.user.dto.event;

import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.common.enums.AuditAction;
import com.bidnow.common.util.AuditContextUtils;
import com.bidnow.common.util.DiffUtils;
import com.bidnow.user.domain.entity.UserProfile;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Spring ApplicationEvent for capturing user profile changes within a transaction.
 */
@Getter
public class UserAuditApplicationEvent extends ApplicationEvent {
    private final AuditLogEvent auditLogEvent;

    public UserAuditApplicationEvent(Object source, UserProfile oldProfile, UserProfile newProfile, AuditAction action, String reason) {
        super(source);
        this.auditLogEvent = AuditLogEvent.builder()
                .correlationId(AuditContextUtils.getCorrelationId())
                .entityType("UserProfile")
                .entityId(newProfile != null ? newProfile.getUserId().toString() : oldProfile.getUserId().toString())
                .action(action)
                .actorId(AuditContextUtils.getActorId())
                .actorType(AuditContextUtils.getActorType())
                .ipAddress(AuditContextUtils.getIpAddress())
                .userAgent(AuditContextUtils.getUserAgent())
                .delta(DiffUtils.calculateDiff(oldProfile, newProfile))
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
