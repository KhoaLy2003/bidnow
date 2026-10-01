package com.bidnow.media.kafka;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.common.dto.event.UserVerificationRequestedEvent;
import com.bidnow.media.notification.handler.AuctionNotificationHandler;
import com.bidnow.media.notification.handler.BidNotificationHandler;
import com.bidnow.media.notification.handler.PaymentNotificationHandler;
import com.bidnow.media.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Domain events → notifications, on the shared notification consumer group (one listener per topic). */
@Component
@RequiredArgsConstructor
@Slf4j
@Loggable
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final AuctionNotificationHandler auctionHandler;
    private final BidNotificationHandler bidHandler;
    private final PaymentNotificationHandler paymentHandler;

    @KafkaListener(topics = "user-verification-requested-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserVerificationRequested(UserVerificationRequestedEvent event) {
        log.info("Received UserVerificationRequestedEvent for user: {}", event.getUserId());
        notificationService.handleUserVerificationRequested(event);
    }

    @KafkaListener(topics = "user-registered-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for user: {}", event.getUserId());
        notificationService.handleUserRegistered(event);
    }

    @KafkaListener(topics = "auction-created-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionCreated(AuctionCreatedEvent event) {
        log.info("Received AuctionCreatedEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionCreated(event);
    }

    @KafkaListener(topics = "bid-placed-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeBidPlaced(BidPlacedEvent event) {
        log.info("Received BidPlacedEvent for auction: {}", event.getAuctionId());
        bidHandler.bidPlaced(event);
    }

    @KafkaListener(topics = "auction-ended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionEnded(AuctionEndedEvent event) {
        log.info("Received AuctionEndedEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionEnded(event);
    }

    @KafkaListener(topics = "auction-cancelled-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionCancelled(AuctionCancelledEvent event) {
        log.info("Received AuctionCancelledEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionCancelled(event);
    }

    @KafkaListener(topics = "auction-extended-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAuctionExtended(AuctionExtendedEvent event) {
        log.info("Received AuctionExtendedEvent for auction: {}", event.getAuctionId());
        auctionHandler.auctionExtended(event);
    }

    @KafkaListener(topics = "payment-event-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("Received PaymentEvent {} for auction: {}", event.getPaymentType(), event.getAuctionId());
        paymentHandler.paymentEvent(event);
    }

    @KafkaListener(topics = "deposit-refunded-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeDepositRefunded(DepositRefundedEvent event) {
        log.info("Received DepositRefundedEvent for auction: {}", event.getAuctionId());
        paymentHandler.depositRefunded(event);
    }
}
