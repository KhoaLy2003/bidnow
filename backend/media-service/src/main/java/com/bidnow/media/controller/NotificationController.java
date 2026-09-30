package com.bidnow.media.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.UnauthorizedException;
import com.bidnow.media.dto.request.NotificationQuery;
import com.bidnow.media.dto.response.BulkUpdateResponse;
import com.bidnow.media.dto.response.NotificationResponse;
import com.bidnow.media.dto.response.UnreadCountResponse;
import com.bidnow.media.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "The signed-in user's notification inbox")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final UserNotificationService notificationService;

    @Operation(summary = "List my notifications", description = "Newest first. Filters: read, types, from, to, search.")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<NotificationResponse>>> list(
            @AuthenticatedUserId UUID userId, @Valid @ModelAttribute NotificationQuery query) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.list(requireUser(userId), query)));
    }

    @Operation(summary = "Count my unread notifications")
    @GetMapping("/unread-count")
    public ResponseEntity<BaseResponse<UnreadCountResponse>> unreadCount(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new UnreadCountResponse(notificationService.unreadCount(requireUser(userId)))));
    }

    @Operation(summary = "Get one notification", description = "Also marks it as read.")
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<NotificationResponse>> get(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.get(requireUser(userId), id)));
    }

    @Operation(summary = "Mark a notification as read")
    @PutMapping("/{id}/read")
    public ResponseEntity<BaseResponse<NotificationResponse>> markRead(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.markRead(requireUser(userId), id)));
    }

    @Operation(summary = "Mark a notification as unread")
    @PutMapping("/{id}/unread")
    public ResponseEntity<BaseResponse<NotificationResponse>> markUnread(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(notificationService.markUnread(requireUser(userId), id)));
    }

    @Operation(summary = "Mark all my notifications as read")
    @PutMapping("/mark-all-read")
    public ResponseEntity<BaseResponse<BulkUpdateResponse>> markAllRead(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new BulkUpdateResponse(notificationService.markAllRead(requireUser(userId)))));
    }

    @Operation(summary = "Delete a notification")
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<String>> delete(@AuthenticatedUserId UUID userId, @PathVariable UUID id) {
        notificationService.delete(requireUser(userId), id);
        return ResponseEntity.ok(BaseResponse.success("Notification deleted"));
    }

    @Operation(summary = "Delete all my notifications")
    @DeleteMapping("/delete-all")
    public ResponseEntity<BaseResponse<BulkUpdateResponse>> deleteAll(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new BulkUpdateResponse(notificationService.deleteAll(requireUser(userId)))));
    }

    @Operation(summary = "Delete all my read notifications")
    @DeleteMapping("/delete-read")
    public ResponseEntity<BaseResponse<BulkUpdateResponse>> deleteRead(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(new BulkUpdateResponse(notificationService.deleteRead(requireUser(userId)))));
    }

    /** The gateway and Spring Security already reject anonymous calls; this keeps the controller safe on its own. */
    private static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authentication required", ErrorCodes.UNAUTHORIZED);
        }
        return userId;
    }
}
