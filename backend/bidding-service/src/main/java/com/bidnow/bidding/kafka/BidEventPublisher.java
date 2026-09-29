package com.bidnow.bidding.kafka;

import com.bidnow.common.dto.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes accepted bids. Called only after the bid is committed and applied, so a publish failure
 * never fails the bid — it is logged CRITICAL (real-time clients miss the update; data is correct).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventPublisher {

    static final String BID_PLACED_TOPIC = "bid-placed-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBidPlaced(BidPlacedEvent event) {
        try {
            kafkaTemplate.send(BID_PLACED_TOPIC, event.getAuctionId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("CRITICAL: Failed to publish BidPlacedEvent for bid {} on auction {}",
                                    event.getBidId(), event.getAuctionId(), ex);
                        } else {
                            log.info("Published BidPlacedEvent for bid {} on auction {}", event.getBidId(), event.getAuctionId());
                        }
                    });
        } catch (RuntimeException ex) {
            log.error("CRITICAL: Failed to publish BidPlacedEvent for bid {} on auction {}",
                    event.getBidId(), event.getAuctionId(), ex);
        }
    }
}
