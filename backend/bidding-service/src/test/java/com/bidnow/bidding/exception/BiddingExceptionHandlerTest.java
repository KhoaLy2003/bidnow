package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BiddingExceptionHandlerTest {

    private final BiddingExceptionHandler handler = new BiddingExceptionHandler();

    @Test
    void handleBidTooLow_returns400WithMinimumBid() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bids");

        ResponseEntity<ErrorResponse> response =
                handler.handleBidTooLow(new BidTooLowException(new BigDecimal("105.00")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(BiddingErrorCodes.BID_TOO_LOW);
        assertThat(response.getBody().getErrors()).containsEntry("minimumBid", "105.00");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/bids");
    }

    @Test
    void handleUnreadableBody_returns400InvalidInput() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bids");

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad", new MockHttpInputMessage(new byte[0])), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_INPUT");
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed request body");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/bids");
    }

    @Test
    void handleInsufficientBalance_returns403WithWalletDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bids");

        ResponseEntity<ErrorResponse> response = handler.handleInsufficientBalance(
                new InsufficientBalanceException(java.util.Map.of("availableBalance", "10.00", "required", "20.00")),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(BiddingErrorCodes.BID_INSUFFICIENT_BALANCE);
        assertThat(response.getBody().getErrors())
                .containsEntry("availableBalance", "10.00")
                .containsEntry("required", "20.00");
    }

    @Test
    void exceptionStatuses() {
        assertThat(new ConflictException("closed", BiddingErrorCodes.AUCTION_NOT_OPEN).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        ServiceUnavailableException unavailable = new ServiceUnavailableException("down");
        assertThat(unavailable.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getErrorCode()).isEqualTo(BiddingErrorCodes.SERVICE_UNAVAILABLE);
    }
}
