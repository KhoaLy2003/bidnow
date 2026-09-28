package com.bidnow.wallet.constant;

public final class WalletErrorCodes {
    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";
    public static final String WALLET_NOT_ACTIVE = "WALLET_NOT_ACTIVE";
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String DEPOSIT_LOCK_CLOSED = "DEPOSIT_LOCK_CLOSED";
    public static final String PAYMENT_HOLD_NOT_FOUND = "PAYMENT_HOLD_NOT_FOUND";
    public static final String PAYMENT_NOT_PENDING = "PAYMENT_NOT_PENDING";
    public static final String PAYMENT_DEADLINE_EXPIRED = "PAYMENT_DEADLINE_EXPIRED";
    public static final String SELLER_WALLET_NOT_FOUND = "SELLER_WALLET_NOT_FOUND";

    private WalletErrorCodes() {
    }
}
