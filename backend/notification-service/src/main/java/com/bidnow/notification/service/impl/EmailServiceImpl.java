package com.bidnow.notification.service.impl;

import com.bidnow.notification.domain.entity.EmailLog;
import com.bidnow.notification.domain.entity.NotificationTemplate;
import com.bidnow.notification.domain.enums.EmailStatus;
import com.bidnow.notification.dto.response.EmailLogResponse;
import com.bidnow.notification.repository.EmailLogRepository;
import com.bidnow.notification.service.EmailService;
import com.bidnow.notification.service.TemplateService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateService templateService;
    private final EmailLogRepository emailLogRepository;

    @Override
    public void sendSimpleEmail(String to, String subject, String content) {
        log.info("Sending simple email to: {}", to);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("no-reply@bidnow.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    @Override
    public EmailLog sendTemplateEmail(String to, NotificationTemplate template, Map<String, Object> variables) {
        log.info("Sending template email '{}' to: {}", template.getName(), to);

        String subject = templateService.processSubject(template, variables);
        String htmlBody = templateService.processHtmlBody(template, variables);
        String textBody = templateService.processTextBody(template, variables);

        EmailLog emailLog = EmailLog.builder()
                .recipientEmail(to)
                .subject(subject)
                .templateName(template.getName())
                .retryCount(0)
                .build();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@bidnow.com");
            helper.setTo(to);
            helper.setSubject(subject);

            if (htmlBody != null) {
                helper.setText(textBody, htmlBody); // Fallback text, Primary HTML
            } else {
                helper.setText(textBody, false);
            }

            mailSender.send(message);

            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            log.info("Template email sent successfully to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send template email to: {}", to, e);
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setFailureReason(e.getMessage());
        }

        return emailLogRepository.save(emailLog);
    }

    @Override
    public Page<EmailLogResponse> getEmailLogs(Pageable pageable) {
        return emailLogRepository.findAll(pageable)
                .map(log -> com.bidnow.notification.dto.response.EmailLogResponse.builder()
                        .id(log.getId())
                        .notificationId(log.getNotificationId())
                        .recipientEmail(log.getRecipientEmail())
                        .subject(log.getSubject())
                        .templateName(log.getTemplateName())
                        .status(log.getStatus() != null ? log.getStatus().toString() : null)
                        .failureReason(log.getFailureReason())
                        .retryCount(log.getRetryCount())
                        .sentAt(log.getSentAt())
                        .createdAt(log.getCreatedAt())
                        .updatedAt(log.getUpdatedAt())
                        .build());
    }

    @Override
    public void retryEmail(UUID emailLogId) {
        throw new UnsupportedOperationException(
                "Manual retry requires full notification metadata, which is currently not fully persisted in the email log.");
    }
}
