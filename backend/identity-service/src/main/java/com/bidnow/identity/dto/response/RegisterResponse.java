package com.bidnow.identity.dto.response;

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
@Schema(description = "Response payload for user registration")
public class RegisterResponse {

    @Schema(description = "Unique identifier of the newly created user", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Display name stored for the user", example = "John Doe")
    private String displayName;

    @Schema(description = "Current status of the account", example = "PENDING_VERIFICATION", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountStatus;

    @Schema(description = "Whether the user email is verified", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isEmailVerified;

    @Schema(description = "Whether the user account is active", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isActive;

    @Schema(description = "Timestamp when the user was created", example = "2023-10-27T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
}
