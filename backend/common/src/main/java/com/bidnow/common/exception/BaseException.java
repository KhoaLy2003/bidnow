/*
 * BidNow Auction System
 */
package com.bidnow.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base abstract exception for all custom exceptions in the system.
 */
@Getter
public abstract class BaseException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    protected BaseException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
