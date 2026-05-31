/*
 * BidNow Auction System
 */
package com.bidnow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response details")
public class ErrorResponse {
    @Builder.Default
    @Schema(description = "Timestamp of the error", example = "2024-03-20T10:00:00Z")
    private Instant timestamp = Instant.now();

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Business error code", example = "USER_NOT_FOUND")
    private String errorCode;

    @Schema(description = "Error message", example = "User not found with id 123")
    private String message;

    @Schema(description = "Request path", example = "/api/v1/users/123")
    private String path;

    @Schema(description = "Detailed validation errors")
    private Map<String, String> errors; // For validation errors
}
