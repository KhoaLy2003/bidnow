package com.bidnow.media.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.util.PaginationUtils;
import com.bidnow.media.dto.response.EmailLogResponse;
import com.bidnow.media.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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
public class AdminEmailLogController {

    private final EmailService emailService;

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<EmailLogResponse>>> getEmailLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PaginationUtils.getPageable(page, size, sortBy, sortDir);
        Page<EmailLogResponse> resultPage = emailService.getEmailLogs(pageable);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.of(resultPage)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<BaseResponse<String>> retryEmail(@PathVariable UUID id) {
        emailService.retryEmail(id);
        return ResponseEntity.ok(BaseResponse.success("Email retry initiated for log id: " + id));
    }
}
