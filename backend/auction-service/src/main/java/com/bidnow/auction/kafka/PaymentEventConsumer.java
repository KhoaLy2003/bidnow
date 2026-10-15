package com.bidnow.auction.kafka;

import com.bidnow.auction.service.AuctionPaymentService;
import com.bidnow.common.dto.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives wallet-service's payment events and hands them to {@link AuctionPaymentService}.
 * Deliberately not transactional and free of logic: the service owns the transaction and the guards,
 * and an exception it throws propagates so the Kafka error handler retries, then sends to the DLT.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final AuctionPaymentService auctionPaymentService;

    @KafkaListener(topics = "payment-event-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentEvent(PaymentEvent event) {
        auctionPaymentService.applyPayment(event);
    }
}
