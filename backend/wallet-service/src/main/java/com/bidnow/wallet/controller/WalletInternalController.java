package com.bidnow.wallet.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.dto.response.WalletBalanceResponse;
import com.bidnow.wallet.service.DepositLockService;
import com.bidnow.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/wallet")
@RequiredArgsConstructor
@Tag(name = "Internal Wallet Interface", description = "Internal endpoints for service-to-service deposit locking and balance checks")
public class WalletInternalController {

    private final DepositLockService depositLockService;
    private final WalletService walletService;

    @Operation(summary = "Get deposit lock status (Internal)", description = "Returns whether the user's deposit is locked for the auction.")
    @GetMapping("/deposit-lock")
    public ResponseEntity<BaseResponse<DepositLockStatusResponse>> getDepositLock(@RequestParam UUID userId,
                                                                                  @RequestParam UUID auctionId) {
        return ResponseEntity.ok(BaseResponse.success(depositLockService.getDepositLock(userId, auctionId)));
    }

    @Operation(summary = "Lock deposit (Internal)", description = "Idempotently locks the auction deposit from the user's available balance.")
    @PostMapping("/deposit-lock")
    public ResponseEntity<BaseResponse<DepositLockResponse>> lockDeposit(@Valid @RequestBody DepositLockRequest request) {
        DepositLockResponse response;
        try {
            response = depositLockService.lockDeposit(request);
        } catch (DataIntegrityViolationException ex) {
            // Backstop for the unique (wallet_id, auction_id) constraint: the retry sees the existing row.
            log.warn("Deposit lock unique violation for userId={}, auctionId={}; retrying once",
                    request.getUserId(), request.getAuctionId());
            response = depositLockService.lockDeposit(request);
        }
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Get wallet balance (Internal)", description = "Returns total, available and locked balances for the user.")
    @GetMapping("/balance/{userId}")
    public ResponseEntity<BaseResponse<WalletBalanceResponse>> getBalance(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletService.getBalance(userId)));
    }
}
