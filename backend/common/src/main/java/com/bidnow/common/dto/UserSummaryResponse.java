package com.bidnow.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Minimal user summary for cross-service consumption")
public class UserSummaryResponse {

    @Schema(description = "User UUID")
    private UUID id;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Avatar image URL")
    private String avatarUrl;
}
