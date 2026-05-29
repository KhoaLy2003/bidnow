package com.bidnow.auction.job;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionActivationJob {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final AuctionKafkaProducer kafkaProducer;

    @Job(name = "Activate auction %0", retries = 3)
    @Transactional
    public void activateAuction(UUID auctionId) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElse(null);
        if (auction == null) {
            log.warn("Activation job skipped — auction {} not found or deleted", auctionId);
            return;
        }
        if (auction.getStatus() != AuctionStatus.SCHEDULED) {
            log.info("Activation job skipped — auction {} is already in status {}", auctionId, auction.getStatus());
            return;
        }

        auction.setStatus(AuctionStatus.ACTIVE);
        auctionItemRepository.save(auction);

        AuctionStatusHistory history = AuctionStatusHistory.builder()
                .auction(auction)
                .fromStatus(AuctionStatus.SCHEDULED.name())
                .toStatus(AuctionStatus.ACTIVE.name())
                .triggeredBy(null)
                .reason("Scheduled start time reached")
                .build();
        auctionStatusHistoryRepository.save(history);

        kafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                .auctionId(auction.getId())
                .sellerId(auction.getSellerId())
                .title(auction.getTitle())
                .startingPrice(auction.getStartingPrice())
                .endTime(auction.getEndTime().toInstant())
                .build());

        log.info("Activated auction {} (SCHEDULED → ACTIVE)", auctionId);
    }
}
