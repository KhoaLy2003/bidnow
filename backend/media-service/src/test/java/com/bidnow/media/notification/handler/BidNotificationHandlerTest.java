package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.BidPlacedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.batch.BidAlert;
import com.bidnow.media.notification.batch.BidAlertKind;
import com.bidnow.media.notification.batch.BidAlerts;
import com.bidnow.media.notification.batch.BidBatchOutcome;
import com.bidnow.media.notification.batch.BidBatcher;
import com.bidnow.media.projection.AuctionLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID BID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;
    @Mock
    private BidBatcher batcher;

    @InjectMocks
    private BidNotificationHandler handler;

    private static BidPlacedEvent bid(Integer totalBids, UUID previousLeader) {
        return BidPlacedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch").bidId(BID)
                .bidderId(BOB).bidAmount(new BigDecimal("120")).previousHighestBidderId(previousLeader)
                .totalBids(totalBids).build();
    }

    private void stubAuction() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));
    }

    private List<BidAlert> recordedAlerts(int expected) {
        ArgumentCaptor<BidAlert> alerts = ArgumentCaptor.forClass(BidAlert.class);
        verify(batcher, times(expected)).record(alerts.capture());
        return alerts.getAllValues();
    }

    @Test
    void firstBid_notifiesSellerInApp_withoutBatching() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.bidPlaced(bid(1, null));

        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        assertThat(intent.getValue().userId()).isEqualTo(SELLER);
        assertThat(intent.getValue().type()).isEqualTo(NotificationType.FIRST_BID);
        assertThat(intent.getValue().dedupKey()).isEqualTo(DedupKeys.firstBid(AUCTION));
        assertThat(intent.getValue().message()).contains("$120.00");
        assertThat(intent.getValue().email()).isNull();
        verifyNoInteractions(batcher);
    }

    @Test
    void firstBid_unknownSeller_isSkipped() {
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.empty());

        handler.bidPlaced(bid(1, null));

        verifyNoInteractions(dispatcher, batcher);
    }

    @Test
    void outbid_openingAWindow_dispatchesBothAlertsImmediately() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.IMMEDIATE);

        handler.bidPlaced(bid(3, ALICE));

        BidAlert outbid = new BidAlert(BidAlertKind.OUTBID, ALICE, AUCTION, BID, "Vintage Watch", new BigDecimal("120"));
        BidAlert newBid = new BidAlert(BidAlertKind.NEW_BID, SELLER, AUCTION, BID, "Vintage Watch", new BigDecimal("120"));
        assertThat(recordedAlerts(2)).containsExactly(outbid, newBid);
        ArgumentCaptor<NotificationIntent> intents = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher, times(2)).dispatch(intents.capture());
        assertThat(intents.getAllValues()).containsExactly(BidAlerts.immediate(outbid), BidAlerts.immediate(newBid));
    }

    @Test
    void batchedOrDuplicateAlerts_areNotDispatched() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED, BidBatchOutcome.DUPLICATE);

        handler.bidPlaced(bid(3, ALICE));

        verify(batcher, times(2)).record(any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void leaderRaisingOwnBid_onlyAlertsTheSeller() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(3, BOB));

        assertThat(recordedAlerts(1)).extracting(BidAlert::kind).containsExactly(BidAlertKind.NEW_BID);
    }

    @Test
    void noPreviousLeader_onlyAlertsTheSeller() {
        stubAuction();
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(2, null));

        assertThat(recordedAlerts(1)).extracting(BidAlert::userId).containsExactly(SELLER);
    }

    @Test
    void unknownSeller_stillAlertsTheOutbidUser() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.empty());
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(3, ALICE));

        assertThat(recordedAlerts(1)).extracting(BidAlert::userId).containsExactly(ALICE);
    }

    @Test
    void unknownTotalBids_skipsTheSellerAlert() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(batcher.record(any())).thenReturn(BidBatchOutcome.BATCHED);

        handler.bidPlaced(bid(null, ALICE));

        assertThat(recordedAlerts(1)).extracting(BidAlert::kind).containsExactly(BidAlertKind.OUTBID);
    }

    @Test
    void missingBidId_skipsBatchedAlerts() {
        BidPlacedEvent event = bid(3, ALICE);
        event.setBidId(null);

        handler.bidPlaced(event);

        verifyNoInteractions(batcher, dispatcher);
    }
}
