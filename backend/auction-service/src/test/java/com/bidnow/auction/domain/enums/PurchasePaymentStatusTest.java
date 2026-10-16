package com.bidnow.auction.domain.enums;

import com.bidnow.auction.domain.entity.AuctionItem;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchasePaymentStatusTest {

    private static AuctionItem.AuctionItemBuilder item(AuctionStatus status) {
        return AuctionItem.builder().id(UUID.randomUUID()).status(status);
    }

    @Test
    void completedWithWinnerAndNoPaidAt_isPendingPayment() {
        AuctionItem item = item(AuctionStatus.COMPLETED).winnerId(UUID.randomUUID()).build();

        assertThat(PurchasePaymentStatus.of(item)).isEqualTo(PurchasePaymentStatus.PENDING_PAYMENT);
    }

    @Test
    void completedWithWinnerAndPaidAt_isPaid() {
        AuctionItem item = item(AuctionStatus.COMPLETED)
                .winnerId(UUID.randomUUID()).winnerPaidAt(OffsetDateTime.now()).build();

        assertThat(PurchasePaymentStatus.of(item)).isEqualTo(PurchasePaymentStatus.PAID);
    }

    @Test
    void failedWithWinner_isForfeited() {
        AuctionItem item = item(AuctionStatus.FAILED).winnerId(UUID.randomUUID()).build();

        assertThat(PurchasePaymentStatus.of(item)).isEqualTo(PurchasePaymentStatus.FORFEITED);
    }

    @Test
    void failedWithoutWinner_isNotAPurchase() {
        assertThat(PurchasePaymentStatus.of(item(AuctionStatus.FAILED).build())).isNull();
    }

    @Test
    void completedWithoutWinner_isNotAPurchase() {
        assertThat(PurchasePaymentStatus.of(item(AuctionStatus.COMPLETED).build())).isNull();
    }

    @Test
    void activeAuctionWithWinnerId_isNotAPurchase() {
        AuctionItem item = item(AuctionStatus.ACTIVE).winnerId(UUID.randomUUID()).build();

        assertThat(PurchasePaymentStatus.of(item)).isNull();
    }

    @Test
    void parse_acceptsKnownStatusesCaseInsensitivelyAndBlankAsAll() {
        assertThat(PurchasePaymentStatus.parse("paid")).isEqualTo(PurchasePaymentStatus.PAID);
        assertThat(PurchasePaymentStatus.parse("PENDING_PAYMENT")).isEqualTo(PurchasePaymentStatus.PENDING_PAYMENT);
        assertThat(PurchasePaymentStatus.parse(null)).isNull();
        assertThat(PurchasePaymentStatus.parse(" ")).isNull();
    }

    @Test
    void parse_rejectsUnknownStatus() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PurchasePaymentStatus.parse("BOGUS"))
                .isInstanceOf(com.bidnow.common.exception.BadRequestException.class);
    }

    @Test
    void nullItem_isNotAPurchase() {
        assertThat(PurchasePaymentStatus.of(null)).isNull();
    }
}
