package com.bidnow.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLogResponse {
    private UUID id;
    private UUID notificationId;
    private String recipientEmail;
    private String subject;
    private String templateName;
    private String status;
    private String failureReason;
    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
