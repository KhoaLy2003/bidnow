package com.bidnow.bidding.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BidHistoryResponse {
    private UUID id;
    private UUID auctionId;
    private UUID bidderId;
    private String bidderName;
    private String bidderAvatarUrl;
    private BigDecimal amount;
    private OffsetDateTime placedAt;

    @JsonProperty("isAutoBid")
    private boolean autoBid;

    @JsonProperty("isAntiSnipingTriggered")
    private boolean antiSnipingTriggered;
}
