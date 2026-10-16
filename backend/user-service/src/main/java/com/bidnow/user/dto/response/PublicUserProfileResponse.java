/*
 * BidNow Auction System
 */
package com.bidnow.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The part of a profile that any visitor may see. Contact details (phone, street address, postal code),
 * preferences and roles are deliberately absent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public view of a user profile")
public class PublicUserProfileResponse {

    @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "Public display name", example = "John Doe")
    private String displayName;

    @Schema(description = "URL to the user's avatar image", example = "https://example.com/avatars/johndoe.jpg")
    private String avatarUrl;

    @Schema(description = "Short biography or description", example = "Vintage camera collector")
    private String bio;

    @Schema(description = "City of residence", example = "Hanoi")
    private String city;

    @Schema(description = "Country of residence", example = "Vietnam")
    private String country;

    @Schema(description = "When the user joined", example = "2025-03-01T12:00:00")
    private LocalDateTime memberSince;
}
