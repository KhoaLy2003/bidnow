/*
 * BidNow Auction System
 */
package com.bidnow.common.constant;

public class ApplicationConstants {
    public static final String API_VERSION = "v1";
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "asc";

    //OTP
    public static final int OTP_LENGTH = 6;
    public static final int OTP_BOUND = 1_000_000; // 000000 – 999999

    //Auth
    public static final String X_USER_ID_HEADER = "X-User-Id";
    public static final String X_USER_ROLES_HEADER = "X-User-Roles";
    public static final String BEARER_PREFIX = "Bearer ";

    //Audit
    public static final String SYSTEM_ACTOR = "SYSTEM";

    private ApplicationConstants() {
    }
}
