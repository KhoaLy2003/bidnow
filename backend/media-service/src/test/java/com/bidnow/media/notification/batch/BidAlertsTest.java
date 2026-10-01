package com.bidnow.media.notification.batch;

import com.bidnow.media.domain.enums.NotificationType;
import com.bidnow.media.notification.DedupKeys;
import com.bidnow.media.notification.NotificationIntent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BidAlertsTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final Instant WINDOW = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void immediateOutbid_isKeyedPerBid() {
        NotificationIntent intent = BidAlerts.immediate(new BidAlert(BidAlertKind.OUTBID, ALICE, AUCTION, BID,
                "Vintage Watch", new BigDecimal("120")));

        assertThat(intent.userId()).isEqualTo(ALICE);
        assertThat(intent.type()).isEqualTo(NotificationType.BID_OUTBID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.bidAlert(NotificationType.BID_OUTBID, BID));
        assertThat(intent.auctionId()).isEqualTo(AUCTION);
        assertThat(intent.title()).isEqualTo("You've been outbid");
        assertThat(intent.message()).isEqualTo("Someone outbid you on \"Vintage Watch\". Current price: $120.00.");
        assertThat(intent.actionUrl()).isEqualTo("/auctions/" + AUCTION);
        assertThat(intent.metadata()).containsEntry("currentPrice", "120");
        assertThat(intent.email()).isNull();
    }

    @Test
    void immediateNewBid_addressesTheSeller() {
        NotificationIntent intent = BidAlerts.immediate(new BidAlert(BidAlertKind.NEW_BID, ALICE, AUCTION, BID,
                "Vintage Watch", new BigDecimal("120")));

        assertThat(intent.type()).isEqualTo(NotificationType.NEW_BID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.bidAlert(NotificationType.NEW_BID, BID));
        assertThat(intent.title()).isEqualTo("New bid on your auction");
        assertThat(intent.message()).isEqualTo("\"Vintage Watch\" received a new bid. Current price: $120.00.");
    }

    @Test
    void batchedOutbid_summarisesTheWindow() {
        NotificationIntent intent = BidAlerts.batched(new DueBatch(BidAlertKind.OUTBID, ALICE, AUCTION, WINDOW, 2,
                "Vintage Watch", new BigDecimal("130")));

        assertThat(intent.type()).isEqualTo(NotificationType.BID_OUTBID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.batch(NotificationType.BID_OUTBID, AUCTION, WINDOW));
        assertThat(intent.message())
                .isEqualTo("You've been outbid 2 more times on \"Vintage Watch\". Current price: $130.00.");
        assertThat(intent.metadata()).containsEntry("currentPrice", "130").containsEntry("count", 2);
        assertThat(intent.email()).isNull();
    }

    @Test
    void batchedCopy_usesSingularForOne() {
        assertThat(BidAlerts.batched(new DueBatch(BidAlertKind.OUTBID, ALICE, AUCTION, WINDOW, 1, "Vase",
                new BigDecimal("5"))).message()).contains("outbid 1 more time on");
        assertThat(BidAlerts.batched(new DueBatch(BidAlertKind.NEW_BID, ALICE, AUCTION, WINDOW, 1, "Vase",
                new BigDecimal("5"))).message()).isEqualTo("\"Vase\" received 1 more bid. Current price: $5.00.");
    }

    @Test
    void batchedNewBid_countsBids() {
        NotificationIntent intent = BidAlerts.batched(new DueBatch(BidAlertKind.NEW_BID, ALICE, AUCTION, WINDOW, 3,
                "Vase", new BigDecimal("150")));

        assertThat(intent.type()).isEqualTo(NotificationType.NEW_BID);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.batch(NotificationType.NEW_BID, AUCTION, WINDOW));
        assertThat(intent.message()).isEqualTo("\"Vase\" received 3 more bids. Current price: $150.00.");
    }

    @Test
    void missingTitleAndAmount_degradeGracefully() {
        NotificationIntent intent = BidAlerts.immediate(new BidAlert(BidAlertKind.OUTBID, ALICE, AUCTION, BID,
                null, null));

        assertThat(intent.message()).isEqualTo("Someone outbid you on this auction.");
        assertThat(intent.metadata()).doesNotContainKey("currentPrice");
    }

    @Test
    void newBidWithMissingTitle_saysYourAuction() {
        NotificationIntent intent = BidAlerts.immediate(new BidAlert(BidAlertKind.NEW_BID, ALICE, AUCTION, BID,
                null, null));

        assertThat(intent.message()).isEqualTo("your auction received a new bid.");
    }
}
