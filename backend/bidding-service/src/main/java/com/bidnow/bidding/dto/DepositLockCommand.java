package com.bidnow.bidding.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of wallet-service's internal POST /api/v1/internal/wallet/deposit-lock. */
public record DepositLockCommand(UUID userId, UUID auctionId, BigDecimal depositAmount) {
}
