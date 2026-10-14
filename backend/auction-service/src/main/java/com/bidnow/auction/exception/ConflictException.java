package com.bidnow.auction.exception;

import com.bidnow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {
    public ConflictException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.CONFLICT);
    }
}
