package com.bidnow.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {

    @NotNull(message = "auctionId is required")
    private UUID auctionId;
}
