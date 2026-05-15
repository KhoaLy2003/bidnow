package com.bidnow.common.resolver;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.constant.ApplicationConstants;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Resolves method parameters annotated with @AuthenticatedUserId.
 * Extracts the X-User-Id header and converts it to UUID or String as needed.
 */
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUserId.class);
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        String userId = webRequest.getHeader(ApplicationConstants.X_USER_ID_HEADER);

        if (userId == null || userId.isBlank()) {
            return null;
        }

        // Support both UUID and String parameter types
        if (parameter.getParameterType().equals(UUID.class)) {
            return UUID.fromString(userId);
        }

        return userId;
    }
}
