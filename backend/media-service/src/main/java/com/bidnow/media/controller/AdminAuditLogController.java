package com.bidnow.media.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.util.PaginationUtils;
import com.bidnow.media.dto.request.criteria.AuditLogCriteria;
import com.bidnow.media.dto.response.AuditLogResponse;
import com.bidnow.media.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Admin Audit Logs", description = "Endpoints for administrators to view and search audit logs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Search audit logs", description = "Returns a paginated list of audit logs, optionally filtered by criteria.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit logs fetched successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required")
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AuditLogResponse>>> searchAuditLogs(
            AuditLogCriteria criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PaginationUtils.getPageable(page, size, sortBy, sortDir);
        Page<AuditLogResponse> resultPage = auditLogService.searchAuditLogs(criteria, pageable);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.of(resultPage)));
    }

    @Operation(summary = "Get audit log by ID", description = "Fetches details of a specific audit log entry.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit log fetched successfully",
                    content = @Content(schema = @Schema(implementation = AuditLogResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Audit log not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<AuditLogResponse>> getAuditLogById(
            @Parameter(description = "UUID of the audit log", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        AuditLogResponse response = auditLogService.getAuditLogById(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
