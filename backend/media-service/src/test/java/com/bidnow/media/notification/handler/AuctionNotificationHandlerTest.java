package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionEndingSoonEvent;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationDispatcher;
import com.bidnow.media.notification.NotificationIntent;
import com.bidnow.media.notification.NotificationLinks;
import com.bidnow.media.projection.AuctionLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID LOSER1 = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID LOSER2 = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;

    private AuctionNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuctionNotificationHandler(dispatcher, auctions, new NotificationLinks("http://localhost:3000"));
    }

    @SuppressWarnings("unchecked")
    private List<NotificationIntent> dispatchedBatch() {
        ArgumentCaptor<List<NotificationIntent>> batch = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(batch.capture());
        return batch.getValue();
    }

    private NotificationIntent dispatchedSingle() {
        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        return intent.getValue();
    }

    @Test
    void created_notifiesSellerInAppAndByEngagementEmail() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.auctionCreated(AuctionCreatedEvent.builder().auctionId(AUCTION).sellerId(SELLER).title("Vintage Watch").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.userId()).isEqualTo(SELLER);
        assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_CREATED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.auctionCreated(AUCTION));
        assertThat(intent.actionUrl()).isEqualTo("/auctions/" + AUCTION);
        assertThat(intent.email().templateBaseName()).isEqualTo("AUCTION_CREATED");
        assertThat(intent.email().transactional()).isFalse();
        assertThat(intent.email().variables())
                .containsEntry("auctionTitle", "Vintage Watch")
                .containsEntry("actionUrl", "http://localhost:3000/auctions/" + AUCTION);
    }

    @Test
    void ended_withWinner_winnerInAppOnly_losersInAppAndEmail() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(WINNER, LOSER1, LOSER2));

        handler.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).winnerId(WINNER).winningBidAmount(new BigDecimal("150")).build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).hasSize(3);
        NotificationIntent won = batch.get(0);
        assertThat(won.userId()).isEqualTo(WINNER);
        assertThat(won.type()).isEqualTo(NotificationType.AUCTION_WON);
        assertThat(won.dedupKey()).isEqualTo(DedupKeys.won(AUCTION));
        assertThat(won.message()).contains("$150.00");
        assertThat(won.email()).isNull();
        assertThat(batch.subList(1, 3)).allSatisfy(lost -> {
            assertThat(lost.type()).isEqualTo(NotificationType.AUCTION_LOST);
            assertThat(lost.dedupKey()).isEqualTo(DedupKeys.lost(AUCTION));
            assertThat(lost.email().templateBaseName()).isEqualTo("AUCTION_LOST");
            assertThat(lost.email().transactional()).isFalse();
            assertThat(lost.email().variables()).containsEntry("actionUrl", "http://localhost:3000/auctions");
        });
        assertThat(batch.subList(1, 3)).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2);
    }

    @Test
    void ended_withoutWinner_notifiesSellerUnsold() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");

        handler.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).singleElement().satisfies(unsold -> {
            assertThat(unsold.userId()).isEqualTo(SELLER);
            assertThat(unsold.type()).isEqualTo(NotificationType.AUCTION_UNSOLD);
            assertThat(unsold.dedupKey()).isEqualTo(DedupKeys.unsold(AUCTION));
            assertThat(unsold.email()).isNull();
        });
    }

    @Test
    void cancelled_notifiesEachParticipantWithEmailToWallet_andSellerInAppOnly() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1, LOSER2));

        handler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2, SELLER);
        assertThat(batch).allSatisfy(intent -> {
            assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_CANCELLED);
            assertThat(intent.dedupKey()).isEqualTo(DedupKeys.cancelled(AUCTION));
        });
        assertThat(batch.subList(0, 2)).allSatisfy(intent -> {
            assertThat(intent.actionUrl()).isEqualTo("/wallet");
            assertThat(intent.email().templateBaseName()).isEqualTo("AUCTION_CANCELLED");
            assertThat(intent.email().variables()).containsEntry("actionUrl", "http://localhost:3000/wallet");
        });
        NotificationIntent seller = batch.get(2);
        assertThat(seller.email()).isNull();
        assertThat(seller.message()).isEqualTo("Your auction \"Vintage Watch\" was cancelled.");
        assertThat(seller.actionUrl()).isEqualTo(NotificationLinks.SELLER_AUCTIONS_PATH);
    }

    @Test
    void cancelled_sellerFallsBackToProjection() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of());
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));

        handler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).build());

        assertThat(dispatchedBatch()).singleElement().satisfies(intent -> {
            assertThat(intent.userId()).isEqualTo(SELLER);
            assertThat(intent.email()).isNull();
        });
    }

    @Test
    void cancelled_sellerWhoIsAlsoParticipant_isNotifiedOnce() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(SELLER, LOSER1));

        handler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).sellerId(SELLER).build());

        assertThat(dispatchedBatch()).extracting(NotificationIntent::userId).containsExactly(SELLER, LOSER1);
    }

    @Test
    void cancelled_withoutParticipantsAndSeller_dispatchesNothing() {
        when(auctions.title(AUCTION, null)).thenReturn(AuctionLookup.UNKNOWN_TITLE);
        when(auctions.participants(AUCTION)).thenReturn(List.of());
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.empty());

        handler.auctionCancelled(AuctionCancelledEvent.builder().auctionId(AUCTION).build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void ended_withWinnerButNoWinningAmount_omitsBidSentence() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(WINNER));

        handler.auctionEnded(AuctionEndedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).winnerId(WINNER).build());

        assertThat(dispatchedBatch().get(0).message())
                .isEqualTo("You won \"Vintage Watch\". Check your email for payment details.");
    }

    @Test
    void extended_notifiesParticipantsAndSellerInAppOnce() {
        Instant newEnd = Instant.parse("2026-10-01T12:05:00Z");
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1, LOSER2));
        when(auctions.sellerId(AUCTION)).thenReturn(Optional.of(SELLER));

        handler.auctionExtended(AuctionExtendedEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .newEndTime(newEnd).build());

        List<NotificationIntent> batch = dispatchedBatch();
        assertThat(batch).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2, SELLER);
        assertThat(batch).allSatisfy(intent -> {
            assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_EXTENDED);
            assertThat(intent.dedupKey()).isEqualTo(DedupKeys.extended(AUCTION, newEnd));
            assertThat(intent.message()).contains("2026-10-01 12:05 UTC");
            assertThat(intent.email()).isNull();
        });
    }

    @Test
    void extended_withoutNewEndTime_dispatchesNothing() {
        handler.auctionExtended(AuctionExtendedEvent.builder().auctionId(AUCTION).build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void created_withoutSeller_dispatchesNothing() {
        handler.auctionCreated(AuctionCreatedEvent.builder().auctionId(AUCTION).title("Vintage Watch").build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void endingSoon_notifiesEveryBidderInAppButNotTheSeller() {
        when(auctions.title(AUCTION, "Vintage Watch")).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1, LOSER2));

        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).auctionTitle("Vintage Watch")
                .sellerId(SELLER).endTime(Instant.parse("2026-10-02T12:15:00Z")).thresholdMinutes(15).build());

        List<NotificationIntent> intents = dispatchedBatch();
        assertThat(intents).extracting(NotificationIntent::userId).containsExactly(LOSER1, LOSER2);
        NotificationIntent intent = intents.get(0);
        assertThat(intent.type()).isEqualTo(NotificationType.AUCTION_ENDING_SOON);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.endingSoon(AUCTION, 15));
        assertThat(intent.title()).isEqualTo("Auction ending soon");
        assertThat(intent.message()).isEqualTo("\"Vintage Watch\" ends in 15 minutes.");
        assertThat(intent.actionUrl()).isEqualTo("/auctions/" + AUCTION);
        assertThat(intent.email()).isNull();
    }

    @Test
    void endingSoon_humanisesWholeHours() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of(LOSER1));

        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).thresholdMinutes(60).build());

        assertThat(dispatchedBatch().get(0).message()).isEqualTo("\"Vintage Watch\" ends in 1 hour.");
    }

    @Test
    void thresholdLabel_coversSingularAndPlural() {
        assertThat(AuctionNotificationHandler.thresholdLabel(1)).isEqualTo("1 minute");
        assertThat(AuctionNotificationHandler.thresholdLabel(15)).isEqualTo("15 minutes");
        assertThat(AuctionNotificationHandler.thresholdLabel(60)).isEqualTo("1 hour");
        assertThat(AuctionNotificationHandler.thresholdLabel(120)).isEqualTo("2 hours");
        assertThat(AuctionNotificationHandler.thresholdLabel(90)).isEqualTo("90 minutes");
    }

    @Test
    void endingSoon_withoutBidders_dispatchesNothing() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");
        when(auctions.participants(AUCTION)).thenReturn(List.of());

        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).thresholdMinutes(15).build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void endingSoon_withoutThreshold_isSkipped() {
        handler.endingSoon(AuctionEndingSoonEvent.builder().auctionId(AUCTION).build());

        verifyNoInteractions(dispatcher, auctions);
    }
}
