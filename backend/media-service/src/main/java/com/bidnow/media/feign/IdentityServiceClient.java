package com.bidnow.media.feign;

import com.bidnow.common.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "identity-service")
public interface IdentityServiceClient {

    @GetMapping("/api/v1/users/internal/active-emails")
    BaseResponse<List<String>> getActiveUserEmails();
}
