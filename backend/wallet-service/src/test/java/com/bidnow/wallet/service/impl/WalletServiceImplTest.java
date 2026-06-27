package com.bidnow.wallet.service.impl;

import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void createWalletIfAbsent_walletAlreadyExists_doesNotSave() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(new Wallet()));

        walletService.createWalletIfAbsent(userId);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void createWalletIfAbsent_noWallet_savesWithZeroBalancesAndActiveStatus() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.createWalletIfAbsent(userId);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        Wallet saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTotalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void getMyWallet_walletFound_returnsMappedResponse() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .totalBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("80.00"))
                .lockedBalance(new BigDecimal("20.00"))
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getMyWallet(userId);

        assertThat(response.getTotalBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(response.getLockedBalance()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getMyWallet_walletNotFound_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getMyWallet(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(userId.toString());
    }
}
