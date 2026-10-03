package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.request.ManualRefundRequest;
import com.bidnow.wallet.dto.response.AdminTransactionResponse;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;
import com.bidnow.wallet.service.WalletAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/wallets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Wallet Admin", description = "Admin visibility and controls for wallets and transactions")
public class WalletAdminController {

    private final WalletAdminService walletAdminService;

    @Operation(summary = "Platform wallet stats")
    @GetMapping("/stats")
    public ResponseEntity<BaseResponse<WalletStatsResponse>> getStats() {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.getStats()));
    }

    @Operation(summary = "All transactions", description = "Paginated across all wallets, newest first; optional type filter.")
    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<PageResponse<AdminTransactionResponse>>> listTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.listTransactions(type, safePage, safeSize)));
    }

    @Operation(summary = "User wallet detail", description = "Wallet, last 20 transactions and active deposit locks.")
    @GetMapping("/{userId}")
    public ResponseEntity<BaseResponse<AdminWalletDetailResponse>> getWalletDetail(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.getWalletDetail(userId)));
    }

    @Operation(summary = "Freeze wallet", description = "Sets status SUSPENDED: blocks deposits and new deposit locks.")
    @PostMapping("/{userId}/freeze")
    public ResponseEntity<BaseResponse<AdminWalletResponse>> freeze(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.freezeWallet(userId)));
    }

    @Operation(summary = "Unfreeze wallet", description = "Sets status ACTIVE.")
    @PostMapping("/{userId}/unfreeze")
    public ResponseEntity<BaseResponse<AdminWalletResponse>> unfreeze(@PathVariable UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletAdminService.unfreezeWallet(userId)));
    }

    @Operation(summary = "Manual refund", description = "Refunds a user-side PAYMENT/FORFEIT debit once, funded by the platform wallet.")
    @PostMapping("/transactions/{transactionId}/refund")
    public ResponseEntity<BaseResponse<ManualRefundResponse>> refund(@AuthenticatedUserId UUID adminId,
                                                                     @PathVariable UUID transactionId,
                                                                     @Valid @RequestBody ManualRefundRequest request) {
        return ResponseEntity.ok(BaseResponse.success(
                walletAdminService.refundTransaction(adminId, transactionId, request.getReason())));
    }
}
