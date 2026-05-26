package com.bidnow.auction.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelAuctionRequest {

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
