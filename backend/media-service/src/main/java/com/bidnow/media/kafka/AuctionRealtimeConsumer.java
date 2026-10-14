package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.realtime.AuctionRealtimeBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds real-time STOMP pushes. Every media instance must see every event (each forwards to its own
 * connected clients), so each listener uses a per-instance consumer group, and reads only new events
 * - replaying history to live browsers would be wrong. Email/notification processing stays in
 * {@link NotificationKafkaConsumer} with the shared group.
 */
@Component
@RequiredArgsConstructor
public class AuctionRealtimeConsumer {

    private static final String PER_INSTANCE_GROUP = "media-realtime-${random.uuid}";
    private static final String NEW_EVENTS_ONLY = "auto.offset.reset=latest";

    private final AuctionRealtimeBroadcaster broadcaster;

    @KafkaListener(topics = "bid-placed-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onBidPlaced(BidPlacedEvent event) {
        broadcaster.bidPlaced(event);
    }

    @KafkaListener(topics = "auction-extended-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onAuctionExtended(AuctionExtendedEvent event) {
        broadcaster.auctionExtended(event);
    }

    @KafkaListener(topics = "auction-ended-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onAuctionEnded(AuctionEndedEvent event) {
        broadcaster.auctionEnded(event);
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = PER_INSTANCE_GROUP, properties = NEW_EVENTS_ONLY)
    public void onAuctionCancelled(AuctionCancelledEvent event) {
        broadcaster.auctionCancelled(event);
    }
}
