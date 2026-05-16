/*
 * BidNow Auction System
 */
package com.bidnow.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new user profile")
public class CreateUserProfileRequest {

    @NotNull
    @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @NotBlank
    @Email
    @Schema(description = "Email address of the user", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
}
