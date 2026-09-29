package com.bidnow.bidding.kafka;

import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Drops the cached bid context when an auction closes so pre-validation rejects new bids immediately. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleConsumer {

    private final AuctionContextCacheService contextCache;

    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionEnded(AuctionEndedEvent event) {
        log.info("Auction {} ended - evicting bid context", event.getAuctionId());
        contextCache.evict(event.getAuctionId());
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionCancelled(AuctionCancelledEvent event) {
        log.info("Auction {} cancelled - evicting bid context", event.getAuctionId());
        contextCache.evict(event.getAuctionId());
    }
}
