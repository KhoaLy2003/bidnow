/*
 * BidNow Auction System
 */
package com.bidnow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logs one line per HTTP request with method, path, response status, and elapsed
 * time. Runs immediately after {@link AuthenticationFilter} so the exchange it
 * observes already carries the {@code X-User-Id} header injected for authenticated
 * requests, at the cost of not timing the JWT-validation step itself.
 */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String X_USER_ID_HEADER = "X-User-Id";
    private static final String ACTUATOR_PATH_PATTERN = "/actuator/**";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (pathMatcher.match(ACTUATOR_PATH_PATTERN, path)) {
            return chain.filter(exchange);
        }

        long start = System.currentTimeMillis();
        return chain.filter(exchange)
                .doFinally(signal -> logRequest(exchange, path, System.currentTimeMillis() - start));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private void logRequest(ServerWebExchange exchange, String path, long elapsedMs) {
        String userId = exchange.getRequest().getHeaders().getFirst(X_USER_ID_HEADER);
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int statusValue = status != null ? status.value() : 0;
        String message = "{} {} -> {} ({}ms) userId={}";
        Object[] args = {
                exchange.getRequest().getMethod(),
                path,
                statusValue,
                elapsedMs,
                userId != null && !userId.isBlank() ? userId : "-"
        };

        if (statusValue >= 500) {
            log.error(message, args);
        } else if (statusValue >= 400) {
            log.warn(message, args);
        } else {
            log.info(message, args);
        }
    }
}
