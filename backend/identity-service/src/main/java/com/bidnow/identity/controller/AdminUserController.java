package com.bidnow.identity.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.identity.dto.request.UpdateUserStatusRequest;
import com.bidnow.identity.dto.response.AdminUserResponse;
import com.bidnow.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "Endpoints for platform administrators to manage user accounts")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "List all users (Admin only)", description = "Fetches a paginated list of all registered users.")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminUserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        PageResponse<AdminUserResponse> response = adminUserService.getAllUsers(page, size, sortBy, direction);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Update user status (Admin only)", description = "Updates a user's account status (ACTIVE, SUSPENDED, BANNED) with a reason.")
    @PutMapping("/{id}/status")
    public ResponseEntity<BaseResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticatedUserId UUID adminId,
            @Valid @RequestBody UpdateUserStatusRequest request) {

        AdminUserResponse response = adminUserService.updateUserStatus(id, adminId, request);
        return ResponseEntity.ok(BaseResponse.success("User status updated successfully", response));
    }
}
