package com.bidnow.media.projection;

import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.repository.AuctionProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionProjectionServiceTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID BIDDER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private AuctionProjectionRepository repository;

    private AuctionProjectionService service;

    @BeforeEach
    void setUp() {
        service = new AuctionProjectionService(repository,
                Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void onAuctionCreated_upsertsFullAuction() {
        service.onAuctionCreated(AuctionCreatedEvent.builder()
                .auctionId(AUCTION).sellerId(SELLER).title("Vintage Watch")
                .endTime(Instant.parse("2026-10-02T12:00:00Z")).build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", SELLER, OffsetDateTime.parse("2026-10-02T12:00:00Z"));
    }

    @Test
    void onBidPlaced_upsertsAuctionAndParticipant() {
        LocalDateTime bidTime = LocalDateTime.of(2026, 10, 1, 11, 0);
        OffsetDateTime endTime = OffsetDateTime.parse("2026-10-02T12:00:00Z");

        service.onBidPlaced(BidPlacedEvent.builder()
                .auctionId(AUCTION).auctionTitle("Vintage Watch").bidderId(BIDDER)
                .bidAmount(new BigDecimal("105.00")).bidTime(bidTime).endTime(endTime).build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", null, endTime);
        verify(repository).upsertParticipant(AUCTION, BIDDER, new BigDecimal("105.00"), bidTime);
    }

    @Test
    void onBidPlaced_missingBidTime_usesNow() {
        service.onBidPlaced(BidPlacedEvent.builder()
                .auctionId(AUCTION).bidderId(BIDDER).bidAmount(new BigDecimal("105.00")).build());

        verify(repository).upsertParticipant(AUCTION, BIDDER, new BigDecimal("105.00"), LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    @Test
    void onBidPlaced_withoutBidder_skipsParticipant() {
        service.onBidPlaced(BidPlacedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch").build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", null, null);
        verify(repository, never()).upsertParticipant(any(), any(), any(), any());
    }

    @Test
    void onAuctionExtended_updatesEndTime() {
        service.onAuctionExtended(AuctionExtendedEvent.builder()
                .auctionId(AUCTION).auctionTitle("Vintage Watch")
                .newEndTime(Instant.parse("2026-10-02T12:05:00Z")).build());

        verify(repository).upsertAuction(AUCTION, "Vintage Watch", null, OffsetDateTime.parse("2026-10-02T12:05:00Z"));
    }
}
