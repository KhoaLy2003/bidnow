/*
 * BidNow Auction System
 */
package com.bidnow.gateway.filter;

import com.bidnow.gateway.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Global gateway filter that:
 * 1. Blocks requests to protected routes that carry no valid JWT.
 * 2. Injects the X-User-Id header so downstream services never need to
 * parse the token themselves — they simply trust this header.
 * <p>
 * Public paths (auth endpoints) are whitelisted and pass through unchanged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String X_USER_ID_HEADER = "X-User-Id";
    private static final String X_USER_ROLES_HEADER = "X-User-Roles";
    private static final String BEARER_PREFIX = "Bearer ";
    /**
     * Paths that are service-internal only and must never be reachable from the public internet.
     * Feign clients call these directly via Eureka, not through this gateway.
     */
    private static final List<String> INTERNAL_PATHS = List.of(
            "/api/v1/**/internal/**",
            // discovery-locator routes: /{service-id}/api/v1/**/internal/**
            "/*/api/v1/**/internal/**"
    );
    /**
     * Paths that do NOT require a valid JWT.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/categories",
            "/api/v1/auctions/public",
            "/api/v1/auctions/public/**",
            // public bid history (single segment only; /my-bids stays authenticated)
            "/api/v1/bids/auction/*",
            "/actuator",
            "/**/v3/api-docs/**",
            "/**/swagger-ui/**",
            "/demo/**",
            "/api/v1/media/download"
    );
    /**
     * Paths where a JWT is optional: anonymous callers pass through (public auction topics), a valid
     * JWT identifies the user (private queues), an invalid one is rejected. Browsers cannot set
     * headers on WebSocket/SockJS, so the token may also arrive as the {@code access_token} query param.
     */
    private static final List<String> OPTIONAL_AUTH_PATHS = List.of(
            "/ws-notifications/**"
    );
    private static final String ACCESS_TOKEN_PARAM = "access_token";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Block service-internal paths — these are only reachable service-to-service via Eureka
        if (isInternalPath(path)) {
            log.warn("Blocked public access to internal path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        if (isOptionalAuthPath(path)) {
            return filterOptionalAuth(exchange, chain, path);
        }

        // Let public paths through without any token check
        if (isPublicPath(path)) {
            // never forward client-supplied identity headers downstream
            ServerHttpRequest stripped = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(X_USER_ID_HEADER);
                        headers.remove(X_USER_ROLES_HEADER);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtUtil.isTokenValid(token)) {
            log.warn("Invalid or expired JWT for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String userId = jwtUtil.extractUserId(token);
        String roles = jwtUtil.extractRoles(token);

        // Mutate the request to add headers; strip any client-supplied values first
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(X_USER_ID_HEADER); // prevent header spoofing
                    headers.remove(X_USER_ROLES_HEADER);
                    headers.add(X_USER_ID_HEADER, userId);
                    if (roles != null) {
                        headers.add(X_USER_ROLES_HEADER, roles);
                    }
                })
                .build();

        log.info("Authenticated request for userId={} roles={} path={}", userId, roles, path);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run before route filters
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> filterOptionalAuth(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        ServerHttpRequest request = exchange.getRequest();
        if (hasUnsafePath(request.getURI().getRawPath())) {
            log.warn("Rejected suspicious optional-auth path: {}", request.getURI().getRawPath());
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }
        String token = resolveToken(request);
        if (token != null && !jwtUtil.isTokenValid(token)) {
            log.warn("Invalid or expired JWT for optional-auth path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        URI withoutToken;
        try {
            withoutToken = stripAccessToken(request.getURI());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed query on optional-auth path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }
        String userId = token == null ? null : jwtUtil.extractUserId(token);
        String roles = token == null ? null : jwtUtil.extractRoles(token);
        ServerHttpRequest mutated = request.mutate()
                .uri(withoutToken)
                .headers(headers -> {
                    headers.remove(X_USER_ID_HEADER); // never trust client-supplied identity
                    headers.remove(X_USER_ROLES_HEADER);
                    if (userId != null) {
                        headers.add(X_USER_ID_HEADER, userId);
                        if (roles != null) {
                            headers.add(X_USER_ROLES_HEADER, roles);
                        }
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** Drops every query param whose URL-decoded name is access_token, keeping the rest raw. */
    static URI stripAccessToken(URI uri) {
        String rawQuery = uri.getRawQuery();
        String newQuery = null;
        if (rawQuery != null) {
            List<String> kept = new ArrayList<>();
            for (String part : rawQuery.split("&")) {
                if (part.isEmpty()) {
                    continue;
                }
                int eq = part.indexOf('=');
                String rawName = eq < 0 ? part : part.substring(0, eq);
                if (!ACCESS_TOKEN_PARAM.equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) {
                    kept.add(part);
                }
            }
            newQuery = kept.isEmpty() ? null : String.join("&", kept);
        }
        return UriComponentsBuilder.fromUri(uri).replaceQuery(newQuery).build(true).toUri();
    }

    private static boolean hasUnsafePath(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        if (rawPath.indexOf(';') >= 0) {
            return true;
        }
        String lower = rawPath.toLowerCase();
        if (lower.contains("%2f") || lower.contains("%2e") || lower.contains("%5c")) {
            return true;
        }
        for (String segment : rawPath.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                return true;
            }
        }
        return false;
    }

    private static String resolveToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        String queryToken = request.getQueryParams().getFirst(ACCESS_TOKEN_PARAM);
        return queryToken == null || queryToken.isBlank() ? null : queryToken;
    }

    private boolean isOptionalAuthPath(String path) {
        return OPTIONAL_AUTH_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isInternalPath(String path) {
        return INTERNAL_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
