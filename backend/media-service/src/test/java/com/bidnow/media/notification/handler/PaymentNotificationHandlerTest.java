package com.bidnow.media.notification.handler;

import com.bidnow.common.dto.event.DepositRefundedEvent;
import com.bidnow.common.dto.event.PaymentEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationHandlerTest {

    private static final UUID AUCTION = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Instant DEADLINE = Instant.parse("2026-10-03T10:00:00Z");

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private AuctionLookup auctions;

    private PaymentNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentNotificationHandler(dispatcher, auctions, new NotificationLinks("http://localhost:3000"));
    }

    private NotificationIntent dispatchedSingle() {
        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(dispatcher).dispatch(intent.capture());
        return intent.getValue();
    }

    private static PaymentEvent.PaymentEventBuilder payment(String type) {
        return PaymentEvent.builder().auctionId(AUCTION).userId(WINNER).sellerId(SELLER).paymentType(type)
                .amount(new BigDecimal("1500")).depositAmount(new BigDecimal("150")).remaining(new BigDecimal("1350"))
                .deadline(DEADLINE);
    }

    @Test
    void required_sendsTheWinnerEmailAsTransactional() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REQUIRED").insufficientFunds(false).build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.userId()).isEqualTo(WINNER);
        assertThat(intent.type()).isEqualTo(NotificationType.PAYMENT_REQUIRED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.payment("REQUIRED", AUCTION));
        assertThat(intent.actionUrl()).isEqualTo("/wallet");
        assertThat(intent.message()).contains("$1,350.00").contains("2026-10-03 10:00 UTC").doesNotContain("top up");
        assertThat(intent.email().templateBaseName()).isEqualTo("AUCTION_WON");
        assertThat(intent.email().transactional()).isTrue();
        assertThat(intent.email().variables())
                .containsEntry("auctionTitle", "Vintage Watch")
                .containsEntry("bidAmount", "$1,500.00")
                .containsEntry("paymentDeadline", "2026-10-03 10:00 UTC")
                .containsEntry("actionUrl", "http://localhost:3000/wallet");
    }

    @Test
    void required_withInsufficientFunds_asksToTopUp() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REQUIRED").insufficientFunds(true).build());

        assertThat(dispatchedSingle().message()).contains("top up");
    }

    @Test
    @SuppressWarnings("unchecked")
    void completed_emailsWinnerAndSeller() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("COMPLETED").build());

        ArgumentCaptor<List<NotificationIntent>> batch = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(batch.capture());
        assertThat(batch.getValue()).hasSize(2);
        NotificationIntent winner = batch.getValue().get(0);
        assertThat(winner.userId()).isEqualTo(WINNER);
        assertThat(winner.type()).isEqualTo(NotificationType.PAYMENT_RECEIVED);
        assertThat(winner.email().templateBaseName()).isEqualTo("PAYMENT_SUCCESSFUL");
        assertThat(winner.email().transactional()).isTrue();
        assertThat(winner.email().variables()).containsEntry("bidAmount", "$1,500.00");
        NotificationIntent seller = batch.getValue().get(1);
        assertThat(seller.userId()).isEqualTo(SELLER);
        assertThat(seller.type()).isEqualTo(NotificationType.PAYMENT_RECEIVED);
        assertThat(seller.actionUrl()).isEqualTo("/seller/auctions");
        assertThat(seller.email().templateBaseName()).isEqualTo("SALE_PAYMENT_RECEIVED");
        assertThat(seller.email().transactional()).isTrue();
        assertThat(seller.email().variables())
                .containsEntry("bidAmount", "$1,500.00")
                .containsEntry("actionUrl", "http://localhost:3000/seller/auctions");
        assertThat(batch.getValue()).extracting(NotificationIntent::dedupKey)
                .containsOnly(DedupKeys.payment("COMPLETED", AUCTION));
    }

    @Test
    void failed_sendsTransactionalForfeitEmail() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("FAILED").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.type()).isEqualTo(NotificationType.PAYMENT_FAILED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.payment("FAILED", AUCTION));
        assertThat(intent.email().templateBaseName()).isEqualTo("PAYMENT_FAILED");
        assertThat(intent.email().transactional()).isTrue();
        assertThat(intent.email().variables()).containsEntry("depositAmount", "$150.00");
    }

    @Test
    void unknownPaymentType_dispatchesNothing() {
        handler.paymentEvent(payment("REMINDER_24H").build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void refund_afterLoss_emailsWithProjectedTitle() {
        when(auctions.title(AUCTION, null)).thenReturn("Camera");

        handler.depositRefunded(DepositRefundedEvent.builder().userId(WINNER).auctionId(AUCTION)
                .amount(new BigDecimal("150")).reason("AUCTION_LOST").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.type()).isEqualTo(NotificationType.DEPOSIT_REFUNDED);
        assertThat(intent.dedupKey()).isEqualTo(DedupKeys.refund(AUCTION));
        assertThat(intent.message()).contains("$150.00").contains("Camera");
        assertThat(intent.email().templateBaseName()).isEqualTo("DEPOSIT_REFUNDED");
        assertThat(intent.email().transactional()).isFalse();
        assertThat(intent.email().variables()).containsEntry("auctionTitle", "Camera");
    }

    @Test
    void refund_afterCancellation_isInAppOnly() {
        when(auctions.title(AUCTION, null)).thenReturn(AuctionLookup.UNKNOWN_TITLE);

        handler.depositRefunded(DepositRefundedEvent.builder().userId(WINNER).auctionId(AUCTION)
                .amount(new BigDecimal("150")).reason("AUCTION_CANCELLED").build());

        NotificationIntent intent = dispatchedSingle();
        assertThat(intent.message()).contains("your auction");
        assertThat(intent.email()).isNull();
    }

    @Test
    void paymentWithoutWinner_dispatchesNothing() {
        handler.paymentEvent(PaymentEvent.builder().auctionId(AUCTION).paymentType("REQUIRED").build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void refund_withoutUserOrAuction_dispatchesNothing() {
        handler.depositRefunded(DepositRefundedEvent.builder().auctionId(AUCTION).amount(new BigDecimal("1")).build());
        handler.depositRefunded(DepositRefundedEvent.builder().userId(WINNER).amount(new BigDecimal("1")).build());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void required_withZeroRemaining_asksToConfirmPayment() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REQUIRED").remaining(BigDecimal.ZERO).insufficientFunds(false).build());

        assertThat(dispatchedSingle().message()).isEqualTo(
                "You won \"Vintage Watch\". Please confirm your payment of $1,500.00 by 2026-10-03 10:00 UTC.");
    }

    @Test
    void required_withNullRemainingAndNoDeadline_omitsBothAndEndsWithPeriod() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REQUIRED").remaining(null).deadline(null).insufficientFunds(true).build());

        assertThat(dispatchedSingle().message())
                .isEqualTo("You won \"Vintage Watch\". Please confirm your payment of $1,500.00.");
    }

    @Test
    void required_withRemainingAndNoDeadline_omitsDeadline() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("REQUIRED").deadline(null).insufficientFunds(false).build());

        assertThat(dispatchedSingle().message())
                .isEqualTo("You won \"Vintage Watch\". Please complete your payment of $1,350.00.");
    }

    @Test
    void failed_withoutDeposit_omitsForfeitSentence() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("FAILED").depositAmount(null).build());

        assertThat(dispatchedSingle().message())
                .isEqualTo("The payment deadline for \"Vintage Watch\" has passed, so your win was cancelled.");
    }

    @Test
    void failed_withZeroDeposit_omitsForfeitSentence() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("FAILED").depositAmount(BigDecimal.ZERO).build());

        assertThat(dispatchedSingle().message()).doesNotContain("forfeited");
    }

    @Test
    void failed_withDeposit_mentionsForfeit() {
        when(auctions.title(AUCTION, null)).thenReturn("Vintage Watch");

        handler.paymentEvent(payment("FAILED").build());

        assertThat(dispatchedSingle().message()).contains("Your deposit of $150.00 was forfeited.");
    }
}
