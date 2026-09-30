package com.bidnow.media.realtime;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionRealtimeBroadcasterTest {

    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID BIDDER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PREVIOUS = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String TOPIC = "/topic/auctions/b0000000-0000-0000-0000-000000000005";

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private AuctionRealtimeBroadcaster broadcaster;

    private static BidPlacedEvent bidPlaced(UUID previousHighestBidderId) {
        return BidPlacedEvent.builder()
                .bidId(UUID.randomUUID()).auctionId(AUCTION_ID).auctionTitle("Vintage Watch")
                .bidderId(BIDDER).bidderName("Bob").bidAmount(new BigDecimal("105.00"))
                .bidTime(LocalDateTime.of(2026, 10, 1, 12, 0))
                .previousHighestBidderId(previousHighestBidderId).isAntiSnipingTriggered(true)
                .totalBids(2).endTime(OffsetDateTime.parse("2026-10-01T12:05:00Z"))
                .build();
    }

    private AuctionRealtimeMessage capturedTopicMessage() {
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(TOPIC), message.capture());
        return (AuctionRealtimeMessage) message.getValue();
    }

    @Test
    void auctionTopic_hasContractFormat() {
        assertThat(AuctionRealtimeBroadcaster.auctionTopic(AUCTION_ID)).isEqualTo(TOPIC);
    }

    @Test
    void bidPlaced_broadcastsToAuctionTopic() {
        BidPlacedEvent event = bidPlaced(null);

        broadcaster.bidPlaced(event);

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.BID_PLACED);
        assertThat(message.auctionId()).isEqualTo(AUCTION_ID);
        RealtimePayloads.BidPlaced payload = (RealtimePayloads.BidPlaced) message.payload();
        assertThat(payload.bidId()).isEqualTo(event.getBidId());
        assertThat(payload.bidderId()).isEqualTo(BIDDER);
        assertThat(payload.bidderName()).isEqualTo("Bob");
        assertThat(payload.amount()).isEqualByComparingTo("105.00");
        assertThat(payload.placedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(payload.totalBids()).isEqualTo(2);
        assertThat(payload.endTime()).isEqualTo(OffsetDateTime.parse("2026-10-01T12:05:00Z"));
        assertThat(payload.antiSnipingTriggered()).isTrue();
    }

    @Test
    void bidPlaced_notifiesOutbidPreviousLeader() {
        broadcaster.bidPlaced(bidPlaced(PREVIOUS));

        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq(PREVIOUS.toString()),
                eq(AuctionRealtimeBroadcaster.USER_QUEUE), message.capture());
        AuctionRealtimeMessage outbid = (AuctionRealtimeMessage) message.getValue();
        assertThat(outbid.type()).isEqualTo(AuctionRealtimeMessage.OUTBID);
        assertThat(outbid.auctionId()).isEqualTo(AUCTION_ID);
        RealtimePayloads.Outbid payload = (RealtimePayloads.Outbid) outbid.payload();
        assertThat(payload.auctionTitle()).isEqualTo("Vintage Watch");
        assertThat(payload.currentPrice()).isEqualByComparingTo("105.00");
        assertThat(payload.newLeaderName()).isEqualTo("Bob");
    }

    @Test
    void bidPlaced_noPreviousLeader_sendsNoOutbid() {
        broadcaster.bidPlaced(bidPlaced(null));

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void bidPlaced_leaderRaisingOwnBid_sendsNoOutbid() {
        broadcaster.bidPlaced(bidPlaced(BIDDER));

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void auctionExtended_broadcastsNewEndTime() {
        broadcaster.auctionExtended(AuctionExtendedEvent.builder().auctionId(AUCTION_ID)
                .previousEndTime(Instant.parse("2026-10-01T12:00:00Z"))
                .newEndTime(Instant.parse("2026-10-01T12:05:00Z")).extensionCount(1).build());

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.AUCTION_EXTENDED);
        RealtimePayloads.AuctionExtended payload = (RealtimePayloads.AuctionExtended) message.payload();
        assertThat(payload.newEndTime()).isEqualTo(Instant.parse("2026-10-01T12:05:00Z"));
        assertThat(payload.previousEndTime()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(payload.extensionCount()).isEqualTo(1);
    }

    @Test
    void auctionEnded_broadcastsWinnerAndFinalPrice() {
        broadcaster.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION_ID).winnerId(BIDDER)
                .winningBidAmount(new BigDecimal("300.00")).endedAt(Instant.parse("2026-10-01T13:00:00Z")).build());

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.AUCTION_ENDED);
        RealtimePayloads.AuctionEnded payload = (RealtimePayloads.AuctionEnded) message.payload();
        assertThat(payload.winnerId()).isEqualTo(BIDDER);
        assertThat(payload.finalPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void auctionEnded_withoutWinner_isStillBroadcast() {
        broadcaster.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION_ID).build());

        RealtimePayloads.AuctionEnded payload = (RealtimePayloads.AuctionEnded) capturedTopicMessage().payload();
        assertThat(payload.winnerId()).isNull();
        assertThat(payload.finalPrice()).isNull();
    }

    @Test
    void auctionCancelled_broadcastsReason() {
        broadcaster.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION_ID).reason("Fraud").build());

        AuctionRealtimeMessage message = capturedTopicMessage();
        assertThat(message.type()).isEqualTo(AuctionRealtimeMessage.AUCTION_CANCELLED);
        assertThat(((RealtimePayloads.AuctionCancelled) message.payload()).reason()).isEqualTo("Fraud");
    }

    @Test
    void sendFailure_isSwallowed() {
        doThrow(new MessageDeliveryException("broker down")).when(messagingTemplate).convertAndSend(eq(TOPIC), any(Object.class));

        assertThatCode(() -> broadcaster.auctionCancelled(
                AuctionCancelledEvent.builder().auctionId(AUCTION_ID).reason("x").build()))
                .doesNotThrowAnyException();
    }
}
