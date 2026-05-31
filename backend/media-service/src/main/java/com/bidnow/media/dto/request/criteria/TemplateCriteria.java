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
@Schema(description = "Criteria for searching and filtering templates")
public class TemplateCriteria {
    @Schema(description = "Filter by template types", example = "[\"EMAIL\", \"SMS\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> types;

    @Schema(description = "Filter by languages", example = "[\"en\", \"vi\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> languages;

    @Schema(description = "Filter by active status", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean active;

    @Schema(description = "Search term for name or subject", example = "Welcome", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String search;
}
