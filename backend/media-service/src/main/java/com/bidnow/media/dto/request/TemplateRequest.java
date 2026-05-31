package com.bidnow.media.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Email or notification template creation/update request")
public class TemplateRequest {
    @NotBlank(message = "Template name is required")
    @Schema(description = "Name of the template", example = "WELCOME_EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Template type is required")
    @Schema(description = "Type of the template (e.g., EMAIL, SMS, PUSH)", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotBlank(message = "Template language is required")
    @Schema(description = "Language of the template", example = "en", requiredMode = Schema.RequiredMode.REQUIRED)
    private String language;

    @Schema(description = "Subject of the email", example = "Welcome to BidNow!", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String subject;

    @Schema(description = "HTML content of the template", example = "<h1>Welcome!</h1>", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String bodyHtml;

    @NotBlank(message = "Plain text body is required")
    @Schema(description = "Plain text content of the template", example = "Welcome to BidNow!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bodyText;

    @Schema(description = "List of variables used in the template", example = "[\"userName\", \"verificationLink\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> variables;

    @Schema(description = "Whether the template is active", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean active;
}
