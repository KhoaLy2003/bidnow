package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private String type;
    private BigDecimal amount;
    private BigDecimal availableBalanceBefore;
    private BigDecimal availableBalanceAfter;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
