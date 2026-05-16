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
@Schema(description = "Response payload for OTP verification")
public class VerifyOtpResponse {

    @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Updated status of the account after verification", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountStatus;

    @Schema(description = "Whether the user email is verified", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isEmailVerified;

    @Schema(description = "Timestamp when the email was verified", example = "2023-10-27T10:05:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime verifiedAt;
}
