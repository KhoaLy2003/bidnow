package com.bidnow.wallet.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController walletController;

    @Test
    void getMyWallet_walletExists_returns200WithWalletData() {
        UUID userId = UUID.randomUUID();
        WalletResponse walletResponse = WalletResponse.builder()
                .totalBalance(new BigDecimal("50.00"))
                .availableBalance(new BigDecimal("50.00"))
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status("ACTIVE")
                .build();
        when(walletService.getMyWallet(userId)).thenReturn(walletResponse);

        ResponseEntity<BaseResponse<WalletResponse>> response = walletController.getMyWallet(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getTotalBalance())
                .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.getBody().getData().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getMyWallet_walletNotFound_propagatesNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletService.getMyWallet(userId)).thenThrow(
                new NotFoundException("Wallet not found for userId: " + userId, "NOT_FOUND"));

        assertThatThrownBy(() -> walletController.getMyWallet(userId))
                .isInstanceOf(NotFoundException.class);
    }
}
