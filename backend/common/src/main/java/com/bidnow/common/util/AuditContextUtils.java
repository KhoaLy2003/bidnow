package com.bidnow.common.util;

import com.bidnow.common.constant.ApplicationConstants;
import com.bidnow.common.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Utility class for capturing audit context information like actor ID, IP address, and user agent.
 */
@UtilityClass
public class AuditContextUtils {

    public static String getActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDto user) {
                return user.getId().toString();
            }
            return principal.toString();
        }
        return ApplicationConstants.SYSTEM_ACTOR;
    }

    public static String getActorEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDto user) {
            return user.getEmail();
        }
        return ApplicationConstants.SYSTEM_ACTOR;
    }

    public static String getActorType() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDto user) {
            return user.getRole().name();
        }
        if (authentication != null && authentication.getAuthorities() != null) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            return isAdmin ? "ADMIN" : "USER";
        }
        return ApplicationConstants.SYSTEM_ACTOR;
    }

    public static String getIpAddress() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "0.0.0.0";
    }

    public static String getUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            return request.getHeader("User-Agent");
        }
        return "UNKNOWN";
    }

    public static UUID getCorrelationId() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            String correlationId = request.getHeader("X-Correlation-Id");
            if (correlationId != null) {
                try {
                    return UUID.fromString(correlationId);
                } catch (IllegalArgumentException e) {
                    // Ignore and generate new one
                }
            }
        }
        return UUID.randomUUID();
    }

    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return (attributes != null) ? attributes.getRequest() : null;
    }
}
