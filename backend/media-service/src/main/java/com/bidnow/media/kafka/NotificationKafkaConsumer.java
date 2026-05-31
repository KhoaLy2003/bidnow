package com.bidnow.media.kafka;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Loggable
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "user-verification-requested-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserVerificationRequested(UserVerificationRequestedEvent event) {
        log.info("Received UserVerificationRequestedEvent for user: {}", event.getUserId());
        notificationService.handleUserVerificationRequested(event);
    }

    @KafkaListener(topics = "user-registered-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: {}", event);
        notificationService.handleUserRegistered(event);
    }

    @KafkaListener(topics = "auction-created-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionCreated(AuctionCreatedEvent event) {
        log.info("Received AuctionCreatedEvent: {}", event);
        notificationService.handleAuctionCreated(event);
    }

    @KafkaListener(topics = "bid-placed-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeBidPlaced(BidPlacedEvent event) {
        log.info("Received BidPlacedEvent: {}", event);
        notificationService.handleBidPlaced(event);
    }

    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionEnded(AuctionEndedEvent event) {
        log.info("Received AuctionEndedEvent: {}", event);
        notificationService.handleAuctionEnded(event);
    }

    @KafkaListener(topics = "payment-event-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("Received PaymentEvent: {}", event);
        notificationService.handlePaymentEvent(event);
    }
}
