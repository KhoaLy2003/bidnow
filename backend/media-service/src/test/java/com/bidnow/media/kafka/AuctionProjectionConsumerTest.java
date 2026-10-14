package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.projection.AuctionProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionProjectionConsumerTest {

    @Mock
    private AuctionProjectionService projectionService;

    @InjectMocks
    private AuctionProjectionConsumer consumer;

    @Test
    void delegatesEachEvent() {
        AuctionCreatedEvent created = AuctionCreatedEvent.builder().auctionId(UUID.randomUUID()).build();
        BidPlacedEvent bid = BidPlacedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionExtendedEvent extended = AuctionExtendedEvent.builder().auctionId(UUID.randomUUID()).build();

        consumer.onAuctionCreated(created);
        consumer.onBidPlaced(bid);
        consumer.onAuctionExtended(extended);

        verify(projectionService).onAuctionCreated(created);
        verify(projectionService).onBidPlaced(bid);
        verify(projectionService).onAuctionExtended(extended);
    }

    @Test
    void listenersUseTheirOwnStableGroup() {
        Map<String, KafkaListener> listeners = Arrays.stream(AuctionProjectionConsumer.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .collect(Collectors.toMap(Method::getName, m -> m.getAnnotation(KafkaListener.class)));

        assertThat(listeners).containsOnlyKeys("onAuctionCreated", "onBidPlaced", "onAuctionExtended");
        assertThat(listeners.get("onAuctionCreated").topics()).containsExactly("auction-created-topic");
        assertThat(listeners.get("onBidPlaced").topics()).containsExactly("bid-placed-topic");
        assertThat(listeners.get("onAuctionExtended").topics()).containsExactly("auction-extended-topic");
        // Not media-service-group: NotificationKafkaConsumer already listens to bid-placed-topic in that
        // group, and two listeners in one group split the partitions between them.
        listeners.values().forEach(l -> assertThat(l.groupId()).isEqualTo("media-projection-group"));
    }
}
