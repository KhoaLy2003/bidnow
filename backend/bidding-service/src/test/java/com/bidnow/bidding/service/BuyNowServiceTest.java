package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.BuyNowCommand;
import com.bidnow.bidding.dto.BuyNowResult;
import com.bidnow.bidding.dto.request.BuyNowRequest;
import com.bidnow.bidding.dto.response.BuyNowResponse;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.InsufficientBalanceException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.common.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyNowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final OffsetDateTime END = OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC);
    private static final OffsetDateTime ENDED_AT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private AuctionContextCacheService contextCache;
    @Mock
    private DepositGate depositGate;
    @Mock
    private AuctionBidGateway auctionBidGateway;

    private BuyNowService service;

    @BeforeEach
    void setUp() {
        service = new BuyNowService(contextCache, new BidValidationService(), depositGate, auctionBidGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BidContext context(String currentPrice, String buyNowPrice) {
        return BidContext.builder()
                .auctionId(AUCTION_ID).title("Vintage Watch").sellerId(SELLER_ID).status("ACTIVE")
                .currentPrice(new BigDecimal(currentPrice)).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).totalBids(1).endTime(END)
                .buyNowPrice(buyNowPrice == null ? null : new BigDecimal(buyNowPrice))
                .build();
    }

    private static BuyNowResult applied() {
        return BuyNowResult.builder().auctionId(AUCTION_ID).price(new BigDecimal("200.00")).endedAt(ENDED_AT).build();
    }

    private static BuyNowRequest request() {
        return new BuyNowRequest(AUCTION_ID);
    }

    @Test
    void happyPath_runsStepsInOrderAndEvictsContext() {
        BidContext ctx = context("100.00", "200.00");
        when(contextCache.get(AUCTION_ID)).thenReturn(ctx);
        when(auctionBidGateway.buyNow(eq(AUCTION_ID), any(BuyNowCommand.class))).thenReturn(applied());

        BuyNowResponse response = service.buyNow(BUYER_ID, request());

        InOrder order = inOrder(contextCache, depositGate, auctionBidGateway);
        order.verify(contextCache).get(AUCTION_ID);
        order.verify(depositGate).ensureLocked(BUYER_ID, ctx);
        order.verify(auctionBidGateway).buyNow(AUCTION_ID, new BuyNowCommand(BUYER_ID));
        order.verify(contextCache).evict(AUCTION_ID);
        assertThat(response.getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(response.getPrice()).isEqualByComparingTo("200.00");
        assertThat(response.getEndedAt()).isEqualTo(ENDED_AT);
    }

    @Test
    void responseUsesTheRequestedAuctionId() {
        when(contextCache.get(AUCTION_ID)).thenReturn(context("100.00", "200.00"));
        when(auctionBidGateway.buyNow(eq(AUCTION_ID), any(BuyNowCommand.class)))
                .thenReturn(BuyNowResult.builder().price(new BigDecimal("200.00")).endedAt(ENDED_AT).build());

        BuyNowResponse response = service.buyNow(BUYER_ID, request());

        assertThat(response.getAuctionId()).isEqualTo(AUCTION_ID);
    }

    @Test
    void currentLeader_canBuyAndGateIsStillCalledIdempotently() {
        BidContext ctx = context("150.00", "200.00").toBuilder().currentWinnerId(BUYER_ID).build();
        when(contextCache.get(AUCTION_ID)).thenReturn(ctx);
        when(auctionBidGateway.buyNow(eq(AUCTION_ID), any(BuyNowCommand.class))).thenReturn(applied());

        service.buyNow(BUYER_ID, request());

        verify(depositGate).ensureLocked(BUYER_ID, ctx);
    }

    @Test
    void staleContextWithoutBuyNowPrice_isRefreshedOnceThenSucceeds() {
        BidContext stale = context("100.00", null);
        BidContext fresh = context("100.00", "200.00");
        when(contextCache.get(AUCTION_ID)).thenReturn(stale);
        when(contextCache.refresh(AUCTION_ID)).thenReturn(fresh);
        when(auctionBidGateway.buyNow(eq(AUCTION_ID), any(BuyNowCommand.class))).thenReturn(applied());

        service.buyNow(BUYER_ID, request());

        verify(contextCache, times(1)).refresh(AUCTION_ID);
        verify(depositGate).ensureLocked(BUYER_ID, fresh);
    }

    @Test
    void stillNoBuyNowPriceAfterRefresh_throwsNotAvailableWithoutLockingDeposit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(context("100.00", null));
        when(contextCache.refresh(AUCTION_ID)).thenReturn(context("100.00", null));

        assertThatThrownBy(() -> service.buyNow(BUYER_ID, request()))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BUY_NOW_NOT_AVAILABLE);
        verify(contextCache, never()).evict(any());
        verifyNoInteractions(depositGate, auctionBidGateway);
    }

    @Test
    void priceReachedEvenAfterRefresh_throwsNotAvailable() {
        when(contextCache.get(AUCTION_ID)).thenReturn(context("200.00", "200.00"));
        when(contextCache.refresh(AUCTION_ID)).thenReturn(context("200.00", "200.00"));

        assertThatThrownBy(() -> service.buyNow(BUYER_ID, request()))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BUY_NOW_NOT_AVAILABLE);
        verify(contextCache, times(1)).refresh(AUCTION_ID);
        verifyNoInteractions(depositGate, auctionBidGateway);
    }

    @Test
    void seller_isRejectedWithoutRefreshOrDeposit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(context("100.00", "200.00"));

        assertThatThrownBy(() -> service.buyNow(SELLER_ID, request()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.BID_OWN_AUCTION);
        verify(contextCache, never()).refresh(any());
        verify(contextCache, never()).evict(any());
        verifyNoInteractions(depositGate, auctionBidGateway);
    }

    @Test
    void depositFailure_propagatesAndAuctionIsNeverCalled() {
        BidContext ctx = context("100.00", "200.00");
        when(contextCache.get(AUCTION_ID)).thenReturn(ctx);
        doThrow(new InsufficientBalanceException(Map.of("availableBalance", "10.00", "required", "20.00")))
                .when(depositGate).ensureLocked(BUYER_ID, ctx);

        assertThatThrownBy(() -> service.buyNow(BUYER_ID, request()))
                .isInstanceOf(InsufficientBalanceException.class);
        verifyNoInteractions(auctionBidGateway);
    }

    @Test
    void auctionRejectsAfterDepositLock_evictsContextAndRethrows() {
        BidContext ctx = context("100.00", "200.00");
        when(contextCache.get(AUCTION_ID)).thenReturn(ctx);
        when(auctionBidGateway.buyNow(eq(AUCTION_ID), any(BuyNowCommand.class)))
                .thenThrow(new ConflictException("closed", BiddingErrorCodes.AUCTION_NOT_OPEN));

        assertThatThrownBy(() -> service.buyNow(BUYER_ID, request()))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(BiddingErrorCodes.AUCTION_NOT_OPEN);
        verify(depositGate).ensureLocked(BUYER_ID, ctx);
        verify(contextCache).evict(AUCTION_ID);
    }

    @Test
    void auctionUnavailable_evictsContextAndRethrows() {
        when(contextCache.get(AUCTION_ID)).thenReturn(context("100.00", "200.00"));
        when(auctionBidGateway.buyNow(eq(AUCTION_ID), any(BuyNowCommand.class)))
                .thenThrow(new ServiceUnavailableException("Auction service is unavailable"));

        assertThatThrownBy(() -> service.buyNow(BUYER_ID, request()))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(contextCache).evict(AUCTION_ID);
    }
}
