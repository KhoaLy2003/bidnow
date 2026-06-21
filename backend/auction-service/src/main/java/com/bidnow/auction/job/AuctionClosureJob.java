package com.bidnow.auction.job;

import com.bidnow.auction.service.AuctionClosureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosureJob {

    private final AuctionClosureService closureService;

    /**
     * JobRunr entry point that closes the auction with the given ID. Delegates entirely to
     * {@link AuctionClosureService#close}, which is idempotent — retries triggered by JobRunr
     * ({@code retries = 3}) are safe and will no-op if the auction is already closed.
     *
     * @param auctionId the ID of the auction to close
     */
    @Job(name = "Close auction %0", retries = 3)
    public void closeAuction(UUID auctionId) {
        closureService.close(auctionId);
    }
}
