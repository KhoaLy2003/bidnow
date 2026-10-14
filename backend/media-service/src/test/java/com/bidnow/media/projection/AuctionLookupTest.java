package com.bidnow.media.projection;

import com.bidnow.media.repository.AuctionProjectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionLookupTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock
    private AuctionProjectionRepository repository;

    @InjectMocks
    private AuctionLookup lookup;

    @Test
    void title_prefersTheEventTitle() {
        assertThat(lookup.title(AUCTION, "Vintage Watch")).isEqualTo("Vintage Watch");
        verifyNoInteractions(repository);
    }

    @Test
    void title_fallsBackToProjectionThenDefault() {
        when(repository.findAuction(AUCTION)).thenReturn(Optional.of(new AuctionRef(AUCTION, "Camera", SELLER, null)));
        assertThat(lookup.title(AUCTION, " ")).isEqualTo("Camera");

        when(repository.findAuction(AUCTION)).thenReturn(Optional.empty());
        assertThat(lookup.title(AUCTION, null)).isEqualTo(AuctionLookup.UNKNOWN_TITLE);
    }

    @Test
    void sellerAndParticipants_comeFromTheProjection() {
        UUID bidder = UUID.randomUUID();
        when(repository.findAuction(AUCTION)).thenReturn(Optional.of(new AuctionRef(AUCTION, "Camera", SELLER, null)));
        when(repository.findParticipantIds(AUCTION)).thenReturn(List.of(bidder));

        assertThat(lookup.sellerId(AUCTION)).contains(SELLER);
        assertThat(lookup.participants(AUCTION)).containsExactly(bidder);
    }
}
