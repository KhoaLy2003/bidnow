package com.bidnow.media.projection;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.repository.AuctionProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Maintains media-service's own view of auctions and who bid on them, so notification handlers can find
 * titles, sellers, losers and active bidders without calling other services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionProjectionService {

    private final AuctionProjectionRepository repository;
    private final Clock clock;

    public void onAuctionCreated(AuctionCreatedEvent event) {
        repository.upsertAuction(event.getAuctionId(), event.getTitle(), event.getSellerId(), toUtc(event.getEndTime()));
    }

    @Transactional
    public void onBidPlaced(BidPlacedEvent event) {
        repository.upsertAuction(event.getAuctionId(), event.getAuctionTitle(), null, event.getEndTime());
        if (event.getBidderId() == null || event.getBidAmount() == null) {
            log.warn("BidPlacedEvent for auction {} has no bidder/amount - participant not recorded", event.getAuctionId());
            return;
        }
        LocalDateTime bidAt = event.getBidTime() != null ? event.getBidTime() : LocalDateTime.now(clock);
        repository.upsertParticipant(event.getAuctionId(), event.getBidderId(), event.getBidAmount(), bidAt);
    }

    public void onAuctionExtended(AuctionExtendedEvent event) {
        repository.upsertAuction(event.getAuctionId(), event.getAuctionTitle(), null, toUtc(event.getNewEndTime()));
    }

    private static OffsetDateTime toUtc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
