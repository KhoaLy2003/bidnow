package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PendingPaymentResponse {
    private UUID auctionId;
    private BigDecimal totalAmount;
    private BigDecimal depositApplied;
    private BigDecimal remaining;
    private boolean fundsHeld;
    private LocalDateTime deadline;
    private long hoursLeft;
    private boolean expired;
}
