package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationIntent.EmailSpec;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bidnow.media.notification.NotificationFormats.deadline;
import static com.bidnow.media.notification.NotificationFormats.money;

/** Auction lifecycle events → seller / bidder notifications (roadmap Story 4 matrix). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionNotificationHandler {

    private final NotificationDispatcher dispatcher;
    private final AuctionLookup auctions;
    private final NotificationLinks links;

    public void auctionCreated(AuctionCreatedEvent event) {
        UUID auctionId = event.getAuctionId();
        if (event.getSellerId() == null) {
            log.warn("AuctionCreatedEvent for auction {} has no seller - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getTitle());
        String path = NotificationLinks.auctionPath(auctionId);
        dispatcher.dispatch(new NotificationIntent(event.getSellerId(), NotificationType.AUCTION_CREATED,
                DedupKeys.auctionCreated(auctionId), auctionId,
                "Your auction is live", "\"" + title + "\" is now open for bidding.", path, null,
                new EmailSpec("AUCTION_CREATED", Map.of("auctionTitle", title, "actionUrl", links.absolute(path)), false)));
    }

    /** Winner: in-app only (the "you won, please pay" email comes from PaymentEvent REQUIRED). Losers: in-app + email. */
    public void auctionEnded(AuctionEndedEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        String path = NotificationLinks.auctionPath(auctionId);
        List<NotificationIntent> intents = new ArrayList<>();
        UUID winner = event.getWinnerId();

        if (winner == null) {
            if (event.getSellerId() != null) {
                intents.add(new NotificationIntent(event.getSellerId(), NotificationType.AUCTION_UNSOLD,
                        DedupKeys.unsold(auctionId), auctionId, "Auction ended without a sale",
                        "\"" + title + "\" ended with no winning bid.", path, null, null));
            }
        } else {
            String bid = event.getWinningBidAmount() == null ? "" : " with a bid of " + money(event.getWinningBidAmount());
            intents.add(new NotificationIntent(winner, NotificationType.AUCTION_WON, DedupKeys.won(auctionId), auctionId,
                    "You won!", "You won \"" + title + "\"" + bid + ". Check your email for payment details.",
                    path, null, null));
            EmailSpec lostEmail = new EmailSpec("AUCTION_LOST",
                    Map.of("auctionTitle", title, "actionUrl", links.absolute(NotificationLinks.AUCTIONS_PATH)), false);
            for (UUID bidder : auctions.participants(auctionId)) {
                if (!bidder.equals(winner)) {
                    intents.add(new NotificationIntent(bidder, NotificationType.AUCTION_LOST, DedupKeys.lost(auctionId),
                            auctionId, "Auction ended", "\"" + title + "\" has ended. You were not the highest bidder.",
                            path, null, lostEmail));
                }
            }
        }
        dispatchIfAny(intents);
    }

    public void auctionCancelled(AuctionCancelledEvent event) {
        UUID auctionId = event.getAuctionId();
        String title = auctions.title(auctionId, event.getAuctionTitle());
        EmailSpec email = new EmailSpec("AUCTION_CANCELLED",
                Map.of("auctionTitle", title, "actionUrl", links.absolute(NotificationLinks.WALLET_PATH)), false);
        List<NotificationIntent> intents = new ArrayList<>();
        Set<UUID> notified = new LinkedHashSet<>();
        for (UUID bidder : auctions.participants(auctionId)) {
            if (notified.add(bidder)) {
                intents.add(new NotificationIntent(bidder, NotificationType.AUCTION_CANCELLED,
                        DedupKeys.cancelled(auctionId), auctionId, "Auction cancelled",
                        "\"" + title + "\" was cancelled. Any deposit you placed is being refunded to your wallet.",
                        NotificationLinks.WALLET_PATH, null, email));
            }
        }
        UUID seller = event.getSellerId() != null ? event.getSellerId() : auctions.sellerId(auctionId).orElse(null);
        if (seller != null && notified.add(seller)) {
            intents.add(new NotificationIntent(seller, NotificationType.AUCTION_CANCELLED,
                    DedupKeys.cancelled(auctionId), auctionId, "Auction cancelled",
                    "Your auction \"" + title + "\" was cancelled.",
                    NotificationLinks.SELLER_AUCTIONS_PATH, null, null));
        }
        dispatchIfAny(intents);
    }

    /** In-app only, to bidders and the seller; one notification per extension (keyed on the new end time). */
    public void auctionExtended(AuctionExtendedEvent event) {
        UUID auctionId = event.getAuctionId();
        if (event.getNewEndTime() == null) {
            log.warn("AuctionExtendedEvent for auction {} has no new end time - notification skipped", auctionId);
            return;
        }
        String title = auctions.title(auctionId, event.getAuctionTitle());
        Set<UUID> recipients = new LinkedHashSet<>(auctions.participants(auctionId));
        auctions.sellerId(auctionId).ifPresent(recipients::add);
        String message = "\"" + title + "\" was extended to " + deadline(event.getNewEndTime())
                + " after a last-minute bid.";
        String dedupKey = DedupKeys.extended(auctionId, event.getNewEndTime());
        List<NotificationIntent> intents = new ArrayList<>();
        for (UUID userId : recipients) {
            intents.add(new NotificationIntent(userId, NotificationType.AUCTION_EXTENDED, dedupKey, auctionId,
                    "Auction extended", message, NotificationLinks.auctionPath(auctionId), null, null));
        }
        dispatchIfAny(intents);
    }

    private void dispatchIfAny(List<NotificationIntent> intents) {
        if (!intents.isEmpty()) {
            dispatcher.dispatchAll(intents);
        }
    }
}
