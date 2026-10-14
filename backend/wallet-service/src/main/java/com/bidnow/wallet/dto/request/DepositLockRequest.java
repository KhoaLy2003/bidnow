package com.bidnow.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositLockRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotNull(message = "auctionId is required")
    private UUID auctionId;

    @NotNull(message = "depositAmount is required")
    @DecimalMin(value = "0.00", message = "depositAmount must be non-negative")
    @Digits(integer = 15, fraction = 4, message = "depositAmount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal depositAmount;
}
