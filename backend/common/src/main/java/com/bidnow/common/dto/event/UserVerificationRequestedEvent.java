package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Emitted by Identity Service when a new user registers and an OTP
 * needs to be sent to their email address.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVerificationRequestedEvent {
    private UUID userId;
    private String email;
    /**
     * The plain-text 6-digit OTP to be delivered via email.
     */
    private String otp;
    private LocalDateTime otpExpiresAt;
    private LocalDateTime requestedAt;
}
