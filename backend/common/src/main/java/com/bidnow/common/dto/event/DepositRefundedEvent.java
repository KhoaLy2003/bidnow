package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRefundedEvent {
    private UUID userId;
    private UUID walletId;
    private UUID auctionId;
    private BigDecimal amount;
    private String reason; // AUCTION_LOST | AUCTION_CANCELLED
    private Instant refundedAt;
}
