package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.TransactionResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<BaseResponse<WalletResponse>> getMyWallet(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletService.getMyWallet(userId)));
    }

    @PostMapping("/deposit")
    public ResponseEntity<BaseResponse<DepositResponse>> deposit(
            @AuthenticatedUserId UUID userId,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(BaseResponse.success(walletService.deposit(userId, request)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> getTransactions(
            @AuthenticatedUserId UUID userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(BaseResponse.success(
                walletService.getTransactions(userId, type, startDate, endDate, page, size)));
    }
}
