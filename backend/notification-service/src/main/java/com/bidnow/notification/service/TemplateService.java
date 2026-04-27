package com.bidnow.notification.service;

import com.bidnow.notification.domain.entity.NotificationTemplate;
import com.bidnow.notification.dto.request.TemplateRequest;
import com.bidnow.notification.dto.response.TemplateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface TemplateService {

    /**
     * Replaces placeholders in the HTML body of a template with actual values from variables map.
     *
     * @param template  The notification template
     * @param variables Map of variables (e.g., {"userName": "John", "bidAmount": "$100"})
     * @return The processed HTML string
     */
    String processHtmlBody(NotificationTemplate template, Map<String, Object> variables);

    /**
     * Replaces placeholders in the Plain Text body of a template with actual values from variables map.
     *
     * @param template  The notification template
     * @param variables Map of variables
     * @return The processed text string
     */
    String processTextBody(NotificationTemplate template, Map<String, Object> variables);

    /**
     * Replaces placeholders in the Subject of a template with actual values from variables map.
     */
    String processSubject(NotificationTemplate template, Map<String, Object> variables);

    // Admin Methods
    TemplateResponse createTemplate(TemplateRequest request);

    TemplateResponse updateTemplate(UUID id, TemplateRequest request);

    TemplateResponse getTemplate(UUID id);

    Page<TemplateResponse> getTemplates(Pageable pageable);
}
