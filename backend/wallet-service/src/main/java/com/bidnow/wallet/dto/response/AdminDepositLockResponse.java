package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminDepositLockResponse {
    private UUID id;
    private UUID auctionId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime lockedAt;
}
