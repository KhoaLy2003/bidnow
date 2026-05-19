package com.bidnow.common.dto.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AuditApplicationEvent extends ApplicationEvent {
    private final AuditLogEvent auditLogEvent;

    public AuditApplicationEvent(Object source, AuditLogEvent auditLogEvent) {
        super(source);
        this.auditLogEvent = auditLogEvent;
    }
}
