package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionActivationService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final AuctionKafkaProducer kafkaProducer;
    private final AuctionClosureService closureService;

    /**
     * Transitions a SCHEDULED auction to ACTIVE once its {@code startTime} has been reached,
     * records a status-history entry, publishes an {@link com.bidnow.common.dto.event.AuctionCreatedEvent},
     * and schedules a closure job for the auction's {@code endTime} via
     * {@link AuctionClosureService#scheduleClosureJob}.
     * <p>
     * The method is idempotent: if the auction is not in SCHEDULED status or {@code startTime} has
     * not yet passed, it logs and returns without changes. This makes it safe to call from startup
     * recovery in addition to the normal JobRunr activation path.
     *
     * @param auctionId the ID of the auction to activate
     */
    @Transactional
    public void activate(UUID auctionId) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElse(null);
        if (auction == null) {
            log.warn("Activation skipped — auction {} not found or deleted", auctionId);
            return;
        }
        if (auction.getStatus() != AuctionStatus.SCHEDULED) {
            log.info("Activation skipped — auction {} is already in status {}", auctionId, auction.getStatus());
            return;
        }
        if (Instant.now().isBefore(auction.getStartTime().toInstant())) {
            log.warn("Activation job fired before startTime for auction {} — expected {}, skipping",
                    auctionId, auction.getStartTime());
            return;
        }

        auction.setStatus(AuctionStatus.ACTIVE);
        auctionItemRepository.save(auction);

        auctionStatusHistoryRepository.save(AuctionStatusHistory.builder()
                .auction(auction)
                .fromStatus(AuctionStatus.SCHEDULED.name())
                .toStatus(AuctionStatus.ACTIVE.name())
                .triggeredBy(null)
                .reason("Scheduled start time reached")
                .build());

        kafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                .auctionId(auction.getId())
                .sellerId(auction.getSellerId())
                .title(auction.getTitle())
                .startingPrice(auction.getStartingPrice())
                .endTime(auction.getEndTime().toInstant())
                .build());

        closureService.scheduleClosureJob(auctionId, auction.getEndTime().toInstant());
        log.info("Activated auction {} (SCHEDULED → ACTIVE)", auctionId);
    }
}
