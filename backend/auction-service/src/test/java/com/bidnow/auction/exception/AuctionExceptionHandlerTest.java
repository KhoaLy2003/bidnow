package com.bidnow.auction.exception;

import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionExceptionHandlerTest {

    private final AuctionExceptionHandler handler = new AuctionExceptionHandler();

    @Test
    void handleBidTooLow_returns400WithMinimumBid() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/auctions/x/bids");

        ResponseEntity<ErrorResponse> response =
                handler.handleBidTooLow(new BidTooLowException(new BigDecimal("550.00")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(AuctionErrorCodes.BID_TOO_LOW);
        assertThat(response.getBody().getErrors()).containsEntry("minimumBid", "550.00");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/internal/auctions/x/bids");
    }

    @Test
    void conflictException_hasStatus409() {
        ConflictException ex = new ConflictException("closed", AuctionErrorCodes.AUCTION_NOT_OPEN);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo(AuctionErrorCodes.AUCTION_NOT_OPEN);
    }
}
