package com.bidnow.media.service.impl;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.media.domain.entity.AuditLog;
import com.bidnow.media.dto.request.criteria.AuditLogCriteria;
import com.bidnow.media.dto.response.AuditLogResponse;
import com.bidnow.media.feign.IdentityServiceClient;
import com.bidnow.media.repository.AuditLogRepository;
import com.bidnow.media.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Loggable
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final IdentityServiceClient identityServiceClient;

    @Override
    @Transactional
    public void saveAuditLog(AuditLogEvent event) {
        AuditLog auditLog = AuditLog.builder()
                .correlationId(event.getCorrelationId())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .action(event.getAction())
                .actorId(event.getActorId())
                .actorType(event.getActorType())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .delta(event.getDelta())
                .reason(event.getReason())
                .metadata(event.getMetadata())
                .timestamp(event.getTimestamp())
                .build();

        auditLogRepository.save(auditLog);
        log.info("Saved audit log for entity {} with action {}", event.getEntityId(), event.getAction());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchAuditLogs(AuditLogCriteria criteria, Pageable pageable) {
        List<String> actorIds = resolveActorIdsByEmail(criteria.getActorEmail());

        SpecificationBuilder<AuditLog> builder = SpecificationBuilder.forEntity();
        builder.withIfPresent("action", SearchOperator.EQUAL, criteria.getAction());
        if (!actorIds.isEmpty()) {
            builder.withIn("actorId", actorIds);
        }
        builder.withIfPresent("timestamp", SearchOperator.GREATER_THAN_OR_EQUAL, criteria.getFromDate());
        builder.withIfPresent("timestamp", SearchOperator.LESS_THAN_OR_EQUAL, criteria.getToDate());

        Page<AuditLog> page = auditLogRepository.findAll(builder.build(), pageable);

        Map<UUID, String> emailMap = resolveActorEmails(page.getContent());

        return page.map(auditLog -> toResponse(auditLog, emailMap));
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(UUID id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found with id: " + id));

        Map<UUID, String> emailMap = resolveActorEmails(List.of(auditLog));
        return toResponse(auditLog, emailMap);
    }

    private List<String> resolveActorIdsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        try {
            List<UUID> userIds = identityServiceClient.findUserIdsByEmailContaining(email).getData();
            if (userIds == null || userIds.isEmpty()) {
                return List.of();
            }
            return userIds.stream().map(UUID::toString).toList();
        } catch (Exception e) {
            log.warn("Failed to resolve actor IDs by email: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<UUID, String> resolveActorEmails(List<AuditLog> auditLogs) {
        List<UUID> actorUuids = auditLogs.stream()
                .map(AuditLog::getActorId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> {
                    try {
                        return UUID.fromString(id);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (actorUuids.isEmpty()) {
            return Map.of();
        }

        try {
            return identityServiceClient.getEmailsByUserIds(actorUuids).getData();
        } catch (Exception e) {
            log.warn("Failed to resolve actor emails: {}", e.getMessage());
            return Map.of();
        }
    }

    private AuditLogResponse toResponse(AuditLog auditLog, Map<UUID, String> emailMap) {
        String actorEmail = null;
        if (auditLog.getActorId() != null) {
            try {
                UUID actorUuid = UUID.fromString(auditLog.getActorId());
                actorEmail = emailMap.get(actorUuid);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .correlationId(auditLog.getCorrelationId())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .action(auditLog.getAction().name())
                .actorId(auditLog.getActorId())
                .actorType(auditLog.getActorType())
                .actorEmail(actorEmail)
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .delta(auditLog.getDelta())
                .reason(auditLog.getReason())
                .metadata(auditLog.getMetadata())
                .timestamp(auditLog.getTimestamp())
                .build();
    }
}
