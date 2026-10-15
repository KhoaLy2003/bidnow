package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BidValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final BidValidationService service = new BidValidationService();

    private BidContext context(int totalBids, String currentPrice) {
        return BidContext.builder()
                .auctionId(UUID.randomUUID())
                .title("Vintage Watch")
                .sellerId(SELLER_ID)
                .status("ACTIVE")
                .currentPrice(new BigDecimal(currentPrice))
                .bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00"))
                .totalBids(totalBids)
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }

    @Test
    void validFirstBidAtCurrentPrice_passes() {
        assertThatCode(() -> service.preValidate(context(0, "100.00"), BIDDER_ID, new BigDecimal("100.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void firstBidBelowCurrentPrice_throwsBidTooLow() {
        assertThatThrownBy(() -> service.preValidate(context(0, "100.00"), BIDDER_ID, new BigDecimal("99.99"), NOW))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("100.00"));
    }

    @Test
    void subsequentBidBelowIncrement_throwsBidTooLow() {
        assertThatThrownBy(() -> service.preValidate(context(1, "100.00"), BIDDER_ID, new BigDecimal("104.00"), NOW))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("105.00"));
    }

    @Test
    void subsequentBidAtExactMinimum_passes() {
        assertThatCode(() -> service.preValidate(context(1, "100.00"), BIDDER_ID, new BigDecimal("105.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void sellerBidding_throwsForbidden() {
        assertThatThrownBy(() -> service.preValidate(context(0, "100.00"), SELLER_ID, new BigDecimal("500.00"), NOW))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BID_OWN_AUCTION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SCHEDULED", "COMPLETED", "FAILED", "CANCELLED", "REJECTED"})
    void notActive_throwsConflict(String status) {
        BidContext ctx = context(0, "100.00");
        ctx.setStatus(status);

        assertThatThrownBy(() -> service.preValidate(ctx, BIDDER_ID, new BigDecimal("100.00"), NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void exactlyAtEndTime_throwsConflict() {
        BidContext ctx = context(0, "100.00");
        ctx.setEndTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.preValidate(ctx, BIDDER_ID, new BigDecimal("100.00"), NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void oneMillisecondBeforeEndTime_passes() {
        BidContext ctx = context(0, "100.00");
        ctx.setEndTime(OffsetDateTime.ofInstant(NOW.plusMillis(1), ZoneOffset.UTC));

        assertThatCode(() -> service.preValidate(ctx, BIDDER_ID, new BigDecimal("100.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void closedAuctionIsReportedBeforeSellerAndAmountChecks() {
        BidContext ctx = context(1, "100.00");
        ctx.setStatus("COMPLETED");

        assertThatThrownBy(() -> service.preValidate(ctx, SELLER_ID, new BigDecimal("1.00"), NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sellerIsReportedBeforeAmountCheck() {
        assertThatThrownBy(() -> service.preValidate(context(1, "100.00"), SELLER_ID, new BigDecimal("1.00"), NOW))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void minimumBid_usesCurrentPriceForFirstBidAndAddsIncrementAfter() {
        assertThat(BidValidationService.minimumBid(context(0, "100.00"))).isEqualByComparingTo("100.00");
        assertThat(BidValidationService.minimumBid(context(3, "100.00"))).isEqualByComparingTo("105.00");
    }

    // ---------------------------------------------------------------------
    // preValidateBuyNow
    // ---------------------------------------------------------------------

    private BidContext buyNowContext(String currentPrice, String buyNowPrice) {
        return context(1, currentPrice).toBuilder()
                .buyNowPrice(buyNowPrice == null ? null : new BigDecimal(buyNowPrice)).build();
    }

    @Test
    void buyNow_priceBelowBuyNowPrice_passes() {
        assertThatCode(() -> service.preValidateBuyNow(buyNowContext("100.00", "200.00"), BIDDER_ID, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void buyNow_oneCentBelowBuyNowPrice_passes() {
        assertThatCode(() -> service.preValidateBuyNow(buyNowContext("199.99", "200.00"), BIDDER_ID, NOW))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"200.00", "200.01", "500.00"})
    void buyNow_priceReachedOrPassed_throwsBuyNowNotAvailable(String currentPrice) {
        assertThatThrownBy(() -> service.preValidateBuyNow(buyNowContext(currentPrice, "200.00"), BIDDER_ID, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BUY_NOW_NOT_AVAILABLE);
    }

    @Test
    void buyNow_noBuyNowPrice_throwsBuyNowNotAvailable() {
        assertThatThrownBy(() -> service.preValidateBuyNow(buyNowContext("100.00", null), BIDDER_ID, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BUY_NOW_NOT_AVAILABLE);
    }

    @Test
    void buyNow_inactiveAuction_throwsNotOpenBeforeAnythingElse() {
        BidContext ctx = buyNowContext("100.00", null).toBuilder().status("COMPLETED").build();

        assertThatThrownBy(() -> service.preValidateBuyNow(ctx, SELLER_ID, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void buyNow_atOrAfterEndTime_throwsNotOpen() {
        BidContext ctx = buyNowContext("100.00", "200.00");

        assertThatThrownBy(() -> service.preValidateBuyNow(ctx, BIDDER_ID, ctx.getEndTime().toInstant()))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void buyNow_seller_throwsForbidden() {
        assertThatThrownBy(() -> service.preValidateBuyNow(buyNowContext("100.00", "200.00"), SELLER_ID, NOW))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BID_OWN_AUCTION);
    }
}
