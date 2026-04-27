package com.bidnow.notification.service;

import com.bidnow.notification.domain.entity.EmailLog;
import com.bidnow.notification.domain.entity.NotificationTemplate;
import com.bidnow.notification.dto.response.EmailLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface EmailService {

    /**
     * Sends a simple text email (mostly for testing).
     */
    void sendSimpleEmail(String to, String subject, String content);

    /**
     * Processes a template and sends an HTML email, logging the result to the database.
     *
     * @param to        Recipient email address
     * @param template  The notification template to use
     * @param variables Map of variables to inject into the template
     * @return The saved EmailLog entity
     */
    EmailLog sendTemplateEmail(String to, NotificationTemplate template, Map<String, Object> variables);

    // Admin Methods
    Page<EmailLogResponse> getEmailLogs(Pageable pageable);

    void retryEmail(java.util.UUID emailLogId);
}
