package com.bidnow.media.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to send a test email using a specific template")
public class EmailTestRequest {
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Email address to send the test to", example = "test@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recipientEmail;

    // Optional map of variables to inject into the template for testing
    @Schema(description = "Variables to inject into the template for testing", example = "{\"userName\": \"Test User\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, Object> variables;
}
