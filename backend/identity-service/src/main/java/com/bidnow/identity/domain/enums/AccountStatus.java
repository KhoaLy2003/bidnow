package com.bidnow.identity.domain.enums;

/**
 * Represents the lifecycle status of an identity user account.
 */
public enum AccountStatus {
    /** Registration submitted; awaiting OTP email verification. */
    PENDING_VERIFICATION,
    /** Email verified; account is fully active. */
    ACTIVE,
    /** Account suspended by an admin. */
    SUSPENDED,
    /** Account permanently banned. */
    BANNED
}
