package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.projection.AuctionLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;

    @InjectMocks
    private BidNotificationHandler handler;

    private static BidPlacedEvent bid(int totalBids) {
        return BidPlacedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .bidAmount(new BigDecimal("100")).totalBids(totalBids).build();
    }

    @Test
    void firstBid_notifiesSellerInApp() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.bidPlaced(bid(1));

        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        assertThat(intent.getValue().userId()).isEqualTo(SELLER);
        assertThat(intent.getValue().type()).isEqualTo(NotificationType.FIRST_BID);
        assertThat(intent.getValue().dedupKey()).isEqualTo(DedupKeys.firstBid(AUCTION));
        assertThat(intent.getValue().message()).contains("$100.00");
        assertThat(intent.getValue().email()).isNull();
    }

    @Test
    void laterBids_doNothingYet() {
        handler.bidPlaced(bid(2));

        verifyNoInteractions(dispatcher, auctions);
    }

    @Test
    void firstBid_unknownSeller_isSkipped() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.empty());

        handler.bidPlaced(bid(1));

        verifyNoInteractions(dispatcher);
    }
}
