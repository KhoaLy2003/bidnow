package com.bidnow.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * Logs one line per HTTP request with method, path, response status, elapsed time,
 * and the caller's userId (read from the {@code X-User-Id} header injected by the
 * API Gateway). Runs outermost in the security filter chain so the timer and the
 * final response status reflect the whole request, including exception translation
 * by {@link com.bidnow.common.exception.GlobalExceptionHandler}.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String X_USER_ID_HEADER = "X-User-Id";
    private static final String ACTUATOR_PATH_PATTERN = "/actuator/**";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return pathMatcher.match(ACTUATOR_PATH_PATTERN, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            logRequest(request, response, elapsed, error);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long elapsedMs, Throwable error) {
        String userId = request.getHeader(X_USER_ID_HEADER);
        int status = response.getStatus();
        String message = "{} {} -> {} ({}ms) {}";
        Object[] args = {
                StructuredArguments.value("method", request.getMethod()),
                StructuredArguments.value("path", request.getRequestURI()),
                StructuredArguments.value("status", status),
                StructuredArguments.value("elapsedMs", elapsedMs),
                StructuredArguments.keyValue("userId", userId != null && !userId.isBlank() ? userId : "-")
        };

        if (error != null) {
            log.error(message, appendThrowable(args, error));
        } else if (status >= 500) {
            log.error(message, args);
        } else if (status >= 400) {
            log.warn(message, args);
        } else {
            log.info(message, args);
        }
    }

    private static Object[] appendThrowable(Object[] args, Throwable error) {
        Object[] withThrowable = Arrays.copyOf(args, args.length + 1);
        withThrowable[args.length] = error;
        return withThrowable;
    }
}
