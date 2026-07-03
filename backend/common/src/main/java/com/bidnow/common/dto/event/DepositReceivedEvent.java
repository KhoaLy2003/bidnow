package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositReceivedEvent {
    private UUID userId;
    private UUID walletId;
    private BigDecimal amount;
    private BigDecimal newBalance;
}
