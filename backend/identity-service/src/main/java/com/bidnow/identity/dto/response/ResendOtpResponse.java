package com.bidnow.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendOtpResponse {

    private String email;
    /**
     * New OTP expiry time after resend.
     */
    private LocalDateTime otpExpiresAt;
}
