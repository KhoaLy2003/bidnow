package com.bidnow.wallet.service;

import com.bidnow.wallet.dto.response.WalletResponse;

import java.util.UUID;

public interface WalletService {
    void createWalletIfAbsent(UUID userId);
    WalletResponse getMyWallet(UUID userId);
}
