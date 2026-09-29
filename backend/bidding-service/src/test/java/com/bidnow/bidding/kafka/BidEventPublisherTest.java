package com.bidnow.bidding.kafka;

import com.bidnow.common.dto.event.BidPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private BidEventPublisher publisher;

    private static BidPlacedEvent event() {
        return BidPlacedEvent.builder().auctionId(UUID.randomUUID()).bidId(UUID.randomUUID()).build();
    }

    @Test
    void publishes_toBidPlacedTopicKeyedByAuction() {
        BidPlacedEvent event = event();
        when(kafkaTemplate.send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishBidPlaced(event);

        verify(kafkaTemplate).send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event);
    }

    @Test
    void asyncSendFailure_isLoggedNotThrown() {
        BidPlacedEvent event = event();
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture.failedFuture(new KafkaException("down"));
        when(kafkaTemplate.send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event)).thenReturn(failed);

        assertThatCode(() -> publisher.publishBidPlaced(event)).doesNotThrowAnyException();
    }

    @Test
    void synchronousSendFailure_isLoggedNotThrown() {
        BidPlacedEvent event = event();
        when(kafkaTemplate.send(BidEventPublisher.BID_PLACED_TOPIC, event.getAuctionId().toString(), event))
                .thenThrow(new KafkaException("metadata timeout"));

        assertThatCode(() -> publisher.publishBidPlaced(event)).doesNotThrowAnyException();
    }
}
