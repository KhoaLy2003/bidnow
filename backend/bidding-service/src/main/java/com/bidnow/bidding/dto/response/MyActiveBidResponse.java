package com.bidnow.bidding.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One active auction the caller has bid on. */
@Data
@Builder
public class MyActiveBidResponse {
    private UUID auctionId;
    private String title;
    private String primaryImageUrl;
    private BigDecimal currentPrice;
    private BigDecimal myHighestBid;
    private int totalBids;
    private OffsetDateTime endTime;
    private OffsetDateTime lastBidAt;

    /** True when the caller currently holds the highest bid. */
    @JsonProperty("isWinning")
    private boolean winning;
}
