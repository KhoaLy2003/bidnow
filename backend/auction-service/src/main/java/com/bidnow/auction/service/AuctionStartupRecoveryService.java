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

    /**
     * Recovers auctions that were missed while the server was down. Runs once on startup after the
     * application context is fully ready ({@link ApplicationReadyEvent}).
     * <p>
     * Two recovery paths:
     * <ol>
     *   <li><b>ACTIVE + endTime passed:</b> the auction should have been closed by JobRunr but the
     *       job never fired. {@link AuctionClosureService#close} is called directly.</li>
     *   <li><b>SCHEDULED + startTime passed:</b> the activation job never ran.
     *       {@link AuctionActivationService#activate} is called first, which transitions the auction
     *       to ACTIVE and schedules a new closure job via {@code afterCommit()} for auctions whose
     *       {@code endTime} is still in the future. If {@code endTime} has also passed,
     *       {@link AuctionClosureService#close} is called immediately after.</li>
     * </ol>
     * Each auction is wrapped in its own try/catch so a single failure cannot block the rest.
     * Activate and close failures are isolated: if activation fails, close is skipped for that
     * auction.
     */
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
                // activate() transitions to ACTIVE and, for auctions with endTime > now, schedules
                // a new closure job via afterCommit(). For auctions with endTime already passed, we
                // call close() explicitly below.
                activationService.activate(auction.getId());
            } catch (Exception e) {
                log.error("Failed to activate overdue SCHEDULED auction {}: {}", auction.getId(), e.getMessage());
                continue;
            }
            if (!auction.getEndTime().isAfter(now)) {
                try {
                    // activate() already scheduled a closure job via afterCommit; calling close() here is intentional
                    // and will be a no-op thanks to the idempotency guard in AuctionClosureService.close()
                    closureService.close(auction.getId());
                } catch (Exception e) {
                    log.error("Failed to close overdue SCHEDULED auction {} after activation: {}", auction.getId(), e.getMessage());
                }
            }
        }

        log.info("Auction recovery scan complete");
    }
}
