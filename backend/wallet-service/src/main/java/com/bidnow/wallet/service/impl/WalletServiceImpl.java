package com.bidnow.wallet.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public void createWalletIfAbsent(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            log.debug("Wallet already exists for userId={}, skipping creation", userId);
            return;
        }
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(wallet);
        log.info("Created wallet for userId={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));
        return WalletResponse.builder()
                .totalBalance(wallet.getTotalBalance())
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .build();
    }
}
