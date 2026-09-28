package com.bidnow.wallet.controller;

import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.dto.response.WalletBalanceResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.exception.WalletExceptionHandler;
import com.bidnow.wallet.service.DepositLockService;
import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WalletInternalControllerTest {

    private static final String BASE = "/api/v1/internal/wallet";

    @Mock
    private DepositLockService depositLockService;

    @Mock
    private WalletService walletService;

    private MockMvc mockMvc;

    private final UUID userId = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WalletInternalController(depositLockService, walletService))
                .setControllerAdvice(new WalletExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private String lockBody(String amount) {
        return "{\"userId\":\"" + userId + "\",\"auctionId\":\"" + auctionId + "\",\"depositAmount\":" + amount + "}";
    }

    // ── GET /deposit-lock ─────────────────────────────────────────────────────

    @Test
    void getDepositLock_notLocked_returnsLockedFalseOnly() throws Exception {
        when(depositLockService.getDepositLock(userId, auctionId))
                .thenReturn(DepositLockStatusResponse.builder().locked(false).build());

        mockMvc.perform(get(BASE + "/deposit-lock").param("userId", userId.toString())
                        .param("auctionId", auctionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.amount").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist());
    }

    @Test
    void getDepositLock_locked_returnsAmount() throws Exception {
        when(depositLockService.getDepositLock(userId, auctionId))
                .thenReturn(DepositLockStatusResponse.builder()
                        .locked(true).amount(new BigDecimal("50.00")).status("LOCKED").build());

        mockMvc.perform(get(BASE + "/deposit-lock").param("userId", userId.toString())
                        .param("auctionId", auctionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.amount").value(50.00))
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
    }

    @Test
    void getDepositLock_walletMissing_returns404() throws Exception {
        when(depositLockService.getDepositLock(userId, auctionId))
                .thenThrow(new NotFoundException("Wallet not found", WalletErrorCodes.WALLET_NOT_FOUND));

        mockMvc.perform(get(BASE + "/deposit-lock").param("userId", userId.toString())
                        .param("auctionId", auctionId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    // ── POST /deposit-lock ────────────────────────────────────────────────────

    @Test
    void lockDeposit_success_returns200WithBalances() throws Exception {
        UUID lockId = UUID.randomUUID();
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenReturn(DepositLockResponse.builder()
                        .lockId(lockId).amount(new BigDecimal("50.00")).status("LOCKED").alreadyLocked(false)
                        .availableBalance(new BigDecimal("150.00")).lockedBalance(new BigDecimal("50.00"))
                        .build());

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockId").value(lockId.toString()))
                .andExpect(jsonPath("$.data.alreadyLocked").value(false))
                .andExpect(jsonPath("$.data.availableBalance").value(150.00))
                .andExpect(jsonPath("$.data.lockedBalance").value(50.00));
    }

    @Test
    void lockDeposit_insufficientBalance_returns400WithAmounts() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new InsufficientBalanceException(new BigDecimal("30.00"), new BigDecimal("50.00")));

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.errors.availableBalance").value("30.00"))
                .andExpect(jsonPath("$.errors.required").value("50.00"));
    }

    @Test
    void lockDeposit_negativeAmount_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("-1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(depositLockService, never()).lockDeposit(any());
    }

    @Test
    void lockDeposit_missingAuctionId_returns400() throws Exception {
        String body = "{\"userId\":\"" + userId + "\",\"depositAmount\":50.00}";

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(depositLockService, never()).lockDeposit(any());
    }

    @Test
    void lockDeposit_moreThanFourDecimals_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(depositLockService, never()).lockDeposit(any());
    }

    @Test
    void lockDeposit_walletNotActive_returns403() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new ForbiddenException("not active", WalletErrorCodes.WALLET_NOT_ACTIVE));

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_ACTIVE"));
    }

    @Test
    void lockDeposit_lockClosed_returns409() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new ConflictException("closed", WalletErrorCodes.DEPOSIT_LOCK_CLOSED));

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEPOSIT_LOCK_CLOSED"));
    }

    @Test
    void lockDeposit_uniqueViolation_retriesOnceAndReturnsAlreadyLocked() throws Exception {
        when(depositLockService.lockDeposit(any(DepositLockRequest.class)))
                .thenThrow(new DataIntegrityViolationException("uq_deposit_locks_wallet_auction"))
                .thenReturn(DepositLockResponse.builder()
                        .lockId(UUID.randomUUID()).amount(new BigDecimal("50.00")).status("LOCKED").alreadyLocked(true)
                        .availableBalance(new BigDecimal("150.00")).lockedBalance(new BigDecimal("50.00"))
                        .build());

        mockMvc.perform(post(BASE + "/deposit-lock").contentType(MediaType.APPLICATION_JSON).content(lockBody("50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyLocked").value(true));
        verify(depositLockService, times(2)).lockDeposit(any(DepositLockRequest.class));
    }

    // ── GET /balance/{userId} ─────────────────────────────────────────────────

    @Test
    void getBalance_returnsBalances() throws Exception {
        when(walletService.getBalance(userId)).thenReturn(WalletBalanceResponse.builder()
                .totalBalance(new BigDecimal("200.00")).availableBalance(new BigDecimal("150.00"))
                .lockedBalance(new BigDecimal("50.00")).currency("USD").build());

        mockMvc.perform(get(BASE + "/balance/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(200.00))
                .andExpect(jsonPath("$.data.availableBalance").value(150.00))
                .andExpect(jsonPath("$.data.lockedBalance").value(50.00))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void getBalance_walletMissing_returns404() throws Exception {
        when(walletService.getBalance(userId))
                .thenThrow(new NotFoundException("Wallet not found", WalletErrorCodes.WALLET_NOT_FOUND));

        mockMvc.perform(get(BASE + "/balance/{userId}", userId))
                .andExpect(status().isNotFound());
    }
}
