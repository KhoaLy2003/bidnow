package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletStatsResponse {
    private BigDecimal platformWalletBalance;
    private long totalActiveWallets;
    private BigDecimal totalLockedBalance;
    private long totalActiveDepositLocks;
}
