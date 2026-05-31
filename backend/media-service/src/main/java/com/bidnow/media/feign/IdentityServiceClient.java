package com.bidnow.media.feign;

import com.bidnow.common.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "identity-service")
public interface IdentityServiceClient {

    @GetMapping("/api/v1/users/internal/active-emails")
    BaseResponse<List<String>> getActiveUserEmails();

    @PostMapping("/api/v1/users/internal/emails-by-ids")
    BaseResponse<Map<UUID, String>> getEmailsByUserIds(@RequestBody List<UUID> userIds);

    @GetMapping("/api/v1/users/internal/id-by-email")
    BaseResponse<UUID> getUserIdByEmail(@RequestParam String email);

    @GetMapping("/api/v1/users/internal/ids-by-email")
    BaseResponse<List<UUID>> findUserIdsByEmailContaining(@RequestParam String email);
}
