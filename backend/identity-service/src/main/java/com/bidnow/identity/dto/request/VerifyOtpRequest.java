package com.bidnow.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for verifying OTP")
public class VerifyOtpRequest {

    @NotBlank
    @Email
    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    @Schema(description = "The 6-digit OTP code sent to user email", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otp;
}
