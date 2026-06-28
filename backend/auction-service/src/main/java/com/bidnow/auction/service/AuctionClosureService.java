package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.job.AuctionClosureJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.BackgroundJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosureService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final AuctionKafkaProducer kafkaProducer;

    /**
     * Derives a deterministic, name-based UUID for the closure job of a given auction. Using a
     * stable ID prevents duplicate jobs from accumulating in JobRunr if
     * {@link #scheduleClosureJob} is called more than once for the same auction (e.g., on restart).
     */
    static UUID closureJobId(UUID auctionId) {
        return UUID.nameUUIDFromBytes(
                ("auction-closure:" + auctionId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Closes an ACTIVE auction by transitioning it to COMPLETED (bids present) or FAILED (no bids),
     * recording a status-history entry, and publishing an {@link AuctionEndedEvent} after the DB
     * transaction commits.
     * <p>
     * The method is idempotent: if the auction is not in ACTIVE status it logs and returns without
     * any changes, making it safe to retry via JobRunr's {@code @Job(retries = 3)}.
     * <p>
     * <b>Kafka publish is best-effort.</b> The event is dispatched in an {@code afterCommit()} hook
     * so the DB is always consistent. If the broker is unreachable the event is lost — check logs
     * for CRITICAL-level alarms and republish manually if necessary.
     */
    @Transactional
    public void close(UUID auctionId) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElse(null);
        if (auction == null) {
            log.warn("Closure skipped — auction {} not found or deleted", auctionId);
            return;
        }
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            log.info("Closure skipped — auction {} is already in status {}", auctionId, auction.getStatus());
            return;
        }

        boolean hasWinner = auction.getTotalBids() > 0;
        AuctionStatus newStatus = hasWinner ? AuctionStatus.COMPLETED : AuctionStatus.FAILED;
        OffsetDateTime now = OffsetDateTime.now();

        if (hasWinner) {
            auction.setWinnerId(auction.getCurrentWinnerId());
            auction.setCompletedAt(now);
        }
        auction.setStatus(newStatus);
        auctionItemRepository.save(auction);

        auctionStatusHistoryRepository.save(AuctionStatusHistory.builder()
                .auction(auction)
                .fromStatus(AuctionStatus.ACTIVE.name())
                .toStatus(newStatus.name())
                .triggeredBy(null)
                .reason("Auction end time reached")
                .build());

        // TODO: loserIds will be populated by the bidding-service integration (not yet built).
        //       Until then this field is intentionally empty. Update the assertion in
        //       AuctionClosureServiceTest when the bidding-service provides loser data.
        AuctionEndedEvent event = AuctionEndedEvent.builder()
                .auctionId(auction.getId())
                .auctionTitle(auction.getTitle())
                .sellerId(auction.getSellerId())
                .winnerId(hasWinner ? auction.getWinnerId() : null)
                .winningBidAmount(hasWinner ? auction.getCurrentPrice() : null)
                .loserIds(List.of())
                .totalBids(auction.getTotalBids())
                .endedAt(now.toInstant())
                .closureSource("SCHEDULER")
                .build();

        // Publish after DB commit so the auction record is always consistent even if Kafka is down.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaProducer.publishAuctionEnded(event);
            }
        });

        log.info("Closed auction {} (ACTIVE → {})", auctionId, newStatus);
    }

    /**
     * Schedules a JobRunr background job to close the given auction at {@code closeAt}.
     * <p>
     * The job is registered inside an {@code afterCommit()} hook so it is only enqueued after the
     * surrounding transaction commits, preventing a job from firing for a rolled-back auction. The
     * job ID is derived deterministically from the auction ID so repeated calls are idempotent.
     * <p>
     * <b>Must be called within an active Spring transaction.</b> Calling this method without an
     * active transaction synchronization will throw {@link IllegalStateException}.
     *
     * @param auctionId the auction to close
     * @param closeAt   the instant at which the closure job should fire
     * @throws IllegalStateException if no transaction synchronization is active
     */
    public void scheduleClosureJob(UUID auctionId, Instant closeAt) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "scheduleClosureJob must be called within an active transaction (auctionId=" + auctionId + ")");
        }
        UUID jobId = closureJobId(auctionId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                BackgroundJob.<AuctionClosureJob>schedule(jobId, closeAt, job -> job.closeAuction(auctionId));
                log.info("Scheduled closure job {} for auction {} at {}", jobId, auctionId, closeAt);
            }
        });
    }
}
