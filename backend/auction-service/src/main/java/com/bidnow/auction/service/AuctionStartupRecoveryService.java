package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStartupRecoveryService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionClosureService closureService;
    private final AuctionActivationService activationService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOverdueAuctions() {
        OffsetDateTime now = OffsetDateTime.now();
        log.info("Starting auction recovery scan at {}", now);

        List<AuctionItem> overdueActive = auctionItemRepository
                .findByStatusAndEndTimeBeforeAndDeletedAtIsNull(AuctionStatus.ACTIVE, now);
        log.info("Found {} overdue ACTIVE auctions", overdueActive.size());
        for (AuctionItem auction : overdueActive) {
            try {
                closureService.close(auction.getId());
            } catch (Exception e) {
                log.error("Failed to close overdue ACTIVE auction {}: {}", auction.getId(), e.getMessage());
            }
        }

        List<AuctionItem> overdueScheduled = auctionItemRepository
                .findByStatusAndStartTimeBeforeAndDeletedAtIsNull(AuctionStatus.SCHEDULED, now);
        log.info("Found {} overdue SCHEDULED auctions", overdueScheduled.size());
        for (AuctionItem auction : overdueScheduled) {
            try {
                activationService.activate(auction.getId());
                if (!auction.getEndTime().isAfter(now)) {
                    closureService.close(auction.getId());
                }
            } catch (Exception e) {
                log.error("Failed to recover overdue SCHEDULED auction {}: {}", auction.getId(), e.getMessage());
            }
        }

        log.info("Auction recovery scan complete");
    }
}
