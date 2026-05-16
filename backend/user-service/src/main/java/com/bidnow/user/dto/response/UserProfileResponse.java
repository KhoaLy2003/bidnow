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
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed user profile response")
public class UserProfileResponse {

    @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "Public display name", example = "John Doe")
    private String displayName;

    @Schema(description = "URL to the user's avatar image", example = "https://example.com/avatars/johndoe.jpg")
    private String avatarUrl;

    @Schema(description = "Contact phone number", example = "+1234567890")
    private String phoneNumber;

    @Schema(description = "Street address", example = "123 Main St")
    private String address;

    @Schema(description = "City of residence", example = "New York")
    private String city;

    @Schema(description = "Country of residence", example = "USA")
    private String country;

    @Schema(description = "Postal or ZIP code", example = "10001")
    private String postalCode;

    @Schema(description = "Short biography or description", example = "Software Engineer and auction enthusiast")
    private String bio;

    @Schema(description = "List of roles assigned to the user", example = "[\"USER\", \"BUYER\"]")
    private List<String> roles;

    @Schema(description = "Preferred language code", example = "en")
    private String language;

    @Schema(description = "Preferred timezone", example = "America/New_York")
    private String timezone;

    @Schema(description = "Preferred currency code", example = "USD")
    private String currency;

    @Schema(description = "Whether email notifications are enabled", example = "true")
    private Boolean emailNotifications;

    @Schema(description = "Whether push notifications are enabled", example = "true")
    private Boolean pushNotifications;

    @Schema(description = "Whether SMS notifications are enabled", example = "false")
    private Boolean smsNotifications;

    @Schema(description = "Timestamp when the profile was created", example = "2024-01-01T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the profile was last updated", example = "2024-03-20T10:00:00")
    private LocalDateTime updatedAt;
}
