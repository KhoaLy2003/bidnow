package com.bidnow.media.feign;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserNotificationPreferenceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PostMapping("/api/v1/users/internal/notification-preferences")
    BaseResponse<List<UserNotificationPreferenceResponse>> getNotificationPreferences(@RequestBody UserIdsRequest request);
}
