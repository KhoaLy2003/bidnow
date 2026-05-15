/*
 * BidNow Auction System
 */
package com.bidnow.media.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MediaUploadResponse {

    private UUID id;
    private String originalName;
    private String s3Key;
    private String contentType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private LocalDateTime createdAt;
}
