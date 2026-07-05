/*
 * BidNow Auction System
 */
package com.bidnow.user.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import com.bidnow.user.dto.request.UpdateUserProfileRequest;
import com.bidnow.user.dto.response.UserProfileResponse;
import com.bidnow.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Endpoints for managing user profiles and preferences")
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * =============================================================
     * Get a minimal user summary by user ID (internal — no auth required).
     * Intended for service-to-service calls (e.g. auction-service enriching seller info).
     *
     * @param userId UUID of the user
     * @return ResponseEntity containing a BaseResponse with UserSummaryResponse.
     * HTTP 200 on success, 404 if no profile found.
     * =============================================================
     */
    @Operation(summary = "Get user summary by ID (Internal)", hidden = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User summary returned"),
            @ApiResponse(responseCode = "404", description = "User profile not found")
    })
    @GetMapping("/internal/{userId}/summary")
    public ResponseEntity<BaseResponse<UserSummaryResponse>> getUserSummary(
            @PathVariable UUID userId) {
        UserSummaryResponse response = userProfileService.getUserSummary(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    /**
     * Internal endpoint called by identity-service (via Feign) after registration.
     * Not exposed to external clients.
     */
    @Operation(summary = "Create user profile (Internal)", description = "Internal endpoint used by identity-service to create a profile after user registration.", hidden = true)
    @PostMapping("/internal/profiles")
    public ResponseEntity<BaseResponse<UserProfileResponse>> createUserProfile(
            @Valid @RequestBody CreateUserProfileRequest request) {
        UserProfileResponse response = userProfileService.createUserProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<UserProfileResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("User profile created successfully")
                        .data(response)
                        .build());
    }

    /**
     * Returns the profile of the currently authenticated user.
     * The userId is resolved from the X-User-Id header injected by the API Gateway
     * after JWT validation — the client never supplies the ID directly.
     */
    @Operation(summary = "Get current user profile", description = "Fetches the profile details of the currently authenticated user.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile fetched successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getMyProfile(
            @Parameter(description = "Authenticated user ID (injected by gateway)", hidden = true)
            @AuthenticatedUserId UUID userId) {
        UserProfileResponse response = userProfileService.getMyProfile(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    /**
     * Updates the profile of the currently authenticated user.
     */
    @Operation(summary = "Update current user profile", description = "Updates the profile details (display name, avatar, phone, preferences) of the currently authenticated user.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> updateMyProfile(
            @Parameter(description = "Authenticated user ID (injected by gateway)", hidden = true)
            @AuthenticatedUserId UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfileResponse response = userProfileService.updateMyProfile(userId, request);
        return ResponseEntity.ok(BaseResponse.success("Profile updated successfully", response));
    }

    /**
     * Admin / internal lookup by explicit userId.
     * Kept for service-to-service calls; should not be exposed publicly via the gateway.
     */
    @Operation(summary = "Get user profile by ID (Internal/Admin)", description = "Fetches a user profile by its explicit UUID. Primarily for internal service-to-service communication.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile fetched successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/{userId}/profile")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getUserProfile(
            @Parameter(description = "UUID of the user", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID userId) {
        UserProfileResponse response = userProfileService.getUserProfile(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
