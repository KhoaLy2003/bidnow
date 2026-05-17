/*
 * BidNow Auction System
 */
package com.bidnow.common.constant;

public final class SecurityConstants {
    public static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/*/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**",
            "/api/v1/auth/**"
    };

    private SecurityConstants() {
    }
}
