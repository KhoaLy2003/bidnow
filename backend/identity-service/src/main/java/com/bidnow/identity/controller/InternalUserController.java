package com.bidnow.identity.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.identity.service.InternalUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/internal")
@RequiredArgsConstructor
@Tag(name = "Internal User Interface", description = "Internal endpoints for service-to-service communication regarding user accounts")
public class InternalUserController {

    private final InternalUserService internalUserService;

    @Operation(summary = "Get all active user emails (Internal)", description = "Returns email addresses of all users with ACTIVE status.")
    @GetMapping("/active-emails")
    public BaseResponse<List<String>> getActiveUserEmails() {
        return BaseResponse.success(internalUserService.getActiveUserEmails());
    }

    @Operation(summary = "Get user emails by IDs (Internal)", description = "Given a list of UUID user IDs, returns a map of user ID to email address.")
    @PostMapping("/emails-by-ids")
    public BaseResponse<Map<UUID, String>> getEmailsByUserIds(@RequestBody List<UUID> userIds) {
        return BaseResponse.success(internalUserService.getEmailsByUserIds(userIds));
    }

    @Operation(summary = "Get user ID by email (Internal)", description = "Given an email address, returns the corresponding user ID.")
    @GetMapping("/id-by-email")
    public BaseResponse<UUID> getUserIdByEmail(@RequestParam String email) {
        return BaseResponse.success(internalUserService.getUserIdByEmail(email).orElse(null));
    }

    @Operation(summary = "Find user IDs by email pattern (Internal)", description = "Given an email pattern, returns list of matching user IDs.")
    @GetMapping("/ids-by-email")
    public BaseResponse<List<UUID>> findUserIdsByEmailContaining(@RequestParam String email) {
        return BaseResponse.success(internalUserService.findUserIdsByEmailContaining(email));
    }
}
