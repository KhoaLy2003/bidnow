package com.bidnow.notification.dto.request;

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
public class TemplateRequest {
    @NotBlank(message = "Template name is required")
    private String name;

    @NotBlank(message = "Template type is required")
    private String type;

    @NotBlank(message = "Template language is required")
    private String language;

    private String subject;
    private String bodyHtml;

    @NotBlank(message = "Plain text body is required")
    private String bodyText;

    private List<String> variables;
    private boolean active;
}
