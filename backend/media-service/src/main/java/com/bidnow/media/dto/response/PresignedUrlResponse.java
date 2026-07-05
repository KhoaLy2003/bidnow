/*
 * BidNow Auction System
 */
package com.bidnow.media.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Presigned URL for media upload")
public class PresignedUrlResponse {

    @Schema(description = "Presigned URL to upload the file to S3", example = "https://bidnow-media.s3.amazonaws.com/...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uploadUrl;

    @Schema(description = "S3 key for the uploaded file", example = "uploads/uuid/image.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String s3Key;

    @Schema(description = "Public URL to access the file after the presigned upload completes", requiredMode = Schema.RequiredMode.REQUIRED)
    private String publicUrl;

    @Schema(description = "Timestamp when the presigned URL expires", example = "2023-10-27T10:15:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant expiresAt;
}
