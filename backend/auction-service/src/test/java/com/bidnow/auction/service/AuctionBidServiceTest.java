package com.bidnow.auction.service;

import com.bidnow.auction.config.AntiSnipeProperties;
import com.bidnow.auction.constant.AuctionErrorCodes;
import com.bidnow.auction.domain.entity.AuctionExtension;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.ApplyBidRequest;
import com.bidnow.auction.dto.response.ApplyBidResponse;
import com.bidnow.auction.dto.response.BidContextResponse;
import com.bidnow.auction.exception.BidTooLowException;
import com.bidnow.auction.exception.ConflictException;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.repository.AuctionExtensionRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.common.dto.event.AuctionExtendedEvent;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BIDDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PREVIOUS_WINNER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private AuctionExtensionRepository auctionExtensionRepository;
    @Mock
    private AuctionKafkaProducer kafkaProducer;

    private AuctionBidService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        service = new AuctionBidService(auctionItemRepository, auctionExtensionRepository, kafkaProducer,
                new AntiSnipeProperties(120, 300), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private AuctionItem endingIn(long secondsLeft) {
        AuctionItem auction = activeAuction(1, "100.00");
        auction.setEndTime(OffsetDateTime.ofInstant(NOW.plusSeconds(secondsLeft), ZoneOffset.UTC));
        return auction;
    }

    private AuctionItem activeAuction(int totalBids, String currentPrice) {
        return AuctionItem.builder()
                .id(UUID.randomUUID())
                .sellerId(SELLER_ID)
                .title("Vintage Watch")
                .status(AuctionStatus.ACTIVE)
                .startingPrice(new BigDecimal("100.00"))
                .bidIncrement(new BigDecimal("5.00"))
                .depositAmount(new BigDecimal("20.00"))
                .currentPrice(new BigDecimal(currentPrice))
                .currentWinnerId(totalBids == 0 ? null : PREVIOUS_WINNER_ID)
                .totalBids(totalBids)
                .extensionCount(0)
                .endTime(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }

    private ApplyBidRequest bid(String amount) {
        return new ApplyBidRequest(UUID.randomUUID(), BIDDER_ID, new BigDecimal(amount));
    }

    // ---------------------------------------------------------------------
    // getBidContext
    // ---------------------------------------------------------------------

    @Test
    void getBidContext_mapsAuctionFields() {
        AuctionItem auction = activeAuction(2, "110.00");
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auction.getId())).thenReturn(Optional.of(auction));

        BidContextResponse ctx = service.getBidContext(auction.getId());

        assertThat(ctx.getAuctionId()).isEqualTo(auction.getId());
        assertThat(ctx.getTitle()).isEqualTo("Vintage Watch");
        assertThat(ctx.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(ctx.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(ctx.getCurrentPrice()).isEqualByComparingTo("110.00");
        assertThat(ctx.getBidIncrement()).isEqualByComparingTo("5.00");
        assertThat(ctx.getDepositAmount()).isEqualByComparingTo("20.00");
        assertThat(ctx.getCurrentWinnerId()).isEqualTo(PREVIOUS_WINNER_ID);
        assertThat(ctx.getTotalBids()).isEqualTo(2);
        assertThat(ctx.getEndTime()).isEqualTo(auction.getEndTime());
    }

    @Test
    void getBidContext_unknownAuction_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBidContext(id))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_FOUND);
    }

    // ---------------------------------------------------------------------
    // applyBid — happy paths
    // ---------------------------------------------------------------------

    @Test
    void applyBid_firstBidAtStartingPrice_isAccepted() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        ApplyBidRequest request = bid("100.00");

        ApplyBidResponse response = service.applyBid(auction.getId(), request);

        assertThat(response.getCurrentPrice()).isEqualByComparingTo("100.00");
        assertThat(response.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(response.getPreviousWinnerId()).isNull();
        assertThat(response.getTotalBids()).isEqualTo(1);
        assertThat(response.isExtended()).isFalse();
        assertThat(auction.getLastBidId()).isEqualTo(request.getBidId());
        verify(auctionItemRepository).save(auction);
    }

    @Test
    void applyBid_subsequentBidAtExactMinimum_updatesStateAndReturnsPreviousWinner() {
        AuctionItem auction = activeAuction(3, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(auction.getCurrentPrice()).isEqualByComparingTo("105.00");
        assertThat(auction.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(auction.getTotalBids()).isEqualTo(4);
        assertThat(response.getPreviousWinnerId()).isEqualTo(PREVIOUS_WINNER_ID);
        assertThat(response.getEndTime()).isEqualTo(auction.getEndTime());
        assertThat(response.getExtensionCount()).isZero();
    }

    @Test
    void applyBid_readsWithRowLockOnly() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        service.applyBid(auction.getId(), bid("100.00"));

        verify(auctionItemRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void applyBid_replayOfLastBidId_returnsCurrentStateWithoutChanges() {
        AuctionItem auction = activeAuction(4, "120.00");
        UUID bidId = UUID.randomUUID();
        auction.setLastBidId(bidId);
        auction.setCurrentWinnerId(BIDDER_ID);
        auction.setStatus(AuctionStatus.COMPLETED); // replay must succeed even after the auction closed
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(),
                new ApplyBidRequest(bidId, BIDDER_ID, new BigDecimal("120.00")));

        assertThat(response.getTotalBids()).isEqualTo(4);
        assertThat(response.getCurrentWinnerId()).isEqualTo(BIDDER_ID);
        assertThat(response.getPreviousWinnerId()).isNull();
        verify(auctionItemRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // applyBid — rejections
    // ---------------------------------------------------------------------

    @Test
    void applyBid_unknownAuction_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(auctionItemRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyBid(id, bid("100.00")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = AuctionStatus.class, names = {"DRAFT", "SCHEDULED", "COMPLETED", "FAILED", "CANCELLED", "REJECTED"})
    void applyBid_notActive_throwsConflict(AuctionStatus status) {
        AuctionItem auction = activeAuction(0, "100.00");
        auction.setStatus(status);
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("100.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_OPEN);
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void applyBid_exactlyAtEndTime_throwsConflict() {
        AuctionItem auction = activeAuction(0, "100.00");
        auction.setEndTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("100.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.AUCTION_NOT_OPEN);
    }

    @Test
    void applyBid_oneMillisecondBeforeEndTime_isAccepted() {
        AuctionItem auction = activeAuction(0, "100.00");
        auction.setEndTime(OffsetDateTime.ofInstant(NOW.plusMillis(1), ZoneOffset.UTC));
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("100.00"));

        assertThat(response.getTotalBids()).isEqualTo(1);
    }

    @Test
    void applyBid_sellerBiddingOnOwnAuction_throwsForbidden() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(),
                new ApplyBidRequest(UUID.randomUUID(), SELLER_ID, new BigDecimal("500.00"))))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(AuctionErrorCodes.BID_OWN_AUCTION);
    }

    @Test
    void applyBid_firstBidBelowStartingPrice_throwsBidTooLow() {
        AuctionItem auction = activeAuction(0, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("99.99")))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("100.00"));
    }

    @Test
    void applyBid_subsequentBidBelowIncrement_throwsBidTooLow() {
        AuctionItem auction = activeAuction(1, "100.00");
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("104.00")))
                .isInstanceOf(BidTooLowException.class)
                .satisfies(ex -> assertThat(((BidTooLowException) ex).getMinimumBid()).isEqualByComparingTo("105.00"));
        verify(auctionItemRepository, never()).save(any());
    }

    @Test
    void minimumBid_usesCurrentPriceForFirstBidAndAddsIncrementAfter() {
        assertThat(AuctionBidService.minimumBid(activeAuction(0, "100.00"))).isEqualByComparingTo("100.00");
        assertThat(AuctionBidService.minimumBid(activeAuction(2, "100.00"))).isEqualByComparingTo("105.00");
    }

    // ---------------------------------------------------------------------
    // anti-sniping extension
    // ---------------------------------------------------------------------

    @Test
    void bidInsideSnipeWindow_extendsEndTimeAndRecordsExtension() {
        AuctionItem auction = endingIn(119);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        ApplyBidRequest request = bid("105.00");

        ApplyBidResponse response = service.applyBid(auction.getId(), request);

        OffsetDateTime expectedEnd = previousEnd.plusSeconds(300);
        assertThat(auction.getEndTime()).isEqualTo(expectedEnd);
        assertThat(auction.getExtensionCount()).isEqualTo(1);
        assertThat(response.isExtended()).isTrue();
        assertThat(response.getEndTime()).isEqualTo(expectedEnd);
        assertThat(response.getExtensionCount()).isEqualTo(1);

        ArgumentCaptor<AuctionExtension> extension = ArgumentCaptor.forClass(AuctionExtension.class);
        verify(auctionExtensionRepository).save(extension.capture());
        assertThat(extension.getValue().getAuction()).isSameAs(auction);
        assertThat(extension.getValue().getPreviousEndTime()).isEqualTo(previousEnd);
        assertThat(extension.getValue().getNewEndTime()).isEqualTo(expectedEnd);
        assertThat(extension.getValue().getExtensionDurationSeconds()).isEqualTo(300);
        assertThat(extension.getValue().getTriggeredByBidId()).isEqualTo(request.getBidId());
        assertThat(extension.getValue().getTriggeredByUserId()).isEqualTo(BIDDER_ID);
    }

    @Test
    void extensionEvent_isPublishedOnlyAfterCommit() {
        AuctionItem auction = endingIn(30);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        ApplyBidRequest request = bid("105.00");

        service.applyBid(auction.getId(), request);

        verify(kafkaProducer, never()).publishAuctionExtended(any());
        triggerAfterCommit();
        ArgumentCaptor<AuctionExtendedEvent> event = ArgumentCaptor.forClass(AuctionExtendedEvent.class);
        verify(kafkaProducer).publishAuctionExtended(event.capture());
        assertThat(event.getValue().getAuctionId()).isEqualTo(auction.getId());
        assertThat(event.getValue().getAuctionTitle()).isEqualTo("Vintage Watch");
        assertThat(event.getValue().getPreviousEndTime()).isEqualTo(previousEnd.toInstant());
        assertThat(event.getValue().getNewEndTime()).isEqualTo(previousEnd.plusSeconds(300).toInstant());
        assertThat(event.getValue().getExtensionCount()).isEqualTo(1);
        assertThat(event.getValue().getTriggeredByBidId()).isEqualTo(request.getBidId());
        assertThat(event.getValue().getTriggeredByUserId()).isEqualTo(BIDDER_ID);
    }

    @Test
    void bidExactlyAtWindowBoundary_doesNotExtend() {
        AuctionItem auction = endingIn(120);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(response.isExtended()).isFalse();
        assertThat(auction.getEndTime()).isEqualTo(previousEnd);
        assertThat(auction.getExtensionCount()).isZero();
        verify(auctionExtensionRepository, never()).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void bidOutsideSnipeWindow_doesNotExtend() {
        AuctionItem auction = endingIn(121);
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(response.isExtended()).isFalse();
        verify(auctionExtensionRepository, never()).save(any());
    }

    @Test
    void repeatedInWindowBids_extendAgain() {
        AuctionItem auction = endingIn(10);
        auction.setExtensionCount(1);
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(), bid("105.00"));

        assertThat(response.isExtended()).isTrue();
        assertThat(auction.getExtensionCount()).isEqualTo(2);
    }

    @Test
    void rejectedBidInsideWindow_doesNotExtend() {
        AuctionItem auction = endingIn(10);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.applyBid(auction.getId(), bid("101.00")))
                .isInstanceOf(BidTooLowException.class);

        assertThat(auction.getEndTime()).isEqualTo(previousEnd);
        verify(auctionExtensionRepository, never()).save(any());
    }

    @Test
    void replayInsideWindow_doesNotExtendAgain() {
        AuctionItem auction = endingIn(10);
        UUID bidId = UUID.randomUUID();
        auction.setLastBidId(bidId);
        OffsetDateTime previousEnd = auction.getEndTime();
        when(auctionItemRepository.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        ApplyBidResponse response = service.applyBid(auction.getId(),
                new ApplyBidRequest(bidId, BIDDER_ID, new BigDecimal("105.00")));

        assertThat(response.isExtended()).isFalse();
        assertThat(auction.getEndTime()).isEqualTo(previousEnd);
        verify(auctionExtensionRepository, never()).save(any());
    }
}
