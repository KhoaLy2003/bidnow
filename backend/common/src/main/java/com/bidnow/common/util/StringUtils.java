/*
 * BidNow Auction System
 */
package com.bidnow.common.util;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class StringUtils {
    public static String generateRandomString() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
