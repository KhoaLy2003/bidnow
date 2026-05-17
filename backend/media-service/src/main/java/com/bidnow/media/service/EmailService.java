package com.bidnow.media.service;

import com.bidnow.media.domain.entity.EmailLog;
import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.dto.request.SendTemplateEmailRequest;
import com.bidnow.media.dto.request.criteria.EmailLogCriteria;
import com.bidnow.media.dto.response.EmailLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

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

    /**
     * Sends a test email using a template ID.
     *
     * @param templateId     ID of the template to use
     * @param recipientEmail Recipient email address
     * @param variables      Map of variables to inject
     */
    void sendTestEmail(UUID templateId, String recipientEmail, Map<String, Object> variables);

    /**
     * Sends template-based emails to multiple recipients based on the request (e.g. all active users or specific users).
     *
     * @param templateId UUID of the template
     * @param request    The sending request parameters
     * @return Number of emails successfully queued/sent
     */
    int sendTemplateEmailToGroup(UUID templateId, SendTemplateEmailRequest request);

    // Admin Methods
    Page<EmailLogResponse> getEmailLogs(EmailLogCriteria criteria, Pageable pageable);

    void retryEmail(UUID emailLogId);
}
