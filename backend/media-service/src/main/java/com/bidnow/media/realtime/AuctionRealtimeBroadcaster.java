package com.bidnow.media.realtime;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Pushes auction events to STOMP subscribers. Best-effort: a failed send is logged and dropped —
 * a stale real-time update is worse than a missed one (clients resync from REST on reload).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRealtimeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    static String auctionTopic(UUID auctionId) {
        return "/topic/auctions/" + auctionId;
    }

    public void bidPlaced(BidPlacedEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.BID_PLACED, new RealtimePayloads.BidPlaced(
                event.getBidId(),
                event.getBidderId(),
                event.getBidderName(),
                event.getBidAmount(),
                event.getBidTime() == null ? null : event.getBidTime().atOffset(ZoneOffset.UTC),
                event.getTotalBids(),
                event.getEndTime(),
                event.isAntiSnipingTriggered()));
    }

    public void auctionExtended(AuctionExtendedEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.AUCTION_EXTENDED, new RealtimePayloads.AuctionExtended(
                event.getPreviousEndTime(), event.getNewEndTime(), event.getExtensionCount()));
    }

    public void auctionEnded(AuctionEndedEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.AUCTION_ENDED, new RealtimePayloads.AuctionEnded(
                event.getWinnerId(), event.getWinningBidAmount(), event.getEndedAt()));
    }

    public void auctionCancelled(AuctionCancelledEvent event) {
        sendToAuction(event.getAuctionId(), AuctionRealtimeMessage.AUCTION_CANCELLED,
                new RealtimePayloads.AuctionCancelled(event.getReason()));
    }

    private void sendToAuction(UUID auctionId, String type, Object payload) {
        try {
            messagingTemplate.convertAndSend(auctionTopic(auctionId), new AuctionRealtimeMessage(type, auctionId, payload));
        } catch (RuntimeException ex) {
            log.warn("Real-time {} broadcast failed for auction {}: {}", type, auctionId, ex.getMessage());
        }
    }
}
