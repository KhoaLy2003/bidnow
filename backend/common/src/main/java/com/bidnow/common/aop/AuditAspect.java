package com.bidnow.common.aop;

import com.bidnow.common.annotation.Audit;
import com.bidnow.common.dto.event.AuditApplicationEvent;
import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.common.util.AuditContextHolder;
import com.bidnow.common.util.AuditContextUtils;
import com.bidnow.common.util.DiffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final ApplicationEventPublisher eventPublisher;

    @Around("@annotation(audit)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            Object oldEntity = AuditContextHolder.getOldState();
            Object newEntity = AuditContextHolder.getNewState();

            if (newEntity == null && result != null) {
                newEntity = result;
            }

            publishAuditEvent(joinPoint, audit,
                    audit.captureDelta() ? oldEntity : null,
                    audit.captureDelta() ? newEntity : null);

            return result;
        } finally {
            AuditContextHolder.clear();
        }
    }

    private void publishAuditEvent(ProceedingJoinPoint joinPoint, Audit audit, Object oldEntity, Object newEntity) {
        try {
            String entityId = extractEntityId(oldEntity, newEntity);

            Map<String, Map<String, Object>> delta = DiffUtils.calculateDiff(oldEntity, newEntity);

            AuditLogEvent auditLogEvent = AuditLogEvent.builder()
                    .correlationId(AuditContextUtils.getCorrelationId())
                    .entityType(audit.entityType())
                    .entityId(entityId)
                    .action(audit.action())
                    .actorId(AuditContextUtils.getActorId())
                    .actorType(AuditContextUtils.getActorType())
                    .ipAddress(AuditContextUtils.getIpAddress())
                    .userAgent(AuditContextUtils.getUserAgent())
                    .delta(delta)
                    .reason(audit.reason())
                    .timestamp(LocalDateTime.now())
                    .build();

            eventPublisher.publishEvent(new AuditApplicationEvent(this, auditLogEvent));
        } catch (Exception e) {
            log.error("Failed to publish audit event for {}: {}", audit.action(), audit.entityType(), e);
        }
    }

    private String extractEntityId(Object oldEntity, Object newEntity) {
        if (newEntity != null) {
            return extractIdFromEntity(newEntity);
        }
        if (oldEntity != null) {
            return extractIdFromEntity(oldEntity);
        }
        return "UNKNOWN";
    }

    private String extractIdFromEntity(Object entity) {
        try {
            var method = entity.getClass().getMethod("getId");
            Object id = method.invoke(entity);
            return id != null ? id.toString() : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
