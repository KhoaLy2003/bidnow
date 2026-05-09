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
public class RegisterResponse {

    private UUID userId;
    private String email;
    private Boolean isEmailVerified;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
