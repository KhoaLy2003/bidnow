package com.bidnow.identity.dto.response;

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
public class VerifyOtpResponse {

    private UUID userId;
    private String email;
    private String accountStatus;
    private Boolean isEmailVerified;
    private LocalDateTime verifiedAt;
}
