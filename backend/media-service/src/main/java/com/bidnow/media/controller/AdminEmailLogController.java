package com.bidnow.media.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.util.PaginationUtils;
import com.bidnow.media.dto.request.criteria.EmailLogCriteria;
import com.bidnow.media.dto.response.EmailLogResponse;
import com.bidnow.media.service.EmailService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/email-logs")
@RequiredArgsConstructor
@Tag(name = "Admin Email Logs", description = "Endpoints for administrators to view and retry email delivery logs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailLogController {

    private final EmailService emailService;

    @Operation(summary = "Get email logs", description = "Returns a paginated list of email delivery logs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logs fetched successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required")
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<EmailLogResponse>>> getEmailLogs(
            EmailLogCriteria criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PaginationUtils.getPageable(page, size, sortBy, sortDir);
        Page<EmailLogResponse> resultPage = emailService.getEmailLogs(criteria, pageable);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.of(resultPage)));
    }

    @Operation(summary = "Retry email delivery", description = "Manually triggers a retry for a failed email log entry.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Retry initiated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Log entry not found")
    })
    @PostMapping("/{id}/retry")
    public ResponseEntity<BaseResponse<String>> retryEmail(
            @Parameter(description = "UUID of the email log entry", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        emailService.retryEmail(id);
        return ResponseEntity.ok(BaseResponse.success("Email retry initiated for log id: " + id));
    }
}
