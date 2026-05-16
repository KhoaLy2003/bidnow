package com.bidnow.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update user profile information")
public class UpdateUserProfileRequest {

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    @Schema(description = "Public display name", example = "John Doe")
    private String displayName;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Schema(description = "Contact phone number", example = "+1234567890")
    private String phoneNumber;

    @Schema(description = "Street address", example = "123 Main St")
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    @Schema(description = "City of residence", example = "New York")
    private String city;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    @Schema(description = "Country of residence", example = "USA")
    private String country;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @Schema(description = "Postal or ZIP code", example = "10001")
    private String postalCode;

    @Schema(description = "Short biography or description", example = "Software Engineer and auction enthusiast")
    private String bio;

    @Schema(description = "URL to the user's avatar image", example = "https://example.com/avatars/johndoe.jpg")
    private String avatarUrl;
}
