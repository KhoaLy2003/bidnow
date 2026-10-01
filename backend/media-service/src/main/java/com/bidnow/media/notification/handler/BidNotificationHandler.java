package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.money;

/** Bid events → seller notifications. Only the first bid for now; outbid / new-bid batching is Story 5. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;

    public void bidPlaced(BidPlacedEvent event) {
        if (event.getTotalBids() == null || event.getTotalBids() != 1) {
            return;
        }
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
