package com.bidnow.media.kafka;

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
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private AuctionNotificationHandler auctionHandler;
    @Mock
    private BidNotificationHandler bidHandler;
    @Mock
    private PaymentNotificationHandler paymentHandler;

    @InjectMocks
    private NotificationKafkaConsumer consumer;

    @Test
    void delegatesEachTopicToItsHandler() {
        UUID id = UUID.randomUUID();
        UserVerificationRequestedEvent otp = UserVerificationRequestedEvent.builder().userId(id).build();
        UserRegisteredEvent registered = UserRegisteredEvent.builder().userId(id).build();
        AuctionCreatedEvent created = AuctionCreatedEvent.builder().auctionId(id).build();
        BidPlacedEvent bid = BidPlacedEvent.builder().auctionId(id).build();
        AuctionEndedEvent ended = AuctionEndedEvent.builder().auctionId(id).build();
        AuctionCancelledEvent cancelled = AuctionCancelledEvent.builder().auctionId(id).build();
        AuctionExtendedEvent extended = AuctionExtendedEvent.builder().auctionId(id).build();
        PaymentEvent payment = PaymentEvent.builder().auctionId(id).build();
        DepositRefundedEvent refunded = DepositRefundedEvent.builder().auctionId(id).build();

        consumer.consumeUserVerificationRequested(otp);
        consumer.consumeUserRegistered(registered);
        consumer.consumeAuctionCreated(created);
        consumer.consumeBidPlaced(bid);
        consumer.consumeAuctionEnded(ended);
        consumer.consumeAuctionCancelled(cancelled);
        consumer.consumeAuctionExtended(extended);
        consumer.consumePaymentEvent(payment);
        consumer.consumeDepositRefunded(refunded);

        verify(notificationService).handleUserVerificationRequested(otp);
        verify(notificationService).handleUserRegistered(registered);
        verify(auctionHandler).auctionCreated(created);
        verify(bidHandler).bidPlaced(bid);
        verify(auctionHandler).auctionEnded(ended);
        verify(auctionHandler).auctionCancelled(cancelled);
        verify(auctionHandler).auctionExtended(extended);
        verify(paymentHandler).paymentEvent(payment);
        verify(paymentHandler).depositRefunded(refunded);
    }

    @Test
    void everyListenerUsesTheSharedNotificationGroup() {
        Map<String, KafkaListener> listeners = Arrays.stream(NotificationKafkaConsumer.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .collect(Collectors.toMap(Method::getName, m -> m.getAnnotation(KafkaListener.class)));

        assertThat(listeners).hasSize(9);
        assertThat(listeners.get("consumeUserVerificationRequested").topics()).containsExactly("user-verification-requested-topic");
        assertThat(listeners.get("consumeUserRegistered").topics()).containsExactly("user-registered-topic");
        assertThat(listeners.get("consumeAuctionCreated").topics()).containsExactly("auction-created-topic");
        assertThat(listeners.get("consumeBidPlaced").topics()).containsExactly("bid-placed-topic");
        assertThat(listeners.get("consumeAuctionEnded").topics()).containsExactly("auction-ended-topic");
        assertThat(listeners.get("consumeAuctionCancelled").topics()).containsExactly("auction-cancelled-topic");
        assertThat(listeners.get("consumeAuctionExtended").topics()).containsExactly("auction-extended-topic");
        assertThat(listeners.get("consumePaymentEvent").topics()).containsExactly("payment-event-topic");
        assertThat(listeners.get("consumeDepositRefunded").topics()).containsExactly("deposit-refunded-topic");
        listeners.values().forEach(l -> assertThat(l.groupId()).isEqualTo("${spring.kafka.consumer.group-id}"));
    }
}
