package com.bidnow.wallet.controller;

import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.GlobalExceptionHandler;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.resolver.UserIdArgumentResolver;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.exception.WalletExceptionHandler;
import com.bidnow.wallet.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class PaymentControllerTest {

    private static final String BASE = "/api/v1/wallets/payments";

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    private final UUID userId = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new WalletExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdArgumentResolver())
                .build();
    }

    private String confirmBody() {
        return "{\"auctionId\":\"" + auctionId + "\"}";
    }

    @Test
    void getPendingPayments_returnsList() throws Exception {
        when(paymentService.getPendingPayments(userId)).thenReturn(List.of(PendingPaymentResponse.builder()
                .auctionId(auctionId)
                .totalAmount(new BigDecimal("500.00"))
                .depositApplied(new BigDecimal("50.00"))
                .remaining(new BigDecimal("450.00"))
                .fundsHeld(true)
                .deadline(LocalDateTime.of(2026, 9, 30, 10, 0))
                .hoursLeft(10)
                .expired(false)
                .build()));

        mockMvc.perform(get(BASE + "/pending").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].auctionId").value(auctionId.toString()))
                .andExpect(jsonPath("$.data[0].totalAmount").value(500.00))
                .andExpect(jsonPath("$.data[0].remaining").value(450.00))
                .andExpect(jsonPath("$.data[0].hoursLeft").value(10))
                .andExpect(jsonPath("$.data[0].expired").value(false));
    }

    @Test
    void confirmPayment_success_returns200() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId)).thenReturn(ConfirmPaymentResponse.builder()
                .auctionId(auctionId)
                .amountPaid(new BigDecimal("500.00"))
                .depositApplied(new BigDecimal("50.00"))
                .remainingPaid(new BigDecimal("450.00"))
                .availableBalance(new BigDecimal("150.00"))
                .lockedBalance(new BigDecimal("0.00"))
                .build());

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPaid").value(500.00))
                .andExpect(jsonPath("$.data.availableBalance").value(150.00));
    }

    @Test
    void confirmPayment_noHold_returns404() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new NotFoundException("none", "PAYMENT_HOLD_NOT_FOUND"));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_HOLD_NOT_FOUND"));
    }

    @Test
    void confirmPayment_expired_returns400() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new BadRequestException("late", "PAYMENT_DEADLINE_EXPIRED"));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_DEADLINE_EXPIRED"));
    }

    @Test
    void confirmPayment_notPending_returns409() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new ConflictException("done", "PAYMENT_NOT_PENDING"));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_PENDING"));
    }

    @Test
    void confirmPayment_insufficientBalance_returns400WithAmounts() throws Exception {
        when(paymentService.confirmPayment(userId, auctionId))
                .thenThrow(new InsufficientBalanceException(new BigDecimal("100.00"), new BigDecimal("450.00")));

        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.errors.availableBalance").value("100.00"))
                .andExpect(jsonPath("$.errors.required").value("450.00"));
    }

    @Test
    void confirmPayment_missingAuctionId_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(BASE + "/confirm").header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        verify(paymentService, never()).confirmPayment(any(), any());
    }
}
