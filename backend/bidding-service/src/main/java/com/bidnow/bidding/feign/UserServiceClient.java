package com.bidnow.bidding.feign;

import com.bidnow.bidding.dto.UserSummariesQuery;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/internal/{userId}/summary")
    BaseResponse<UserSummaryResponse> getUserSummary(@PathVariable("userId") UUID userId);

    @PostMapping("/api/v1/users/internal/summaries")
    BaseResponse<List<UserSummaryResponse>> getUserSummaries(@RequestBody UserSummariesQuery query);
}
