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

        kafkaProducer.publishAuctionEnded(AuctionEndedEvent.builder()
                .auctionId(auction.getId())
                .auctionTitle(auction.getTitle())
                .sellerId(auction.getSellerId())
                .winnerId(hasWinner ? auction.getWinnerId() : null)
                .winningBidAmount(hasWinner ? auction.getCurrentPrice() : null)
                .loserIds(List.of())
                .build());

        log.info("Closed auction {} (ACTIVE → {})", auctionId, newStatus);
    }

    public void scheduleClosureJob(UUID auctionId, Instant closeAt) {
        UUID jobId = closureJobId(auctionId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                BackgroundJob.<AuctionClosureJob>schedule(jobId, closeAt, job -> job.closeAuction(auctionId));
                log.info("Scheduled closure job {} for auction {} at {}", jobId, auctionId, closeAt);
            }
        });
    }

    static UUID closureJobId(UUID auctionId) {
        return UUID.nameUUIDFromBytes(
                ("auction-closure:" + auctionId).getBytes(StandardCharsets.UTF_8));
    }
}
