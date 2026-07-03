package com.bidnow.wallet.service;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.TransactionResponse;
import com.bidnow.wallet.dto.response.WalletResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface WalletService {
    void createWalletIfAbsent(UUID userId);
    WalletResponse getMyWallet(UUID userId);
    DepositResponse deposit(UUID userId, DepositRequest request);
    PageResponse<TransactionResponse> getTransactions(UUID userId, TransactionType type,
                                                      LocalDate startDate, LocalDate endDate,
                                                      int page, int size);
}
