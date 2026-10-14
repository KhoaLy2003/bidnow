package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.bidding.feign.DownstreamError;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/** Calls auction-service's authoritative, row-locked apply-bid and maps its errors to bidding errors. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBidGateway {

    private final AuctionServiceClient auctionServiceClient;
    private final ObjectMapper objectMapper;

    public ApplyBidResult apply(UUID auctionId, ApplyBidCommand command) {
        BaseResponse<ApplyBidResult> response;
        try {
            response = auctionServiceClient.applyBid(auctionId, command);
        } catch (FeignException ex) {
            throw toBiddingException(DownstreamError.of(ex, objectMapper), auctionId, command.bidId());
        }
        ApplyBidResult data = response == null ? null : response.getData();
        if (data == null) {
            log.error("auction-service returned an empty apply-bid result for bid {} on auction {}", command.bidId(), auctionId);
            throw new ServiceUnavailableException("Auction service is unavailable");
        }
        if (data.getCurrentPrice() == null || data.getEndTime() == null || data.getCurrentWinnerId() == null) {
            log.error("auction-service returned a partial apply-bid result for bid {} on auction {}: {}",
                    command.bidId(), auctionId, data);
            throw new ServiceUnavailableException("Auction service is unavailable");
        }
        return data;
    }

    private RuntimeException toBiddingException(DownstreamError error, UUID auctionId, UUID bidId) {
        String code = error.errorCode() == null ? "" : error.errorCode();
        if (error.status() == 409 && BiddingErrorCodes.AUCTION_NOT_OPEN.equals(code)) {
            return new ConflictException("Auction is not open for bidding", BiddingErrorCodes.AUCTION_NOT_OPEN);
        }
        if (error.status() == 400 && BiddingErrorCodes.BID_TOO_LOW.equals(code) && error.errors().containsKey("minimumBid")) {
            try {
                return new BidTooLowException(new BigDecimal(error.errors().get("minimumBid")));
            } catch (NumberFormatException invalidMinimum) {
                log.warn("auction-service returned a non-numeric minimumBid for bid {} on auction {}", bidId, auctionId);
            }
        }
        if (error.status() == 403 && BiddingErrorCodes.BID_OWN_AUCTION.equals(code)) {
            return new ForbiddenException("Sellers cannot bid on their own auction", BiddingErrorCodes.BID_OWN_AUCTION);
        }
        if (error.status() == 404) {
            return new NotFoundException("Auction not found: " + auctionId, BiddingErrorCodes.AUCTION_NOT_FOUND);
        }
        if (error.status() == -1) {
            log.error("CRITICAL: apply-bid outcome unknown for bid {} on auction {} - timeout or connection failure, "
                    + "auction-service may have applied the bid", bidId, auctionId);
            return new ServiceUnavailableException("Auction service is unavailable");
        }
        log.error("auction-service apply-bid failed for bid {} on auction {} (status {}, code {})",
                bidId, auctionId, error.status(), error.errorCode());
        return new ServiceUnavailableException("Auction service is unavailable");
    }
}
