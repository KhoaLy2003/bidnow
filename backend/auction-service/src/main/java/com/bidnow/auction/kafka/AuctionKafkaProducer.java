package com.bidnow.auction.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionKafkaProducer {

    private static final String AUCTION_CREATED_TOPIC = "auction-created-topic";
    private static final String AUCTION_CANCELLED_TOPIC = "auction-cancelled-topic";
    private static final String AUCTION_REJECTED_TOPIC = "auction-rejected-topic";
    private static final String AUCTION_ENDED_TOPIC = "auction-ended-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAuctionCreated(AuctionCreatedEvent event) {
        kafkaTemplate.send(AUCTION_CREATED_TOPIC, event.getAuctionId().toString(), event);
        log.info("Published AuctionCreatedEvent for auction: {}", event.getAuctionId());
    }

    public void publishAuctionCancelled(AuctionCancelledEvent event) {
        kafkaTemplate.send(AUCTION_CANCELLED_TOPIC, event.getAuctionId().toString(), event);
        log.info("Published AuctionCancelledEvent for auction: {}", event.getAuctionId());
    }

    public void publishAuctionRejected(AuctionRejectedEvent event) {
        kafkaTemplate.send(AUCTION_REJECTED_TOPIC, event.getAuctionId().toString(), event);
        log.info("Published AuctionRejectedEvent for auction: {}", event.getAuctionId());
    }

    public void publishAuctionEnded(AuctionEndedEvent event) {
        kafkaTemplate.send(AUCTION_ENDED_TOPIC, event.getAuctionId().toString(), event);
        log.info("Published AuctionEndedEvent for auction: {} (source={})", event.getAuctionId(), event.getClosureSource());
    }
}
