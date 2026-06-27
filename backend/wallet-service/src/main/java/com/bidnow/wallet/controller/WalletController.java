package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<BaseResponse<WalletResponse>> getMyWallet(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletService.getMyWallet(userId)));
    }
}
