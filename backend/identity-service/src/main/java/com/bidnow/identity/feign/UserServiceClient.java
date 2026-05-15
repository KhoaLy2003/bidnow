/*
 * BidNow Auction System
 */
package com.bidnow.identity.feign;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PostMapping("/api/v1/users/internal/profiles")
    BaseResponse<Void> createUserProfile(@RequestBody CreateUserProfileRequest request);
}
