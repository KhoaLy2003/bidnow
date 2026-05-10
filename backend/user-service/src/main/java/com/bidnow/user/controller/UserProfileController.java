/*
 * BidNow Auction System
 */
package com.bidnow.user.controller;

import com.bidnow.common.dto.ApiResponse;
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

    @PostMapping("/internal/profiles")
    public ResponseEntity<ApiResponse<UserProfileResponse>> createUserProfile(
            @Valid @RequestBody CreateUserProfileRequest request) {
        UserProfileResponse response = userProfileService.createUserProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<UserProfileResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("User profile created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(@PathVariable UUID userId) {
        UserProfileResponse response = userProfileService.getUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
