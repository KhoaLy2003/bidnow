/*
 * BidNow Auction System
 */
package com.bidnow.common.util;

import com.bidnow.common.constant.ApplicationConstants;
import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.UUID;

@UtilityClass
public class StringUtils {
    private final SecureRandom secureRandom = new SecureRandom();

    public static String generateRandomString() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generates a zero-padded 6-digit OTP string.
     */
    public String generateOtp() {
        int value = secureRandom.nextInt(ApplicationConstants.OTP_BOUND);
        return String.format("%0" + ApplicationConstants.OTP_LENGTH + "d", value);
    }
}
