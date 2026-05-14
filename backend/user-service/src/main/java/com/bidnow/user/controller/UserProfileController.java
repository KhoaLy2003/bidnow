/*
 * BidNow Auction System
 */
package com.bidnow.user.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import com.bidnow.user.dto.response.UserProfileResponse;
import com.bidnow.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * Internal endpoint called by identity-service (via Feign) after registration.
     * Not exposed to external clients.
     */
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
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getMyProfile(
            @AuthenticatedUserId UUID userId) {
        UserProfileResponse response = userProfileService.getMyProfile(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    /**
     * Admin / internal lookup by explicit userId.
     * Kept for service-to-service calls; should not be exposed publicly via the gateway.
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getUserProfile(@PathVariable UUID userId) {
        UserProfileResponse response = userProfileService.getUserProfile(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
