package com.bidnow.media.controller;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.util.PaginationUtils;
import com.bidnow.media.dto.request.EmailTestRequest;
import com.bidnow.media.dto.request.TemplateRequest;
import com.bidnow.media.dto.request.criteria.TemplateCriteria;
import com.bidnow.media.dto.response.TemplateResponse;
import com.bidnow.media.repository.NotificationTemplateRepository;
import com.bidnow.media.service.EmailService;
import com.bidnow.media.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/templates")
@RequiredArgsConstructor
@Tag(name = "Admin Notification Templates", description = "Endpoints for administrators to manage and test notification templates")
@SecurityRequirement(name = "bearerAuth")
public class AdminTemplateController {

    private final TemplateService templateService;
    private final EmailService emailService;
    private final NotificationTemplateRepository templateRepository;

    @Operation(summary = "Create template", description = "Creates a new notification template (Email/In-app).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template created successfully",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required")
    })
    @PostMapping
    public ResponseEntity<BaseResponse<TemplateResponse>> createTemplate(@Valid @RequestBody TemplateRequest request) {
        TemplateResponse response = templateService.createTemplate(request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Update template", description = "Updates an existing notification template.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template updated successfully",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<TemplateResponse>> updateTemplate(
            @Parameter(description = "UUID of the template", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @Valid @RequestBody TemplateRequest request) {
        TemplateResponse response = templateService.updateTemplate(id, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Get template by ID", description = "Fetches details of a specific notification template.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template fetched successfully",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<TemplateResponse>> getTemplate(
            @Parameter(description = "UUID of the template", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        TemplateResponse response = templateService.getTemplate(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Get templates", description = "Returns a paginated list of notification templates, optionally filtered by criteria.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Templates fetched successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required")
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<TemplateResponse>>> getTemplates(
            TemplateCriteria criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PaginationUtils.getPageable(page, size, sortBy, sortDir);
        Page<TemplateResponse> resultPage = templateService.getTemplates(criteria, pageable);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.of(resultPage)));
    }

    @Operation(summary = "Test template delivery", description = "Dispatches a test email using the specified template and variables.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test email dispatched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or variables"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PostMapping("/{id}/test")
    public ResponseEntity<BaseResponse<String>> testTemplate(
            @Parameter(description = "UUID of the template to test", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @Valid @RequestBody EmailTestRequest request) {

        var template = templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found with id: " + id, ErrorCodes.NOT_FOUND));

        emailService.sendTemplateEmail(request.getRecipientEmail(), template, request.getVariables());
        return ResponseEntity.ok(BaseResponse.success("Test email dispatched to " + request.getRecipientEmail()));
    }
}
