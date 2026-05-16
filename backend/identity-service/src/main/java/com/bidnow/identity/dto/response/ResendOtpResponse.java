package com.bidnow.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload for OTP resend")
public class ResendOtpResponse {

    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    /**
     * New OTP expiry time after resend.
     */
    @Schema(description = "New OTP expiry time", example = "2023-10-27T10:15:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime otpExpiresAt;
}
