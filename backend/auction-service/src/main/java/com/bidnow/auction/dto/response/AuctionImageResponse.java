package com.bidnow.auction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Auction image details")
public class AuctionImageResponse {

    @Schema(description = "Image UUID")
    private UUID id;

    @Schema(description = "Full image URL")
    private String imageUrl;

    @Schema(description = "Thumbnail URL")
    private String thumbnailUrl;

    @Schema(description = "Display order in gallery")
    private Integer displayOrder;

    @Schema(description = "Whether this is the primary/cover image")
    private Boolean isPrimary;

    @Schema(description = "Upload timestamp")
    private OffsetDateTime uploadedAt;
}
