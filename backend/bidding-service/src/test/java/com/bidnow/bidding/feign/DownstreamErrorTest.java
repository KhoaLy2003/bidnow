package com.bidnow.bidding.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamErrorTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesErrorCodeAndErrorsFromCommonErrorResponse() {
        FeignException ex = new FeignException.BadRequest("bad", request(), body("""
                {"timestamp":"2026-10-01T05:00:00Z","status":400,"errorCode":"INSUFFICIENT_BALANCE",
                 "message":"Insufficient balance","path":"/api/v1/internal/wallet/deposit-lock",
                 "errors":{"availableBalance":"10.00","required":"20.00"}}
                """), Map.of());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.status()).isEqualTo(400);
        assertThat(error.errorCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(error.errors()).containsEntry("availableBalance", "10.00").containsEntry("required", "20.00");
    }

    @Test
    void responseWithoutErrorsMap_hasEmptyErrors() {
        FeignException ex = new FeignException.Conflict("conflict", request(),
                body("{\"status\":409,\"errorCode\":\"AUCTION_NOT_OPEN\",\"message\":\"closed\"}"), Map.of());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.errorCode()).isEqualTo("AUCTION_NOT_OPEN");
        assertThat(error.errors()).isEmpty();
    }

    @Test
    void unparseableBody_yieldsNullCode() {
        FeignException ex = new FeignException.InternalServerError("boom", request(), body("<html>oops</html>"), Map.of());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.status()).isEqualTo(500);
        assertThat(error.errorCode()).isNull();
        assertThat(error.errors()).isEmpty();
    }

    @Test
    void timeout_hasNegativeStatusAndNoCode() {
        RetryableException ex = new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.status()).isEqualTo(-1);
        assertThat(error.errorCode()).isNull();
    }

    @Test
    void jsonNullBody_yieldsEmptyError() {
        FeignException ex = new FeignException.BadRequest("bad", request(), body("null"), Map.of());

        DownstreamError error = DownstreamError.of(ex, objectMapper);

        assertThat(error.status()).isEqualTo(400);
        assertThat(error.errorCode()).isNull();
        assertThat(error.errors()).isEmpty();
    }
}
