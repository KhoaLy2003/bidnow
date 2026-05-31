package com.bidnow.media.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to send an email template to all active users or specific users")
public class SendTemplateEmailRequest {

    @Schema(description = "Send to all active users in the system", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean sendToAllActive;

    @Schema(description = "List of specific recipient email addresses", example = "[\"user1@example.com\", \"user2@example.com\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> recipientEmails;

    @Schema(description = "Variables to inject into the template", example = "{\"appName\": \"BidNow\", \"title\": \"Auction Update\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, Object> variables;
}
