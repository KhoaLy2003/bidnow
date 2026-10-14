package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ConfirmPaymentResponse {
    private UUID auctionId;
    private BigDecimal amountPaid;
    private BigDecimal depositApplied;
    private BigDecimal remainingPaid;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
}
