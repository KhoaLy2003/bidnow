package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class DepositLockResponse {
    private UUID lockId;
    private BigDecimal amount;
    private String status;
    private boolean alreadyLocked;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
}
