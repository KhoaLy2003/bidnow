package com.bidnow.media.dto.request.criteria;

import com.bidnow.common.enums.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Criteria for searching and filtering audit logs")
public class AuditLogCriteria {
    @Schema(description = "Filter by actor email", example = "user@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String actorEmail;

    @Schema(description = "Filter by audit action", example = "LOGIN", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private AuditAction action;

    @Schema(description = "Filter from date (inclusive)", example = "2024-01-01T00:00:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDateTime fromDate;

    @Schema(description = "Filter to date (inclusive)", example = "2024-12-31T23:59:59", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDateTime toDate;
}
