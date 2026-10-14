package com.bidnow.media.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.realtime.AuctionRealtimeBroadcaster;
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
class AuctionRealtimeConsumerTest {

    @Mock
    private AuctionRealtimeBroadcaster broadcaster;

    @InjectMocks
    private AuctionRealtimeConsumer consumer;

    @Test
    void delegatesEachEventToTheBroadcaster() {
        BidPlacedEvent bid = BidPlacedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionExtendedEvent extended = AuctionExtendedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionEndedEvent ended = AuctionEndedEvent.builder().auctionId(UUID.randomUUID()).build();
        AuctionCancelledEvent cancelled = AuctionCancelledEvent.builder().auctionId(UUID.randomUUID()).build();

        consumer.onBidPlaced(bid);
        consumer.onAuctionExtended(extended);
        consumer.onAuctionEnded(ended);
        consumer.onAuctionCancelled(cancelled);

        verify(broadcaster).bidPlaced(bid);
        verify(broadcaster).auctionExtended(extended);
        verify(broadcaster).auctionEnded(ended);
        verify(broadcaster).auctionCancelled(cancelled);
    }

    @Test
    void everyListenerUsesPerInstanceGroupReadingOnlyNewEvents() {
        Map<String, KafkaListener> listeners = Arrays.stream(AuctionRealtimeConsumer.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .collect(Collectors.toMap(Method::getName, m -> m.getAnnotation(KafkaListener.class)));

        assertThat(listeners).containsOnlyKeys("onBidPlaced", "onAuctionExtended", "onAuctionEnded", "onAuctionCancelled");
        assertThat(listeners.get("onBidPlaced").topics()).containsExactly("bid-placed-topic");
        assertThat(listeners.get("onAuctionExtended").topics()).containsExactly("auction-extended-topic");
        assertThat(listeners.get("onAuctionEnded").topics()).containsExactly("auction-ended-topic");
        assertThat(listeners.get("onAuctionCancelled").topics()).containsExactly("auction-cancelled-topic");
        listeners.values().forEach(listener -> {
            assertThat(listener.groupId()).isEqualTo("media-realtime-${random.uuid}");
            assertThat(listener.properties()).containsExactly("auto.offset.reset=latest");
        });
    }
}
