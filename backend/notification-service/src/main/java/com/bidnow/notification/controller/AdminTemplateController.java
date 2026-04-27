package com.bidnow.notification.controller;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.ApiResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.util.PaginationUtils;
import com.bidnow.notification.dto.request.EmailTestRequest;
import com.bidnow.notification.dto.request.TemplateRequest;
import com.bidnow.notification.dto.response.TemplateResponse;
import com.bidnow.notification.repository.NotificationTemplateRepository;
import com.bidnow.notification.service.EmailService;
import com.bidnow.notification.service.TemplateService;
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
public class AdminTemplateController {

    private final TemplateService templateService;
    private final EmailService emailService;
    private final NotificationTemplateRepository templateRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(@Valid @RequestBody TemplateRequest request) {
        TemplateResponse response = templateService.createTemplate(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody TemplateRequest request) {
        TemplateResponse response = templateService.updateTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplate(@PathVariable UUID id) {
        TemplateResponse response = templateService.getTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TemplateResponse>>> getTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PaginationUtils.getPageable(page, size, sortBy, sortDir);
        Page<TemplateResponse> resultPage = templateService.getTemplates(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(resultPage)));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<String>> testTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody EmailTestRequest request) {

        var template = templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found with id: " + id, ErrorCodes.NOT_FOUND));

        emailService.sendTemplateEmail(request.getRecipientEmail(), template, request.getVariables());
        return ResponseEntity.ok(ApiResponse.success("Test email dispatched to " + request.getRecipientEmail()));
    }
}
