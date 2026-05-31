package com.bidnow.media.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Audit log details response")
public class AuditLogResponse {
    @Schema(description = "Unique identifier of the audit log", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;

    @Schema(description = "Correlation identifier for tracking related events", example = "660e8400-e29b-41d4-a716-446655440001", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private UUID correlationId;

    @Schema(description = "Type of entity being audited", example = "MEDIA", requiredMode = Schema.RequiredMode.REQUIRED)
    private String entityType;

    @Schema(description = "Unique identifier of the entity being audited", example = "550e8400-e29b-41d4-a716-446655440002", requiredMode = Schema.RequiredMode.REQUIRED)
    private String entityId;

    @Schema(description = "Type of action performed", example = "CREATE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "Identifier of the user or service that performed the action", example = "user-123", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String actorId;

    @Schema(description = "Type of the actor (USER, SERVICE, ADMIN)", example = "USER", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String actorType;

    @Schema(description = "Email address of the actor if applicable", example = "user@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String actorEmail;

    @Schema(description = "IP address from which the action was initiated", example = "192.168.1.1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String ipAddress;

    @Schema(description = "User agent string from the request", example = "Mozilla/5.0...", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String userAgent;

    @Schema(description = "Changes made to the entity (before/after values)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, Map<String, Object>> delta;

    @Schema(description = "Reason for the action if provided", example = "Spam prevention", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reason;

    @Schema(description = "Additional metadata about the audit event", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, Object> metadata;

    @Schema(description = "Timestamp of when the event occurred", example = "2024-03-20T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime timestamp;
}

