package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ManualRefundResponse {
    private UUID refundTransactionId;
    private BigDecimal amount;
    private UUID userId;
    private BigDecimal availableBalance;
}
