package com.bidnow.wallet.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.TransactionResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.service.WalletService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController walletController;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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

    // ── deposit tests ─────────────────────────────────────────────────────────

    @Test
    void deposit_validAmount_returns200WithDepositResponse() {
        UUID userId = UUID.randomUUID();
        DepositResponse depositResponse = DepositResponse.builder()
                .transactionId(UUID.randomUUID())
                .newBalance(new BigDecimal("600.00"))
                .status("COMPLETED")
                .build();
        when(walletService.deposit(eq(userId), any(DepositRequest.class))).thenReturn(depositResponse);

        ResponseEntity<BaseResponse<DepositResponse>> response =
                walletController.deposit(userId, new DepositRequest(new BigDecimal("500.00")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getNewBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(response.getBody().getData().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void depositRequest_amountIsZero_failsValidation() {
        Set<ConstraintViolation<DepositRequest>> violations = validator.validate(new DepositRequest(BigDecimal.ZERO));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("greater than 0"));
    }

    @Test
    void depositRequest_amountIsNegative_failsValidation() {
        Set<ConstraintViolation<DepositRequest>> violations =
                validator.validate(new DepositRequest(new BigDecimal("-1.00")));
        assertThat(violations).isNotEmpty();
    }

    @Test
    void depositRequest_amountIsNull_failsValidation() {
        Set<ConstraintViolation<DepositRequest>> violations = validator.validate(new DepositRequest(null));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("required"));
    }

    // ── getTransactions tests ─────────────────────────────────────────────────

    @Test
    void getTransactions_noFilters_returns200WithPagedResults() {
        UUID userId = UUID.randomUUID();
        TransactionResponse tx = TransactionResponse.builder()
                .id(UUID.randomUUID())
                .type("DEPOSIT")
                .amount(new BigDecimal("500.00"))
                .availableBalanceBefore(new BigDecimal("100.00"))
                .availableBalanceAfter(new BigDecimal("600.00"))
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();
        PageResponse<TransactionResponse> pageResponse = PageResponse.<TransactionResponse>builder()
                .data(List.of(tx))
                .pagination(PaginationMeta.builder()
                        .page(0).limit(10).total(1L).totalPages(1).hasNext(false).hasPrev(false)
                        .build())
                .build();
        when(walletService.getTransactions(userId, null, null, null, 0, 10)).thenReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> response =
                walletController.getTransactions(userId, null, null, null, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getData()).hasSize(1);
        assertThat(response.getBody().getData().getData().get(0).getType()).isEqualTo("DEPOSIT");
        assertThat(response.getBody().getData().getPagination().getTotal()).isEqualTo(1L);
    }

    @Test
    void getTransactions_withTypeFilter_delegatesToServiceWithType() {
        UUID userId = UUID.randomUUID();
        PageResponse<TransactionResponse> empty = PageResponse.<TransactionResponse>builder()
                .data(List.of())
                .pagination(PaginationMeta.builder()
                        .page(0).limit(10).total(0L).totalPages(0).hasNext(false).hasPrev(false)
                        .build())
                .build();
        when(walletService.getTransactions(userId, TransactionType.DEPOSIT, null, null, 0, 10))
                .thenReturn(empty);

        ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> response =
                walletController.getTransactions(userId, TransactionType.DEPOSIT, null, null, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(walletService).getTransactions(userId, TransactionType.DEPOSIT, null, null, 0, 10);
    }
}
