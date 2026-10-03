package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.notification.batch.BidAlert;
import com.bidnow.media.notification.batch.BidAlertKind;
import com.bidnow.media.notification.batch.BidAlerts;
import com.bidnow.media.notification.batch.BidBatchOutcome;
import com.bidnow.media.notification.batch.BidBatcher;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.money;

/**
 * Bid events → in-app alerts. The first bid tells the seller immediately (FIRST_BID). Later bids alert the
 * previous leader (BID_OUTBID) and the seller (NEW_BID) through 5-minute batching windows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;
    private final BidBatcher batcher;

    public void bidPlaced(BidPlacedEvent event) {
        Integer totalBids = event.getTotalBids();
        if (totalBids != null && totalBids == 1) {
            firstBid(event);
            return;
        }
        UUID auctionId = event.getAuctionId();
        if (event.getBidId() == null) {
            log.warn("Bid on auction {} has no bidId - outbid / new-bid alerts skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        UUID previous = event.getPreviousHighestBidderId();
        if (previous != null && !previous.equals(event.getBidderId())) {
            alert(new BidAlert(BidAlertKind.OUTBID, previous, auctionId, event.getBidId(), title, event.getBidAmount()));
        }
        if (totalBids != null && totalBids > 1) {
            Optional<UUID> seller = auctions.sellerId(auctionId);
            if (seller.isPresent()) {
                alert(new BidAlert(BidAlertKind.NEW_BID, seller.get(), auctionId, event.getBidId(), title,
                        event.getBidAmount()));
            } else {
                log.debug("Bid on auction {} but its seller is not projected yet - new-bid alert skipped", auctionId);
            }
        }
    }

    private void alert(BidAlert alert) {
        if (batcher.record(alert) == BidBatchOutcome.IMMEDIATE) {
            dispatcher.dispatch(BidAlerts.immediate(alert));
        }
    }

    private void firstBid(BidPlacedEvent event) {
        UUID auctionId = event.getAuctionId();
        Optional<UUID> seller = auctions.sellerId(auctionId);
        if (seller.isEmpty()) {
            log.warn("First bid on auction {} but its seller is not projected yet - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        dispatcher.dispatch(new NotificationIntent(seller.get(), NotificationType.FIRST_BID,
                DedupKeys.firstBid(auctionId), auctionId, "First bid received",
                "\"" + title + "\" received its first bid: " + money(event.getBidAmount()) + ".",
                NotificationLinks.auctionPath(auctionId), null, null));
    }
}
