package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class AdminWalletResponse {
    private UUID walletId;
    private UUID userId;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private String currency;
    private String status;
}
