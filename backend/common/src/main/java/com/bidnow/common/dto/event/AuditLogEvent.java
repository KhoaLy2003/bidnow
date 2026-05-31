package com.bidnow.common.dto.event;

import com.bidnow.common.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event DTO for publishing audit log information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEvent {
    private UUID correlationId;
    private String entityType;
    private String entityId;
    private AuditAction action;

    private String actorId;
    private String actorType; // USER, ADMIN, SYSTEM

    private String ipAddress;
    private String userAgent;

    private Map<String, Map<String, Object>> delta; // { "field": { "old": "v1", "new": "v2" } }
    private String reason;
    private Map<String, Object> metadata;

    private LocalDateTime timestamp;
}
