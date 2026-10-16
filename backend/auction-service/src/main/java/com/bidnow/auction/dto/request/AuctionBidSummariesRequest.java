package com.bidnow.auction.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuctionBidSummariesRequest {

    @NotEmpty(message = "auctionIds must not be empty")
    @Size(max = 100, message = "At most 100 auctionIds per request")
    private List<UUID> auctionIds;
}
