package com.bidnow.auction.service;

import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.repository.AuctionItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionStartupRecoveryServiceTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionClosureService closureService;
    @Mock
    private AuctionActivationService activationService;

    @InjectMocks
    private AuctionStartupRecoveryService recoveryService;

    @Test
    void recoverOverdueAuctions_withOverdueActiveAuction_callsClose() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder().id(id).build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction));
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.SCHEDULED), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        recoveryService.recoverOverdueAuctions();

        verify(closureService).close(id);
        verify(activationService, never()).activate(any());
    }

    @Test
    void recoverOverdueAuctions_scheduledPastStartFutureEnd_activatesOnly() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(id)
                .endTime(OffsetDateTime.now().plusHours(1))
                .build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.SCHEDULED), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction));

        recoveryService.recoverOverdueAuctions();

        verify(activationService).activate(id);
        verify(closureService, never()).close(any());
    }

    @Test
    void recoverOverdueAuctions_scheduledPastBothTimes_activatesAndCloses() {
        UUID id = UUID.randomUUID();
        AuctionItem auction = AuctionItem.builder()
                .id(id)
                .endTime(OffsetDateTime.now().minusHours(1))
                .build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.SCHEDULED), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction));

        recoveryService.recoverOverdueAuctions();

        verify(activationService).activate(id);
        verify(closureService).close(id);
    }

    @Test
    void recoverOverdueAuctions_noOverdueAuctions_doesNothing() {
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());

        recoveryService.recoverOverdueAuctions();

        verify(closureService, never()).close(any());
        verify(activationService, never()).activate(any());
    }

    @Test
    void recoverScheduledAuctions_whenActivateFails_skipsCloseAndContinues() {
        // Two SCHEDULED auctions, both with startTime in the past
        // endTime is in the past for both (would normally trigger close after activate)
        OffsetDateTime now = OffsetDateTime.now();
        AuctionItem auction1 = AuctionItem.builder()
                .id(UUID.randomUUID())
                .endTime(now.minusMinutes(30))
                .build();
        AuctionItem auction2 = AuctionItem.builder()
                .id(UUID.randomUUID())
                .endTime(now.minusMinutes(30))
                .build();

        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.SCHEDULED), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction1, auction2));
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        // activate throws for auction1, succeeds for auction2
        doThrow(new RuntimeException("activate failed"))
                .when(activationService).activate(auction1.getId());

        recoveryService.recoverOverdueAuctions();

        // close must NOT be called for auction1 (activate failed)
        verify(closureService, never()).close(auction1.getId());
        // auction2 was activated successfully, so close IS called
        verify(closureService).close(auction2.getId());
    }

    @Test
    void recoverOverdueAuctions_closeThrows_continuesNextAuction() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        AuctionItem auction1 = AuctionItem.builder().id(id1).build();
        AuctionItem auction2 = AuctionItem.builder().id(id2).build();
        when(auctionItemRepository.findByStatusAndEndTimeBeforeAndDeletedAtIsNull(
                eq(AuctionStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(auction1, auction2));
        when(auctionItemRepository.findByStatusAndStartTimeBeforeAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("DB error")).when(closureService).close(id1);

        recoveryService.recoverOverdueAuctions();

        verify(closureService).close(id1);
        verify(closureService).close(id2);
    }
}
