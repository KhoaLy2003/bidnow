package com.bidnow.auction.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyNowRequest {

    @NotNull(message = "buyerId is required")
    private UUID buyerId;
}
