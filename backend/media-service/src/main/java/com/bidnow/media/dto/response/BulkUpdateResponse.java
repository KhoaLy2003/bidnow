package com.bidnow.media.dto.response;

/** Result of a bulk inbox operation: how many notifications were changed. */
public record BulkUpdateResponse(int updated) {
}
