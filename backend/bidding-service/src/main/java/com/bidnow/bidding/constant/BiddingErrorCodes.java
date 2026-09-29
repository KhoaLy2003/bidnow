package com.bidnow.bidding.constant;

public final class BiddingErrorCodes {
    public static final String AUCTION_NOT_FOUND = "AUCTION_NOT_FOUND";
    public static final String AUCTION_NOT_OPEN = "AUCTION_NOT_OPEN";
    public static final String BID_TOO_LOW = "BID_TOO_LOW";
    public static final String BID_OWN_AUCTION = "BID_OWN_AUCTION";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String BID_INSUFFICIENT_BALANCE = "BID_INSUFFICIENT_BALANCE";
    public static final String WALLET_NOT_ACTIVE = "WALLET_NOT_ACTIVE";
    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";
    public static final String DEPOSIT_LOCK_CLOSED = "DEPOSIT_LOCK_CLOSED";

    private BiddingErrorCodes() {
    }
}
