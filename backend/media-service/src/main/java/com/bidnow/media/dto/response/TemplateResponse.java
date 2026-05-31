package com.bidnow.media.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Email or notification template details response")
public class TemplateResponse {
    @Schema(description = "Unique identifier of the template", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;

    @Schema(description = "Name of the template", example = "WELCOME_EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Type of the template (e.g., EMAIL, SMS, PUSH)", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "Language of the template", example = "en", requiredMode = Schema.RequiredMode.REQUIRED)
    private String language;

    @Schema(description = "Subject of the email", example = "Welcome to BidNow!", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String subject;

    @Schema(description = "HTML content of the template", example = "<h1>Welcome!</h1>", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String bodyHtml;

    @Schema(description = "Plain text content of the template", example = "Welcome to BidNow!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bodyText;

    @Schema(description = "List of variables used in the template", example = "[\"userName\", \"verificationLink\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> variables;

    @Schema(description = "Whether the template is active", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean active;

    @Schema(description = "Timestamp when the template was created", example = "2023-10-27T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the template was last updated", example = "2023-10-27T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedAt;
}
