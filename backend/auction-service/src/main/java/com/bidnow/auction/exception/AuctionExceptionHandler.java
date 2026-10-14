package com.bidnow.auction.exception;

import com.bidnow.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuctionExceptionHandler {

    @ExceptionHandler(BidTooLowException.class)
    public ResponseEntity<ErrorResponse> handleBidTooLow(BidTooLowException ex, HttpServletRequest request) {
        log.info("Bid rejected: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .errors(Map.of("minimumBid", ex.getMinimumBid().toPlainString()))
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
