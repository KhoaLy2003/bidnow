package com.bidnow.auction.kafka;

import com.bidnow.auction.service.AuctionPaymentService;
import com.bidnow.common.dto.event.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private AuctionPaymentService auctionPaymentService;

    @InjectMocks
    private PaymentEventConsumer consumer;

    @Test
    void onPaymentEvent_delegatesToTheService() {
        PaymentEvent event = PaymentEvent.builder()
                .auctionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .paymentType("COMPLETED")
                .build();

        consumer.onPaymentEvent(event);

        verify(auctionPaymentService).applyPayment(event);
    }

    @Test
    void onPaymentEvent_delegatesNullEvent() {
        // The listener is a pure pass-through; this pins that it does not dereference the event. With real
        // Kafka wiring a tombstone arrives as KafkaNull and goes straight to the DLT without reaching the
        // service, which is harmless because wallet-service never sends tombstones.
        consumer.onPaymentEvent(null);

        verify(auctionPaymentService).applyPayment(null);
    }

    @Test
    void onPaymentEvent_propagatesServiceFailureSoTheErrorHandlerRetries() {
        PaymentEvent event = PaymentEvent.builder().auctionId(UUID.randomUUID()).paymentType("FAILED").build();
        doThrow(new IllegalStateException("still ACTIVE")).when(auctionPaymentService).applyPayment(event);

        assertThatThrownBy(() -> consumer.onPaymentEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("still ACTIVE");
    }
}
