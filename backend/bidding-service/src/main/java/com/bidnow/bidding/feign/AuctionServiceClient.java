package com.bidnow.bidding.feign;

import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.common.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "auction-service")
public interface AuctionServiceClient {

    @GetMapping("/api/v1/internal/auctions/{id}/bid-context")
    BaseResponse<BidContext> getBidContext(@PathVariable("id") UUID auctionId);

    @PostMapping("/api/v1/internal/auctions/{id}/bids")
    BaseResponse<ApplyBidResult> applyBid(@PathVariable("id") UUID auctionId, @RequestBody ApplyBidCommand command);
}
