package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class DepositResponse {
    private UUID transactionId;
    private BigDecimal newBalance;
    private String status;
}
