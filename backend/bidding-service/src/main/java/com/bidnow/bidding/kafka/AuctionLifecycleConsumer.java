package com.bidnow.bidding.kafka;

import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Drops the cached bid context when an auction closes or its end time changes, so pre-validation uses fresh state. */
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

    @KafkaListener(topics = "auction-extended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onAuctionExtended(AuctionExtendedEvent event) {
        log.info("Auction {} extended to {} - evicting bid context", event.getAuctionId(), event.getNewEndTime());
        contextCache.evict(event.getAuctionId());
    }
}
