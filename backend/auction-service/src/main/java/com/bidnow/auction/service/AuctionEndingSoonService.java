package com.bidnow.auction.service;

import com.bidnow.auction.config.EndingSoonProperties;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.job.AuctionEndingSoonJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.util.AfterCommit;
import com.bidnow.common.dto.event.AuctionEndingSoonEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.BackgroundJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * "Ending soon" alerts (roadmap Story 7, Decision 7): one JobRunr job per configured threshold before the auction's
 * end time. A job carries the end time it was scheduled for; when it fires after an anti-sniping extension it
 * reschedules itself for the new end instead of alerting, so extensions need no hook on the bid path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionEndingSoonService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionKafkaProducer kafkaProducer;
    private final EndingSoonProperties properties;
    private final Clock clock;

    /** Deterministic per (auction, threshold, end time): rescheduling for the same end time is a JobRunr no-op. */
    static UUID jobId(UUID auctionId, int thresholdMinutes, Instant endTime) {
        return UUID.nameUUIDFromBytes(("auction-ending-soon:" + auctionId + ":" + thresholdMinutes + ":"
                + endTime.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Schedules every configured threshold still in the future for an auction ending at {@code endTime}. Jobs are
     * registered after the surrounding transaction commits, so it must run inside one.
     *
     * @throws IllegalStateException if no transaction synchronization is active
     */
    public void scheduleAll(UUID auctionId, Instant endTime) {
        for (int minutes : properties.thresholdsMinutes()) {
            scheduleOne(auctionId, minutes, endTime);
        }
    }

    /**
     * Fires one threshold. No-op unless the auction is still ACTIVE and has not ended; if its end time moved since
     * the job was scheduled (anti-sniping), the threshold is rescheduled for the new end instead.
     */
    @Transactional
    public void fire(UUID auctionId, int thresholdMinutes, long expectedEndEpochMilli) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            log.info("Ending-soon alert skipped - auction {} is gone or no longer active", auctionId);
            return;
        }
        Instant endTime = auction.getEndTime().toInstant();
        // Compare in millis: the job argument is millis, Postgres keeps microseconds
        if (endTime.toEpochMilli() != expectedEndEpochMilli) {
            log.info("Auction {} now ends at {} - {}-minute alert rescheduled", auctionId, endTime, thresholdMinutes);
            scheduleOne(auctionId, thresholdMinutes, endTime);
            return;
        }
        if (!clock.instant().isBefore(endTime)) {
            log.info("Ending-soon alert skipped - auction {} already reached its end time {}", auctionId, endTime);
            return;
        }
        AuctionEndingSoonEvent event = AuctionEndingSoonEvent.builder()
                .auctionId(auctionId)
                .auctionTitle(auction.getTitle())
                .sellerId(auction.getSellerId())
                .endTime(endTime)
                .thresholdMinutes(thresholdMinutes)
                .build();
        AfterCommit.run(() -> kafkaProducer.publishEndingSoon(event));
    }

    private void scheduleOne(UUID auctionId, int thresholdMinutes, Instant endTime) {
        Instant fireAt = endTime.minus(Duration.ofMinutes(thresholdMinutes));
        if (!fireAt.isAfter(clock.instant())) {
            log.debug("Auction {} {}-minute alert not scheduled - {} is already past", auctionId, thresholdMinutes, fireAt);
            return;
        }
        UUID jobId = jobId(auctionId, thresholdMinutes, endTime);
        long endMillis = endTime.toEpochMilli();
        AfterCommit.run(() -> {
            BackgroundJob.<AuctionEndingSoonJob>schedule(jobId, fireAt,
                    job -> job.notifyEndingSoon(auctionId, thresholdMinutes, endMillis));
            log.info("Scheduled {}-minute ending-soon job {} for auction {} at {}", thresholdMinutes, jobId, auctionId, fireAt);
        });
    }
}
