package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.projection.AuctionProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the auction/participant projection. Uses its own stable group: NotificationKafkaConsumer already
 * consumes bid-placed-topic in media-service-group, and two listeners in one group would split partitions.
 * Reads from the earliest offset on first start, which backfills bids still retained in Kafka.
 */
@Component
@RequiredArgsConstructor
public class AuctionProjectionConsumer {

    private static final String PROJECTION_GROUP = "media-projection-group";

    private final AuctionProjectionService projectionService;

    @KafkaListener(topics = "auction-created-topic", groupId = PROJECTION_GROUP)
    public void onAuctionCreated(AuctionCreatedEvent event) {
        projectionService.onAuctionCreated(event);
    }

    @KafkaListener(topics = "bid-placed-topic", groupId = PROJECTION_GROUP)
    public void onBidPlaced(BidPlacedEvent event) {
        projectionService.onBidPlaced(event);
    }

    @KafkaListener(topics = "auction-extended-topic", groupId = PROJECTION_GROUP)
    public void onAuctionExtended(AuctionExtendedEvent event) {
        projectionService.onAuctionExtended(event);
    }
}
