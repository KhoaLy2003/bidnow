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
@Schema(description = "A single auction status transition entry")
public class AuctionStatusHistoryResponse {

    @Schema(description = "Status before the transition (null for the initial creation entry)")
    private String fromStatus;

    @Schema(description = "Status after the transition")
    private String toStatus;

    @Schema(description = "Reason recorded for the transition")
    private String reason;

    @Schema(description = "User ID who triggered the transition")
    private UUID triggeredBy;

    @Schema(description = "When the transition occurred")
    private OffsetDateTime createdAt;
}
