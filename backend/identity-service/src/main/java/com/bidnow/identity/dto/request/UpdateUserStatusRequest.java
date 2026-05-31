package com.bidnow.identity.dto.request;

import com.bidnow.identity.domain.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update user account status")
public class UpdateUserStatusRequest {

    @NotNull(message = "New status is required")
    private AccountStatus status;

    @Schema(description = "Reason for the status change (required for SUSPENDED or BANNED)", example = "Repeated policy violations")
    private String reason;
}
