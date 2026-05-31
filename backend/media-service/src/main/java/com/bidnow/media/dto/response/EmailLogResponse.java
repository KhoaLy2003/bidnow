package com.bidnow.media.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Email log details response")
public class EmailLogResponse {
    @Schema(description = "Unique identifier of the email log", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;

    @Schema(description = "Associated notification identifier", example = "660e8400-e29b-41d4-a716-446655440001", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private UUID notificationId;

    @Schema(description = "Email address of the recipient", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recipientEmail;

    @Schema(description = "Subject of the email sent", example = "Welcome to BidNow!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subject;

    @Schema(description = "Name of the template used", example = "WELCOME_EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    @Schema(description = "Current status of the email delivery", example = "SENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "Reason for failure if status is FAILED", example = "Invalid recipient address", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String failureReason;

    @Schema(description = "Number of retries attempted", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer retryCount;

    @Schema(description = "Timestamp when the email was actually sent", example = "2023-10-27T10:05:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDateTime sentAt;

    @Schema(description = "Timestamp when the log was created", example = "2023-10-27T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the log was last updated", example = "2023-10-27T10:05:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedAt;
}
