package com.bidnow.bidding.feign;

import com.bidnow.common.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

import java.util.Map;

/**
 * The status and common {@link ErrorResponse} fields of a failed Feign call. {@code status} is -1
 * for connection failures and timeouts; {@code errorCode} is null when the body is absent or not an
 * ErrorResponse.
 */
public record DownstreamError(int status, String errorCode, Map<String, String> errors) {

    public static DownstreamError of(FeignException ex, ObjectMapper objectMapper) {
        String body = ex.contentUTF8();
        if (body == null || body.isBlank()) {
            return new DownstreamError(ex.status(), null, Map.of());
        }
        try {
            ErrorResponse response = objectMapper.readValue(body, ErrorResponse.class);
            if (response == null) {
                return new DownstreamError(ex.status(), null, Map.of());
            }
            Map<String, String> errors = response.getErrors() == null ? Map.of() : response.getErrors();
            return new DownstreamError(ex.status(), response.getErrorCode(), errors);
        } catch (JsonProcessingException parseFailure) {
            return new DownstreamError(ex.status(), null, Map.of());
        }
    }
}
