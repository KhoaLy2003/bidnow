/*
 * BidNow Auction System
 */
package com.bidnow.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Lightweight JWT utility for the API Gateway.
 * Only validates the token and extracts the subject (userId) —
 * no DB lookup, no Spring Security context.
 */
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /**
     * Returns true if the token signature is valid and it has not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            Date expiry = parseClaims(token).getExpiration();
            return expiry.after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the userId (JWT subject) from a valid token.
     */
    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts roles from a valid token.
     */
    public String extractRoles(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
