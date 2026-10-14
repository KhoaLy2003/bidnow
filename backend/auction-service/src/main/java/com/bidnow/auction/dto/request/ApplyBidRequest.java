package com.bidnow.auction.dto.request;

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
public class ApplyBidRequest {

    @NotNull(message = "bidId is required")
    private UUID bidId;

    @NotNull(message = "bidderId is required")
    private UUID bidderId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer digits and 2 decimal places")
    private BigDecimal amount;
}
