package com.bidnow.media.dto.request.criteria;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Criteria for searching and filtering email logs")
public class EmailLogCriteria {
    @Schema(description = "Filter by recipient email", example = "user@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String recipientEmail;

    @Schema(description = "Filter by template names", example = "[\"WELCOME_EMAIL\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> templateNames;

    @Schema(description = "Filter by statuses", example = "[\"SENT\", \"FAILED\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> statuses;

    @Schema(description = "Search term for subject", example = "Welcome", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String search;
}
