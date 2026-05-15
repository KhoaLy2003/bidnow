/*
 * BidNow Auction System
 */
package com.bidnow.common.constant;

public class ErrorCodes {
    public static final String UNEXPECTED_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String INVALID_INPUT = "INVALID_INPUT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    // OTP / Verification
    public static final String OTP_INVALID = "OTP_INVALID";
    public static final String OTP_EXPIRED = "OTP_EXPIRED";
    public static final String OTP_MAX_ATTEMPTS = "OTP_MAX_ATTEMPTS";
    public static final String ACCOUNT_NOT_PENDING = "ACCOUNT_NOT_PENDING";
    public static final String OTP_NOT_EXPIRED = "OTP_NOT_EXPIRED";

    private ErrorCodes() {
    }
}
