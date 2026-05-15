/*
 * BidNow Auction System
 */
package com.bidnow.media.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PresignedUrlResponse {

    private String uploadUrl;
    private String s3Key;
    private Instant expiresAt;
}
