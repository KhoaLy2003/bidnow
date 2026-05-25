package com.bidnow.auction.kafka;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionKafkaProducer {

    private static final String AUCTION_CREATED_TOPIC = "auction-created-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAuctionCreated(AuctionCreatedEvent event) {
        kafkaTemplate.send(AUCTION_CREATED_TOPIC, event.getAuctionId().toString(), event);
        log.info("Published AuctionCreatedEvent for auction: {}", event.getAuctionId());
    }
}
