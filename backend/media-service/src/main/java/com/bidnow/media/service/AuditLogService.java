package com.bidnow.media.service;

import com.bidnow.common.dto.event.AuditLogEvent;
import com.bidnow.media.dto.request.criteria.AuditLogCriteria;
import com.bidnow.media.dto.response.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditLogService {
    void saveAuditLog(AuditLogEvent event);

    Page<AuditLogResponse> searchAuditLogs(AuditLogCriteria criteria, Pageable pageable);

    AuditLogResponse getAuditLogById(UUID id);
}
