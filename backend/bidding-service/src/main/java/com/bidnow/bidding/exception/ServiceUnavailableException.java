package com.bidnow.bidding.exception;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseException {
    public ServiceUnavailableException(String message) {
        super(message, BiddingErrorCodes.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
