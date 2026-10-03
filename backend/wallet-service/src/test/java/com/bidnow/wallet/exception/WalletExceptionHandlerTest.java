package com.bidnow.wallet.exception;

import com.bidnow.common.dto.ErrorResponse;
import com.bidnow.wallet.constant.WalletErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WalletExceptionHandlerTest {

    private final WalletExceptionHandler handler = new WalletExceptionHandler();

    @Test
    void handleInsufficientBalance_returns400WithAmountsInErrorsMap() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/wallet/deposit-lock");
        InsufficientBalanceException ex =
                new InsufficientBalanceException(new BigDecimal("30.00"), new BigDecimal("50.00"));

        ResponseEntity<ErrorResponse> response = handler.handleInsufficientBalance(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getErrorCode()).isEqualTo(WalletErrorCodes.INSUFFICIENT_BALANCE);
        assertThat(body.getPath()).isEqualTo("/api/v1/internal/wallet/deposit-lock");
        assertThat(body.getErrors())
                .containsEntry("availableBalance", "30.00")
                .containsEntry("required", "50.00");
    }

    @Test
    void conflictException_hasConflictStatus() {
        ConflictException ex = new ConflictException("closed", WalletErrorCodes.DEPOSIT_LOCK_CLOSED);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("DEPOSIT_LOCK_CLOSED");
    }
}
