package com.bidnow.bidding.feign;

import com.bidnow.bidding.dto.DepositLockCommand;
import com.bidnow.common.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "wallet-service")
public interface WalletServiceClient {

    /** Idempotent: a second call for the same user+auction returns the existing lock. */
    @PostMapping("/api/v1/internal/wallet/deposit-lock")
    BaseResponse<Object> lockDeposit(@RequestBody DepositLockCommand command);
}
