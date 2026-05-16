package com.bidnow.identity.dto.response;

import com.bidnow.common.enums.Role;
import com.bidnow.identity.domain.enums.AccountStatus;
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
@Schema(description = "User details for admin management")
public class AdminUserResponse {
    private UUID id;
    private String email;
    private Role role;
    private AccountStatus status;
    private String statusReason;
    private Boolean isEmailVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
