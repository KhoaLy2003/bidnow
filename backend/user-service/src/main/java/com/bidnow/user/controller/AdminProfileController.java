package com.bidnow.user.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.user.dto.response.UserProfileResponse;
import com.bidnow.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/profiles")
@RequiredArgsConstructor
@Tag(name = "Admin User Profiles", description = "Endpoints for administrators to manage user profiles")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Get user profile by ID (Admin only)", description = "Fetches a specific user's full profile details.")
    @GetMapping("/{userId}")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getUserProfile(@PathVariable UUID userId) {
        UserProfileResponse response = userProfileService.getUserProfile(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // Additional admin-only profile listing/management can be added here
}
