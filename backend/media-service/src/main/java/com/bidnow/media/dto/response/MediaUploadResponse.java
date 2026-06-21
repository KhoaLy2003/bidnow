/*
 * BidNow Auction System
 */
package com.bidnow.media.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Response containing uploaded media metadata")
public class MediaUploadResponse {

    @Schema(description = "Unique identifier of the media record", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;

    @Schema(description = "Original name of the uploaded file", example = "profile.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalName;

    @Schema(description = "S3 key of the stored file", example = "media/550e8400-e29b-41d4-a716-446655440000/profile.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String s3Key;

    @Schema(description = "Public URL to access the uploaded file directly", requiredMode = Schema.RequiredMode.REQUIRED)
    private String publicUrl;

    @Schema(description = "MIME type of the file", example = "image/jpeg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contentType;

    @Schema(description = "Size of the file in bytes", example = "102400", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fileSize;

    @Schema(description = "Width of the image (if applicable)", example = "800", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer width;

    @Schema(description = "Height of the image (if applicable)", example = "600", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer height;

    @Schema(description = "Timestamp when the media was uploaded", example = "2023-10-27T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
}
