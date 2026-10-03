package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.domain.entity.Bid;
import com.bidnow.bidding.dto.ApplyBidCommand;
import com.bidnow.bidding.dto.ApplyBidResult;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.request.PlaceBidRequest;
import com.bidnow.bidding.dto.response.PlaceBidResponse;
import com.bidnow.bidding.exception.BidTooLowException;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.kafka.BidEventPublisher;
import com.bidnow.bidding.repository.BidRepository;
import com.bidnow.common.dto.event.BidPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PREVIOUS_WINNER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final OffsetDateTime END = OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC);

    @Mock
    private AuctionContextCacheService contextCache;
    @Mock
    private DepositGate depositGate;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuctionBidGateway auctionBidGateway;
    @Mock
    private UserSummaryCacheService userSummaries;
    @Mock
    private BidEventPublisher bidEventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BidService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(bidRepository.saveAndFlush(any(Bid.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userSummaries.displayName(BIDDER_ID)).thenReturn("Bob");
        service = new BidService(contextCache, new BidValidationService(), depositGate, bidRepository,
                auctionBidGateway, userSummaries, bidEventPublisher,
                new TransactionTemplate(transactionManager), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BidContext openContext() {
        return BidContext.builder()
                .auctionId(AUCTION_ID).title("Vintage Watch").sellerId(SELLER_ID).status("ACTIVE")
                .currentPrice(new BigDecimal("100.00")).bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00")).currentWinnerId(PREVIOUS_WINNER).totalBids(1).endTime(END)
                .build();
    }

    private static ApplyBidResult applied(boolean extended, OffsetDateTime endTime) {
        return ApplyBidResult.builder()
                .auctionId(AUCTION_ID).currentPrice(new BigDecimal("105.00")).currentWinnerId(BIDDER_ID)
                .previousWinnerId(PREVIOUS_WINNER).totalBids(2).endTime(endTime).extended(extended).build();
    }

    private static PlaceBidRequest request(String amount) {
        return new PlaceBidRequest(AUCTION_ID, new BigDecimal(amount));
    }

    @Test
    void happyPath_runsStepsInOrderAndPublishesAfterCommit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        InOrder order = inOrder(contextCache, depositGate, bidRepository, auctionBidGateway, transactionManager,
                bidEventPublisher);
        order.verify(contextCache).get(AUCTION_ID);
        order.verify(depositGate).ensureLocked(eq(BIDDER_ID), any(BidContext.class));
        order.verify(bidRepository).saveAndFlush(any(Bid.class));
        order.verify(auctionBidGateway).apply(eq(AUCTION_ID), any(ApplyBidCommand.class));
        order.verify(transactionManager).commit(any());
        order.verify(contextCache).put(any(BidContext.class));
        order.verify(bidEventPublisher).publishBidPlaced(any(BidPlacedEvent.class));

        assertThat(response.getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(response.getAmount()).isEqualByComparingTo("105.00");
        assertThat(response.getCurrentPrice()).isEqualByComparingTo("105.00");
        assertThat(response.getTotalBids()).isEqualTo(2);
        assertThat(response.getEndTime()).isEqualTo(END);
        assertThat(response.isExtended()).isFalse();
        assertThat(response.getPlacedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void bidIdIsSharedByStoredBidApplyCommandAndEvent() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<Bid> bid = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).saveAndFlush(bid.capture());
        ArgumentCaptor<ApplyBidCommand> command = ArgumentCaptor.forClass(ApplyBidCommand.class);
        verify(auctionBidGateway).apply(eq(AUCTION_ID), command.capture());
        ArgumentCaptor<BidPlacedEvent> event = ArgumentCaptor.forClass(BidPlacedEvent.class);
        verify(bidEventPublisher).publishBidPlaced(event.capture());

        assertThat(bid.getValue().getId()).isNotNull().isEqualTo(response.getBidId());
        assertThat(command.getValue().bidId()).isEqualTo(response.getBidId());
        assertThat(command.getValue().bidderId()).isEqualTo(BIDDER_ID);
        assertThat(command.getValue().amount()).isEqualByComparingTo("105.00");
        assertThat(event.getValue().getBidId()).isEqualTo(response.getBidId());
        assertThat(bid.getValue().getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(bid.getValue().getBidderId()).isEqualTo(BIDDER_ID);
        assertThat(bid.getValue().isAutoBid()).isFalse();
    }

    @Test
    void event_carriesContextTitleBidderNamePreviousWinnerAndNewState() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<BidPlacedEvent> captor = ArgumentCaptor.forClass(BidPlacedEvent.class);
        verify(bidEventPublisher).publishBidPlaced(captor.capture());
        BidPlacedEvent event = captor.getValue();
        assertThat(event.getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(event.getAuctionTitle()).isEqualTo("Vintage Watch");
        assertThat(event.getBidderId()).isEqualTo(BIDDER_ID);
        assertThat(event.getBidderName()).isEqualTo("Bob");
        assertThat(event.getBidAmount()).isEqualByComparingTo("105.00");
        assertThat(event.getBidTime()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(event.getPreviousHighestBidderId()).isEqualTo(PREVIOUS_WINNER);
        assertThat(event.isAntiSnipingTriggered()).isFalse();
        assertThat(event.getTotalBids()).isEqualTo(2);
        assertThat(event.getEndTime()).isEqualTo(END);
    }

    @Test
    void cache_isOverwrittenWithAuthoritativeStateAfterCommit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        OffsetDateTime extendedEnd = END.plusMinutes(5);
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(true, extendedEnd));

        service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<BidContext> captor = ArgumentCaptor.forClass(BidContext.class);
        verify(contextCache).put(captor.capture());
        BidContext updated = captor.getValue();
        assertThat(updated.getCurrentPrice()).isEqualByComparingTo("105.00");
        assertThat(updated.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(updated.getTotalBids()).isEqualTo(2);
        assertThat(updated.getEndTime()).isEqualTo(extendedEnd);
        assertThat(updated.getTitle()).isEqualTo("Vintage Watch");
        assertThat(updated.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void extension_isPersistedOnBidAndReportedInEventAndResponse() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenReturn(applied(true, END.plusMinutes(5)));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        ArgumentCaptor<Bid> bid = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).saveAndFlush(bid.capture());
        assertThat(bid.getValue().isAntiSnipingTriggered()).isTrue();
        ArgumentCaptor<BidPlacedEvent> event = ArgumentCaptor.forClass(BidPlacedEvent.class);
        verify(bidEventPublisher).publishBidPlaced(event.capture());
        assertThat(event.getValue().isAntiSnipingTriggered()).isTrue();
        assertThat(response.isExtended()).isTrue();
    }

    @Test
    void applyBidConflict_rollsBackEvictsAndPublishesNothing() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenThrow(new ConflictException("closed", BiddingErrorCodes.AUCTION_NOT_OPEN));

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ConflictException.class);

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(contextCache).evict(AUCTION_ID);
        verify(contextCache, never()).put(any());
        verify(bidEventPublisher, never()).publishBidPlaced(any());
    }

    @Test
    void applyBidTooLow_rollsBackAndEvicts() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenThrow(new BidTooLowException(new BigDecimal("110.00")));

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(BidTooLowException.class);

        verify(transactionManager).rollback(any());
        verify(contextCache).evict(AUCTION_ID);
    }

    @Test
    void auctionServiceUnavailable_rollsBackWithoutEviction() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class)))
                .thenThrow(new ServiceUnavailableException("down"));

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(transactionManager).rollback(any());
        verify(contextCache, never()).evict(any());
        verify(bidEventPublisher, never()).publishBidPlaced(any());
    }

    @Test
    void depositGateFailure_insertsNothingAndNeverAppliesBid() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        doThrow(new ServiceUnavailableException("wallet down")).when(depositGate).ensureLocked(eq(BIDDER_ID), any());

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(bidRepository, never()).saveAndFlush(any());
        verify(auctionBidGateway, never()).apply(any(), any());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void commitFailureAfterApply_propagatesAndPublishesNothing() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));
        doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(TransactionSystemException.class);

        verify(bidEventPublisher, never()).publishBidPlaced(any());
        verify(contextCache, never()).put(any());
    }

    @Test
    void preValidationTooLow_neitherRefreshesNorLocksDeposit() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("104.00")))
                .isInstanceOf(BidTooLowException.class);

        verify(contextCache, never()).refresh(any());
        verify(depositGate, never()).ensureLocked(any(), any());
    }

    @Test
    void preValidationOwnAuction_neverRefreshes() {
        when(contextCache.get(AUCTION_ID)).thenReturn(openContext());

        assertThatThrownBy(() -> service.placeBid(SELLER_ID, request("500.00")))
                .hasFieldOrPropertyWithValue("errorCode", BiddingErrorCodes.BID_OWN_AUCTION);

        verify(contextCache, never()).refresh(any());
    }

    @Test
    void staleClosedContext_isRefreshedOnceAndBidProceeds() {
        BidContext stale = openContext();
        stale.setStatus("SCHEDULED");
        when(contextCache.get(AUCTION_ID)).thenReturn(stale);
        when(contextCache.refresh(AUCTION_ID)).thenReturn(openContext());
        when(auctionBidGateway.apply(eq(AUCTION_ID), any(ApplyBidCommand.class))).thenReturn(applied(false, END));

        PlaceBidResponse response = service.placeBid(BIDDER_ID, request("105.00"));

        assertThat(response.getTotalBids()).isEqualTo(2);
        verify(contextCache).refresh(AUCTION_ID);
    }

    @Test
    void genuinelyClosedAuction_returnsConflictAfterOneRefresh() {
        BidContext closed = openContext();
        closed.setStatus("COMPLETED");
        when(contextCache.get(AUCTION_ID)).thenReturn(closed);
        when(contextCache.refresh(AUCTION_ID)).thenReturn(closed);

        assertThatThrownBy(() -> service.placeBid(BIDDER_ID, request("105.00")))
                .isInstanceOf(ConflictException.class);

        verify(contextCache).refresh(AUCTION_ID);
        verify(depositGate, never()).ensureLocked(any(), any());
    }
}
