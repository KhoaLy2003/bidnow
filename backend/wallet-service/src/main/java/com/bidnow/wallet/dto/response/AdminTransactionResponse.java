package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminTransactionResponse {
    private UUID id;
    private UUID walletId;
    private UUID userId;
    private String type;
    private BigDecimal amount;
    private BigDecimal availableBalanceBefore;
    private BigDecimal availableBalanceAfter;
    private UUID referenceId;
    private String description;
    private String status;
    private String metadata;
    private LocalDateTime createdAt;
}
