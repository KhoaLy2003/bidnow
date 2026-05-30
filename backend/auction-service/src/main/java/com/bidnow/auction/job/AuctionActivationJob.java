package com.bidnow.auction.job;

import com.bidnow.auction.service.AuctionActivationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionActivationJob {

    private final AuctionActivationService activationService;

    @Job(name = "Activate auction %0", retries = 3)
    public void activateAuction(UUID auctionId) {
        activationService.activate(auctionId);
    }
}
