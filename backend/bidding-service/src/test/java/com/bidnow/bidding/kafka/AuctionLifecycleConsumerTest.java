package com.bidnow.bidding.kafka;

import com.bidnow.bidding.service.AuctionContextCacheService;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleConsumerTest {

    @Mock
    private AuctionContextCacheService contextCache;

    @InjectMocks
    private AuctionLifecycleConsumer consumer;

    @Test
    void auctionEnded_evictsContext() {
        UUID auctionId = UUID.randomUUID();

        consumer.onAuctionEnded(AuctionEndedEvent.builder().auctionId(auctionId).build());

        verify(contextCache).evict(auctionId);
    }

    @Test
    void auctionCancelled_evictsContext() {
        UUID auctionId = UUID.randomUUID();

        consumer.onAuctionCancelled(AuctionCancelledEvent.builder().auctionId(auctionId).build());

        verify(contextCache).evict(auctionId);
    }
}
