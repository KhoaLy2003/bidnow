package com.bidnow.identity.dto.response;

import com.bidnow.common.enums.Role;
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
@Schema(description = "Response payload for successful login")
public class LoginResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;

    @Builder.Default
    @Schema(description = "Token type", example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tokenType = "Bearer";

    @Schema(description = "Access token expiry time in milliseconds", example = "3600000", requiredMode = Schema.RequiredMode.REQUIRED)
    private long expiresIn;

    @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "User role", example = "USER", requiredMode = Schema.RequiredMode.REQUIRED)
    private Role role;

    @Schema(description = "JWT refresh token", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
