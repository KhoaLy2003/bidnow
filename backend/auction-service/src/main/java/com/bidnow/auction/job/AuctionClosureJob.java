package com.bidnow.auction.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Jobrunr job that triggers auction closure.
 * Stub created in Task 2 to satisfy the compile-time reference in AuctionClosureService.
 * Full implementation is in Task 3.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosureJob {

    public void closeAuction(UUID auctionId) {
        throw new UnsupportedOperationException("AuctionClosureJob not yet implemented — Task 3");
    }
}
