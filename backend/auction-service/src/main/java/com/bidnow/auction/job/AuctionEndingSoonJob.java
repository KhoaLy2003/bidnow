package com.bidnow.auction.job;

import com.bidnow.auction.service.AuctionEndingSoonService;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuctionEndingSoonJob {

    private final AuctionEndingSoonService endingSoonService;

    /**
     * JobRunr entry point for one "ending soon" threshold. {@code expectedEndEpochMilli} is the end time the job was
     * scheduled for; {@link AuctionEndingSoonService#fire} compares it with the auction's current end time.
     */
    @Job(name = "Ending-soon alert for auction %0 (%1 min)", retries = 3)
    public void notifyEndingSoon(UUID auctionId, int thresholdMinutes, long expectedEndEpochMilli) {
        endingSoonService.fire(auctionId, thresholdMinutes, expectedEndEpochMilli);
    }
}
