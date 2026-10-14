package com.bidnow.wallet.controller;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.PaginationMeta;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.WalletExceptionHandler;
import com.bidnow.wallet.service.WalletAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WalletAdminControllerTest {

    private static final String BASE = "/api/v1/admin/wallets";

    @Mock
    private WalletAdminService walletAdminService;

    private MockMvc mockMvc;

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WalletAdminController(walletAdminService))
                .setControllerAdvice(new WalletExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private AdminWalletResponse walletResponse(String status) {
        return AdminWalletResponse.builder()
                .walletId(UUID.randomUUID()).userId(userId)
                .totalBalance(new BigDecimal("100.00")).availableBalance(new BigDecimal("100.00"))
                .lockedBalance(BigDecimal.ZERO).currency("USD").status(status)
                .build();
    }

    @Test
    void getStats_returns200() throws Exception {
        when(walletAdminService.getStats()).thenReturn(WalletStatsResponse.builder()
                .platformWalletBalance(new BigDecimal("950.00")).totalActiveWallets(12)
                .totalLockedBalance(new BigDecimal("300.00")).totalActiveDepositLocks(4)
                .build());

        mockMvc.perform(get(BASE + "/stats").header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformWalletBalance").value(950.00))
                .andExpect(jsonPath("$.data.totalActiveWallets").value(12))
                .andExpect(jsonPath("$.data.totalActiveDepositLocks").value(4));
    }

    @Test
    void listTransactions_defaultsToPageZeroSizeFifty() throws Exception {
        when(walletAdminService.listTransactions(null, 0, 50)).thenReturn(PageResponse.<com.bidnow.wallet.dto.response.AdminTransactionResponse>builder()
                .data(List.of()).pagination(PaginationMeta.builder().page(0).limit(50).build()).build());

        mockMvc.perform(get(BASE + "/transactions"))
                .andExpect(status().isOk());
        verify(walletAdminService).listTransactions(null, 0, 50);
    }

    @Test
    void listTransactions_clampsOutOfRangePaging() throws Exception {
        when(walletAdminService.listTransactions(null, 0, 200)).thenReturn(PageResponse.<com.bidnow.wallet.dto.response.AdminTransactionResponse>builder()
                .data(List.of()).pagination(PaginationMeta.builder().page(0).limit(200).build()).build());

        mockMvc.perform(get(BASE + "/transactions").param("page", "-3").param("size", "5000"))
                .andExpect(status().isOk());
        verify(walletAdminService).listTransactions(null, 0, 200);
    }

    @Test
    void listTransactions_passesTypeAndPaging()throws Exception {
        when(walletAdminService.listTransactions(TransactionType.FORFEIT, 2, 10)).thenReturn(PageResponse.<com.bidnow.wallet.dto.response.AdminTransactionResponse>builder()
                .data(List.of()).pagination(PaginationMeta.builder().page(2).limit(10).build()).build());

        mockMvc.perform(get(BASE + "/transactions").param("type", "FORFEIT").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        verify(walletAdminService).listTransactions(TransactionType.FORFEIT, 2, 10);
    }

    @Test
    void getWalletDetail_returns200() throws Exception {
        when(walletAdminService.getWalletDetail(userId)).thenReturn(AdminWalletDetailResponse.builder()
                .wallet(walletResponse("ACTIVE")).recentTransactions(List.of()).activeDepositLocks(List.of())
                .build());

        mockMvc.perform(get(BASE + "/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wallet.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.wallet.status").value("ACTIVE"));
    }

    @Test
    void getWalletDetail_missing_returns404() throws Exception {
        when(walletAdminService.getWalletDetail(userId))
                .thenThrow(new NotFoundException("none", "WALLET_NOT_FOUND"));

        mockMvc.perform(get(BASE + "/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    @Test
    void freeze_returns200WithSuspended() throws Exception {
        when(walletAdminService.freezeWallet(userId)).thenReturn(walletResponse("SUSPENDED"));

        mockMvc.perform(post(BASE + "/{userId}/freeze", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    void unfreeze_returns200WithActive() throws Exception {
        when(walletAdminService.unfreezeWallet(userId)).thenReturn(walletResponse("ACTIVE"));

        mockMvc.perform(post(BASE + "/{userId}/unfreeze", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void refund_passesAdminIdAndReason() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        when(walletAdminService.refundTransaction(adminId, txId, "Customer dispute")).thenReturn(ManualRefundResponse.builder()
                .refundTransactionId(refundId).amount(new BigDecimal("50.00")).userId(userId)
                .availableBalance(new BigDecimal("150.00")).build());

        mockMvc.perform(post(BASE + "/transactions/{id}/refund", txId).header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Customer dispute\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundTransactionId").value(refundId.toString()))
                .andExpect(jsonPath("$.data.amount").value(50.00));
        verify(walletAdminService).refundTransaction(adminId, txId, "Customer dispute");
    }

    @Test
    void refund_blankReason_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/transactions/{id}/refund", UUID.randomUUID()).header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(walletAdminService, never()).refundTransaction(any(), any(), any());
    }

    @Test
    void refund_reasonTooLong_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/transactions/{id}/refund", UUID.randomUUID()).header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(walletAdminService, never()).refundTransaction(any(), any(), any());
    }

    @Test
    void refund_alreadyRefunded_returns409()throws Exception {
        UUID txId = UUID.randomUUID();
        when(walletAdminService.refundTransaction(adminId, txId, "again"))
                .thenThrow(new ConflictException("done", "TRANSACTION_ALREADY_REFUNDED"));

        mockMvc.perform(post(BASE + "/transactions/{id}/refund", txId).header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_ALREADY_REFUNDED"));
    }
}
