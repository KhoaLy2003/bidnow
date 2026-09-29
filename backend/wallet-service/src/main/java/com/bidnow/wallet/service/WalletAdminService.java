package com.bidnow.wallet.service;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.AdminTransactionResponse;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;

import java.util.UUID;

public interface WalletAdminService {

    WalletStatsResponse getStats();

    PageResponse<AdminTransactionResponse> listTransactions(TransactionType type, int page, int size);

    AdminWalletDetailResponse getWalletDetail(UUID userId);

    AdminWalletResponse freezeWallet(UUID userId);

    AdminWalletResponse unfreezeWallet(UUID userId);

    ManualRefundResponse refundTransaction(UUID adminId, UUID transactionId, String reason);
}
