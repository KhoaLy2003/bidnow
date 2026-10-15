package com.bidnow.bidding.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BuyNowResponse {
    private UUID auctionId;
    private BigDecimal price;
    private OffsetDateTime endedAt;
}
