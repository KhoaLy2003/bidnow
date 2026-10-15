package com.bidnow.bidding.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Public Buy It Now body. The buyer is never taken from the body — it comes from the X-User-Id header. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyNowRequest {

    @NotNull(message = "auctionId is required")
    private UUID auctionId;
}
